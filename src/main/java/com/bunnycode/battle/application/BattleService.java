package com.bunnycode.battle.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bunnycode.battle.application.BattleEvent.BattleFinished;
import com.bunnycode.battle.application.BattleEvent.BattleStarted;
import com.bunnycode.battle.application.BattleEvent.BattleTerminated;
import com.bunnycode.battle.application.BattleEvent.CodeChanged;
import com.bunnycode.battle.application.BattleEvent.PlayerReady;
import com.bunnycode.battle.application.BattleEvent.SubmissionJudged;
import com.bunnycode.battle.application.BattleViews.BattleSnapshot;
import com.bunnycode.battle.application.BattleViews.PlayerView;
import com.bunnycode.battle.application.BattleViews.QuestionView;
import com.bunnycode.battle.application.BattleViews.ViewerRole;
import com.bunnycode.battle.domain.Battle;
import com.bunnycode.battle.domain.BattleRepository;
import com.bunnycode.battle.domain.BattleStatus;
import com.bunnycode.battle.domain.Question;
import com.bunnycode.battle.domain.QuestionRepository;
import com.bunnycode.identity.UserDirectory;
import com.bunnycode.identity.UserSummary;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.storage.FileStorage;
import com.bunnycode.storage.StorageLayout;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The live part of a battle. Keystroke-rate traffic (code updates) touches only Redis; the
 * database is read for rare commands (ready, submit, leave) and written only through the
 * compare-and-set transitions in {@link BattleRepository}.
 */
@Service
public class BattleService {

    private static final Logger log = LoggerFactory.getLogger(BattleService.class);
    static final String WINNER_CODE_CONTENT_TYPE = "application/javascript";

    private final BattleRepository battles;
    private final QuestionRepository questions;
    private final BattleLiveStore liveStore;
    private final BattleNotifier notifier;
    private final JudgeDispatcher judgeDispatcher;
    private final Judge judge;
    private final UserDirectory users;
    private final FileStorage storage;
    private final StorageLayout storageLayout;
    private final Clock clock;

    BattleService(BattleRepository battles, QuestionRepository questions, BattleLiveStore liveStore,
                  BattleNotifier notifier, JudgeDispatcher judgeDispatcher, Judge judge, UserDirectory users,
                  FileStorage storage, StorageLayout storageLayout, Clock clock) {
        this.battles = battles;
        this.questions = questions;
        this.liveStore = liveStore;
        this.notifier = notifier;
        this.judgeDispatcher = judgeDispatcher;
        this.judge = judge;
        this.users = users;
        this.storage = storage;
        this.storageLayout = storageLayout;
        this.clock = clock;
    }

    /** What a client needs on entering the battle room. Spectators entering an open battle add a view. */
    @Transactional
    public BattleSnapshot snapshot(long battleId, AuthenticatedUser viewer) {
        Battle battle = battles.findWithQuestion(battleId).orElseThrow(BattleService::notFound);
        boolean isPlayer = battle.isPlayer(viewer.id());
        if (!isPlayer && !battle.getStatus().isOver()) {
            battles.incrementWatchCount(battleId);
        }
        List<Long> playerIds = battle.playerIds();
        Map<Long, UserSummary> profiles = users.findAllByIds(playerIds);
        Map<Long, PlayerState> live = battle.getStatus().isOver() ? Map.of() : liveStore.players(battleId, playerIds);
        List<PlayerView> players = playerIds.stream()
                .map(id -> {
                    PlayerState state = live.getOrDefault(id, new PlayerState(false, "", 0));
                    return new PlayerView(profiles.get(id), state.ready(), state.code(), state.chancesLeft());
                })
                .toList();
        return new BattleSnapshot(battle.getId(), battle.getName(), battle.getStatus(), questionView(battle.getQuestion()),
                players, isPlayer ? ViewerRole.PLAYER : ViewerRole.SPECTATOR, battle.getWinnerId());
    }

    public void markReady(long battleId, AuthenticatedUser player) {
        Battle battle = playerBattle(battleId, player);
        if (battle.getStatus() != BattleStatus.WAITING) {
            return; // idempotent: re-sent "ready" after the start is harmless
        }
        liveStore.markReady(battleId, player.id());
        notifier.publish(battleId, new PlayerReady(player.id()));
        Map<Long, PlayerState> states = liveStore.players(battleId, battle.playerIds());
        boolean everyoneReady = states.size() == battle.playerIds().size()
                && states.values().stream().allMatch(PlayerState::ready);
        if (everyoneReady && battles.transition(battleId, BattleStatus.WAITING, BattleStatus.RUNNING) == 1) {
            notifier.publish(battleId, new BattleStarted(clock.instant()));
        }
    }

