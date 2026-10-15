package com.bunnycode.battle.domain;

/** One lobby row: both players as ids (resolved in one batch), the question joined in the same query. */
public record BattleSummaryRow(Long id, String name, BattleStatus status, long watchCount,
                               Long firstPlayerId, Long secondPlayerId, Long winnerId,
                               String questionName, QuestionLevel questionLevel) {
}
