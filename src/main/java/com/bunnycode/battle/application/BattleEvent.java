package com.bunnycode.battle.application;

import com.bunnycode.battle.domain.Verdict;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.Instant;
import java.util.List;

/**
 * Everything broadcast on {@code /topic/battles/{battleId}}. Serialised with a {@code type}
 * discriminator so clients can switch on it, and sealed so the server handles every case.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BattleEvent.PlayerReady.class, name = "PLAYER_READY"),
        @JsonSubTypes.Type(value = BattleEvent.BattleStarted.class, name = "STARTED"),
        @JsonSubTypes.Type(value = BattleEvent.CodeChanged.class, name = "CODE_CHANGED"),
        @JsonSubTypes.Type(value = BattleEvent.SubmissionJudged.class, name = "SUBMISSION_JUDGED"),
        @JsonSubTypes.Type(value = BattleEvent.BattleFinished.class, name = "FINISHED"),
        @JsonSubTypes.Type(value = BattleEvent.BattleTerminated.class, name = "TERMINATED")
})
public sealed interface BattleEvent {

    record PlayerReady(long userId) implements BattleEvent {
    }

    record BattleStarted(Instant startedAt) implements BattleEvent {
    }

    record CodeChanged(long userId, String code) implements BattleEvent {
    }

    record SubmissionJudged(long userId, Verdict verdict, List<TestCaseReport> testCases, String output,
                            int chancesLeft) implements BattleEvent {
        public SubmissionJudged {
            testCases = List.copyOf(testCases);
        }
    }

    record BattleFinished(long winnerId, String winnerName, String reason) implements BattleEvent {
    }

    record BattleTerminated(String reason) implements BattleEvent {
    }
}
