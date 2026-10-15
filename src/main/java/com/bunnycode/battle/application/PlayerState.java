package com.bunnycode.battle.application;

/** A player's live state during a battle (kept in Redis, not the database). */
public record PlayerState(boolean ready, String code, int chancesLeft) {
}
