package com.bunnycode.battle.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.bunnycode.battle.application.BattleEvent.BattleFinished;
import com.bunnycode.battle.application.BattleEvent.BattleStarted;
import com.bunnycode.battle.application.BattleEvent.BattleTerminated;
import com.bunnycode.battle.application.BattleEvent.PlayerReady;
import com.bunnycode.battle.application.BattleEvent.SubmissionJudged;
import com.bunnycode.battle.domain.BattleRepository;
import com.bunnycode.battle.domain.BattleStatus;
import com.bunnycode.battle.domain.QuestionRepository;
import com.bunnycode.battle.domain.Verdict;
import com.bunnycode.identity.UserDirectory;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.storage.FileStorage;
import com.bunnycode.storage.StorageLayout;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Battle lifecycle")
class BattleServiceTest {

    private final BattleRepository battles = mock(BattleRepository.class);
    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final BattleLiveStore liveStore = mock(BattleLiveStore.class);
    private final BattleNotifier notifier = mock(BattleNotifier.class);
    private final Judge judge = mock(Judge.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final StorageLayout layout = mock(StorageLayout.class);
    /** Runs judging inline so the test can assert on its effects. */
    private final JudgeDispatcher inline = Runnable::run;
    private final BattleService service = new BattleService(battles, questions, liveStore, notifier, inline, judge,
            mock(UserDirectory.class), storage, layout, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    private final AuthenticatedUser alice = new AuthenticatedUser(1L, "alice");
    private final AuthenticatedUser bob = new AuthenticatedUser(2L, "bob");

    @Nested
    @DisplayName("Getting ready")
    class Ready {

        @Test
        @DisplayName("Given the other player is ready, when I get ready and win the WAITING→RUNNING race, then the battle starts once")
        void startsWhenBothReady() {
            given(battles.findWithQuestion(9L)).willReturn(Optional.of(BattleFixtures.battle(9L, BattleStatus.WAITING)));
            given(liveStore.players(9L, List.of(1L, 2L))).willReturn(Map.of(
                    1L, new PlayerState(true, "", 3), 2L, new PlayerState(true, "", 3)));
            given(battles.transition(9L, BattleStatus.WAITING, BattleStatus.RUNNING)).willReturn(1);

            service.markReady(9L, alice);

            then(notifier).should().publish(9L, new PlayerReady(1L));
            then(notifier).should().publish(9L, new BattleStarted(Instant.EPOCH));
        }

        @Test
        @DisplayName("Given the other player won the start race, then I do not announce a second start")
        void lostStartRace() {
            given(battles.findWithQuestion(9L)).willReturn(Optional.of(BattleFixtures.battle(9L, BattleStatus.WAITING)));
            given(liveStore.players(9L, List.of(1L, 2L))).willReturn(Map.of(
                    1L, new PlayerState(true, "", 3), 2L, new PlayerState(true, "", 3)));
            given(battles.transition(9L, BattleStatus.WAITING, BattleStatus.RUNNING)).willReturn(0);

            service.markReady(9L, alice);

            then(notifier).should(never()).publish(eq(9L), any(BattleStarted.class));
        }

        @Test
        @DisplayName("Spectators cannot get ready")
        void spectator() {
            given(battles.findWithQuestion(9L)).willReturn(Optional.of(BattleFixtures.battle(9L, BattleStatus.WAITING)));
            assertThatThrownBy(() -> service.markReady(9L, new AuthenticatedUser(3L, "eve")))
                    .isInstanceOfSatisfying(BunnyException.class, e -> assertThat(e.code()).isEqualTo("battle.not-player"));
        }
    }

    @Nested
    @DisplayName("Submitting")
    class Submitting {

        @Test
        @DisplayName("Given an accepted submission that wins the RUNNING→FINISHED race, then the code is archived and the winner announced")
        void wins() {
            given(battles.findWithQuestion(9L)).willReturn(Optional.of(BattleFixtures.battle(9L, BattleStatus.RUNNING)));
            given(liveStore.consumeChance(9L, 1L)).willReturn(OptionalInt.of(2));
            given(questions.findWithTestCases(5L)).willReturn(Optional.of(BattleFixtures.twoSum(1)));
            given(judge.judge(any(), eq("solution"))).willReturn(new Judgement(Verdict.ACCEPTED, List.of(), ""));
            given(battles.finish(9L, 1L, BattleStatus.RUNNING, BattleStatus.FINISHED)).willReturn(1);
            given(layout.battleWinnerKey(9L, 1L)).willReturn("/battle/battle_9/1.js");
            given(battles.findById(9L)).willReturn(Optional.of(BattleFixtures.battle(9L, BattleStatus.FINISHED)));

            service.submit(9L, alice, "session", "solution");

            then(notifier).should().publish(9L, new SubmissionJudged(1L, Verdict.ACCEPTED, List.of(), "", 2));
            then(storage).should().store(eq("/battle/battle_9/1.js"), any(), eq("application/javascript"));
            then(battles).should().attachWinnerCode(9L, "/battle/battle_9/1.js");
            then(liveStore).should().close(9L, List.of(1L, 2L));
            then(notifier).should().publish(eq(9L), any(BattleFinished.class));
        }

        @Test
        @DisplayName("Given the opponent finished first, then a later accepted submission changes nothing")
        void lateWinner() {
            given(battles.findWithQuestion(9L)).willReturn(Optional.of(BattleFixtures.battle(9L, BattleStatus.RUNNING)));
            given(liveStore.consumeChance(9L, 1L)).willReturn(OptionalInt.of(2));
            given(questions.findWithTestCases(5L)).willReturn(Optional.of(BattleFixtures.twoSum(1)));
            given(judge.judge(any(), any())).willReturn(new Judgement(Verdict.ACCEPTED, List.of(), ""));
            given(battles.finish(9L, 1L, BattleStatus.RUNNING, BattleStatus.FINISHED)).willReturn(0);

            service.submit(9L, alice, "session", "solution");

            then(storage).shouldHaveNoInteractions();
            then(notifier).should(never()).publish(eq(9L), any(BattleFinished.class));
        }

        @Test
        @DisplayName("Given my last submission is wrong, then the battle is terminated")
        void outOfChances() {
            given(battles.findWithQuestion(9L)).willReturn(Optional.of(BattleFixtures.battle(9L, BattleStatus.RUNNING)));
            given(liveStore.consumeChance(9L, 1L)).willReturn(OptionalInt.of(0));
            given(questions.findWithTestCases(5L)).willReturn(Optional.of(BattleFixtures.twoSum(1)));
            given(judge.judge(any(), any())).willReturn(new Judgement(Verdict.WRONG_ANSWER, List.of(), ""));
            given(battles.findById(9L)).willReturn(Optional.of(BattleFixtures.battle(9L, BattleStatus.RUNNING)));
            given(battles.transition(9L, BattleStatus.RUNNING, BattleStatus.TERMINATED)).willReturn(1);

            service.submit(9L, alice, "session", "wrong");

            then(notifier).should().publish(9L, new BattleTerminated("alice ran out of submissions."));
        }

        @Test
        @DisplayName("Given no submissions are left, then it is refused before anything runs")
        void noChances() {
            given(battles.findWithQuestion(9L)).willReturn(Optional.of(BattleFixtures.battle(9L, BattleStatus.RUNNING)));
            given(liveStore.consumeChance(9L, 1L)).willReturn(OptionalInt.empty());

            assertThatThrownBy(() -> service.submit(9L, alice, "session", "code"))
                    .isInstanceOfSatisfying(BunnyException.class, e -> assertThat(e.code()).isEqualTo("battle.no-chances"));
            then(judge).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Given the battle has not started, then submissions are refused")
        void notRunning() {
            given(battles.findWithQuestion(9L)).willReturn(Optional.of(BattleFixtures.battle(9L, BattleStatus.WAITING)));
            assertThatThrownBy(() -> service.submit(9L, alice, "session", "code"))
                    .isInstanceOfSatisfying(BunnyException.class, e -> assertThat(e.code()).isEqualTo("battle.not-running"));
        }

        @Test
        @DisplayName("Given the sandbox is unavailable, then the submission is refunded and only I am told")
        void refunds() {
            given(battles.findWithQuestion(9L)).willReturn(Optional.of(BattleFixtures.battle(9L, BattleStatus.RUNNING)));
            given(liveStore.consumeChance(9L, 1L)).willReturn(OptionalInt.of(2));
            given(questions.findWithTestCases(5L)).willReturn(Optional.of(BattleFixtures.twoSum(1)));
            given(judge.judge(any(), any())).willThrow(BunnyException.unavailable("execution.busy", "busy"));

            service.submit(9L, alice, "session-1", "code");

            then(liveStore).should().refundChance(9L, 1L);
            then(notifier).should().notifyError(1L, "session-1", "execution.busy", "busy");
            then(notifier).should(never()).publish(anyLong(), any(SubmissionJudged.class));
        }
    }

    @Nested
    @DisplayName("Typing and leaving")
    class TypingAndLeaving {

        @Test
        @DisplayName("Code updates go through Redis only and are broadcast to the room")
        void codeUpdates() {
            given(liveStore.isLive(9L, 1L)).willReturn(true);

            service.updateCode(9L, alice, "let x");

            then(liveStore).should().updateCode(9L, 1L, "let x");
            then(battles).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("Non-players cannot type into a battle")
        void spectatorsCannotType() {
            given(liveStore.isLive(9L, 3L)).willReturn(false);
            assertThatThrownBy(() -> service.updateCode(9L, new AuthenticatedUser(3L, "eve"), "x"))
                    .isInstanceOf(BunnyException.class);
        }

        @Test
        @DisplayName("Leaving a running battle forfeits it")
        void leaveRunning() {
            given(battles.findById(9L)).willReturn(Optional.of(BattleFixtures.battle(9L, BattleStatus.RUNNING)));
            given(battles.transition(9L, BattleStatus.RUNNING, BattleStatus.TERMINATED)).willReturn(1);

            service.leave(9L, bob);

            then(notifier).should().publish(9L, new BattleTerminated("bob left the battle."));
        }

        @Test
        @DisplayName("Leaving before the start, or as a spectator, changes nothing")
        void leaveWaiting() {
            given(battles.findById(9L)).willReturn(Optional.of(BattleFixtures.battle(9L, BattleStatus.WAITING)));

            service.leave(9L, bob);
            service.leave(9L, new AuthenticatedUser(3L, "eve"));

            then(battles).should(never()).transition(anyLong(), any(), any());
            then(notifier).should(never()).notifyError(anyLong(), anyString(), anyString(), anyString());
        }
    }
}
