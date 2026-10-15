package com.bunnycode.battle.domain;

/**
 * <pre>
 * WAITING ──both ready──▶ RUNNING ──a submission passes every case──▶ FINISHED
 *                            └──a player leaves / runs out of submissions──▶ TERMINATED
 * </pre>
 * Transitions are applied with conditional updates ({@code … where status = :from}), so when two
 * events race only one of them wins.
 */
public enum BattleStatus {
    WAITING,
    RUNNING,
    FINISHED,
    TERMINATED;

    public boolean isOver() {
        return this == FINISHED || this == TERMINATED;
    }
}
