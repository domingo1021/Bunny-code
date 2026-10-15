package com.bunnycode.battle.application;

import com.bunnycode.battle.application.BattleViews.BattleDetailView;
import com.bunnycode.battle.application.BattleViews.BattleSummaryView;
import com.bunnycode.battle.application.BattleViews.QuestionView;
import com.bunnycode.battle.domain.Battle;
import com.bunnycode.battle.domain.BattleRepository;
import com.bunnycode.battle.domain.BattleStatus;
import com.bunnycode.battle.domain.BattleSummaryRow;
import com.bunnycode.battle.domain.Question;
import com.bunnycode.battle.domain.QuestionLevel;
import com.bunnycode.identity.UserDirectory;
import com.bunnycode.identity.UserSummary;
import com.bunnycode.shared.web.LikePatterns;
import com.bunnycode.shared.web.PageResponse;
import com.bunnycode.storage.FileStorage;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lobby and review pages. Two queries per page (rows + count) plus one batched user lookup. */
@Service
@Transactional(readOnly = true)
public class BattleQueryService {

    private static final int MAX_PLAYER_MATCHES = 100;
    private static final Set<BattleStatus> LISTED_BY_DEFAULT =
            Set.copyOf(EnumSet.of(BattleStatus.WAITING, BattleStatus.RUNNING, BattleStatus.FINISHED));

    private final BattleRepository battles;
    private final UserDirectory users;
    private final FileStorage storage;

    BattleQueryService(BattleRepository battles, UserDirectory users, FileStorage storage) {
        this.battles = battles;
        this.users = users;
        this.storage = storage;
    }

    /**
     * Lobby page. {@code statuses} empty means every battle worth listing (terminated ones are
     * hidden unless asked for); {@code level} null means any level.
     */
    public PageResponse<BattleSummaryView> list(Set<BattleStatus> statuses, QuestionLevel level, String keyword,
                                                SearchField field, int page, int size) {
        Set<BattleStatus> wanted = statuses == null || statuses.isEmpty() ? LISTED_BY_DEFAULT : statuses;
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<BattleSummaryRow> rows;
        if (keyword == null || keyword.isBlank()) {
            rows = battles.findByStatuses(wanted, level, pageRequest);
        } else {
            String pattern = LikePatterns.contains(keyword);
            rows = switch (field) {
                case NAME -> battles.searchByName(wanted, level, pattern, pageRequest);
                case QUESTION -> battles.searchByQuestion(wanted, level, pattern, pageRequest);
                case PLAYER -> {
                    Set<Long> playerIds = users.findIdsByNameContaining(keyword, MAX_PLAYER_MATCHES);
                    yield playerIds.isEmpty() ? Page.empty(pageRequest)
                            : battles.findByPlayers(wanted, level, playerIds, pageRequest);
                }
            };
        }
        Set<Long> playerIds = new HashSet<>();
        rows.forEach(row -> {
            playerIds.add(row.firstPlayerId());
            playerIds.add(row.secondPlayerId());
        });
        Map<Long, UserSummary> profiles = users.findAllByIds(playerIds);
        return PageResponse.of(rows).map(row -> new BattleSummaryView(row.id(), row.name(), row.status(),
                row.watchCount(), row.questionName(), row.questionLevel(),
                List.of(profiles.get(row.firstPlayerId()), profiles.get(row.secondPlayerId())), row.winnerId()));
    }

    /** Battles shown in the lobby (all but terminated ones). */
    public long countListedBattles() {
        return battles.countByStatuses(LISTED_BY_DEFAULT);
    }

    public boolean isNameAvailable(String name) {
        return !battles.existsByName(name);
    }

    public BattleDetailView detail(long battleId) {
        Battle battle = battles.findWithQuestion(battleId).orElseThrow(BattleService::notFound);
        Set<Long> ids = new HashSet<>(battle.playerIds());
        Map<Long, UserSummary> profiles = users.findAllByIds(ids);
        Question question = battle.getQuestion();
        String winnerCode = battle.getWinnerCodeKey().isBlank() ? null : storage.publicUrl(battle.getWinnerCodeKey());
        return new BattleDetailView(battle.getId(), battle.getName(), battle.getStatus(), battle.getWatchCount(),
                new QuestionView(question.getName(), question.getLevel(), storage.publicUrl(question.getDescriptionKey()),
                        storage.publicUrl(question.getStarterCodeKey())),
                battle.playerIds().stream().map(profiles::get).toList(),
                battle.getWinnerId() == null ? null : profiles.get(battle.getWinnerId()),
                winnerCode);
    }

    public enum SearchField {
        NAME,
        QUESTION,
        PLAYER
    }
}
