package com.bunnycode.battle.application;

import com.bunnycode.battle.domain.BattleStatus;
import com.bunnycode.battle.domain.QuestionLevel;
import com.bunnycode.identity.UserSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** Read models of the battle module, as sent to clients. */
public final class BattleViews {

    private BattleViews() {
    }

    public record QuestionView(String name, QuestionLevel level, String descriptionUrl, String starterCodeUrl) {
    }

    public record BattleSummaryView(long id, String name, BattleStatus status, long watchCount,
                                    String questionName, QuestionLevel questionLevel,
                                    List<UserSummary> players, Long winnerId) {
        public BattleSummaryView {
            players = List.copyOf(players);
        }
    }

    /** Lobby/review detail. {@code winner} and {@code winnerCodeUrl} are null until someone wins. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record BattleDetailView(long id, String name, BattleStatus status, long watchCount, QuestionView question,
                                   List<UserSummary> players, UserSummary winner, String winnerCodeUrl) {
        public BattleDetailView {
            players = List.copyOf(players);
        }
    }

    public record PlayerView(UserSummary user, boolean ready, String code, int chancesLeft) {
    }

    /** Sent once to a client subscribing to {@code /app/battles/{id}}; later changes arrive as {@link BattleEvent}s. */
    public record BattleSnapshot(long id, String name, BattleStatus status, QuestionView question,
                                 List<PlayerView> players, ViewerRole viewerRole, Long winnerId) {
        public BattleSnapshot {
            players = List.copyOf(players);
        }
    }

    public enum ViewerRole {
        PLAYER,
        SPECTATOR
    }

    public record InvitationView(String invitationId, String battleName, QuestionLevel level,
                                 long inviterId, String inviterName, Instant expiresAt) {
    }

    /** Sent to both players on {@code /user/queue/battles} once an invitation is accepted. */
    public record BattleCreated(long battleId) {
    }
}