    /** Hot path (every keystroke): Redis only. Being in the live state is what proves you are a player. */
    public void updateCode(long battleId, AuthenticatedUser player, String code) {
        if (!liveStore.isLive(battleId, player.id())) {
            throw BunnyException.forbidden("battle.not-player", "Only the two players can type in a battle.");
        }
        liveStore.updateCode(battleId, player.id(), code);
        notifier.publish(battleId, new CodeChanged(player.id(), code));
    }

    /**
     * Uses one submission and judges it asynchronously; the verdict is broadcast to the room.
     * A submission that fails for infrastructure reasons is refunded.
     */
    public void submit(long battleId, AuthenticatedUser player, String sessionId, String code) {
        Battle battle = playerBattle(battleId, player);
        if (battle.getStatus() != BattleStatus.RUNNING) {
            throw BunnyException.conflict("battle.not-running", "Submissions open once both players are ready.");
        }
        int chancesLeft = liveStore.consumeChance(battleId, player.id())
                .orElseThrow(() -> BunnyException.conflict("battle.no-chances", "You have no submissions left."));
        long questionId = battle.getQuestion().getId();
        judgeDispatcher.dispatch(() -> judgeAndPublish(battleId, player, sessionId, questionId, code, chancesLeft));
    }

    /** Leaving a running battle forfeits it; leaving before the start or as a spectator changes nothing. */
    public void leave(long battleId, AuthenticatedUser user) {
        battles.findById(battleId)
                .filter(battle -> battle.isPlayer(user.id()) && battle.getStatus() == BattleStatus.RUNNING)
                .ifPresent(battle -> terminate(battle, user.name() + " left the battle."));
    }

    void judgeAndPublish(long battleId, AuthenticatedUser player, String sessionId, long questionId, String code,
                         int chancesLeft) {
        try {
            Question question = questions.findWithTestCases(questionId).orElseThrow();
            Judgement judgement = judge.judge(question, code);
            notifier.publish(battleId, new SubmissionJudged(player.id(), judgement.verdict(), judgement.testCases(),
                    judgement.output(), chancesLeft));
            if (judgement.accepted()) {
                declareWinner(battleId, player, code);
            } else if (chancesLeft == 0) {
                battles.findById(battleId).ifPresent(battle ->
                        terminate(battle, player.name() + " ran out of submissions."));
            }
        } catch (BunnyException e) {
            liveStore.refundChance(battleId, player.id());
            notifier.notifyError(player.id(), sessionId, e.code(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Judging failed for battle {} and user {}", battleId, player.id(), e);
            liveStore.refundChance(battleId, player.id());
            notifier.notifyError(player.id(), sessionId, "internal",
                    "We couldn't judge your submission. It was not counted — please try again.");
        }
    }

    private void declareWinner(long battleId, AuthenticatedUser winner, String code) {
        if (battles.finish(battleId, winner.id(), BattleStatus.RUNNING, BattleStatus.FINISHED) != 1) {
            return; // the opponent's accepted submission was a moment earlier
        }
        String key = storageLayout.battleWinnerKey(battleId, winner.id());
        storage.store(key, code.getBytes(UTF_8), WINNER_CODE_CONTENT_TYPE);
        battles.attachWinnerCode(battleId, key);
        battles.findById(battleId).ifPresent(battle -> liveStore.close(battleId, battle.playerIds()));
        notifier.publish(battleId, new BattleFinished(winner.id(), winner.name(),
                winner.name() + " passed every test case!"));
    }

    private void terminate(Battle battle, String reason) {
        if (battles.transition(battle.getId(), BattleStatus.RUNNING, BattleStatus.TERMINATED) == 1) {
            liveStore.close(battle.getId(), battle.playerIds());
            notifier.publish(battle.getId(), new BattleTerminated(reason));
        }
    }

    private Battle playerBattle(long battleId, AuthenticatedUser user) {
        Battle battle = battles.findWithQuestion(battleId).orElseThrow(BattleService::notFound);
        if (!battle.isPlayer(user.id())) {
            throw BunnyException.forbidden("battle.not-player", "Only the two players can do that.");
        }
        return battle;
    }

    private QuestionView questionView(Question question) {
        return new QuestionView(question.getName(), question.getLevel(),
                storage.publicUrl(question.getDescriptionKey()), storage.publicUrl(question.getStarterCodeKey()));
    }

    static BunnyException notFound() {
        return BunnyException.notFound("battle.not-found", "That battle does not exist.");
    }
}
