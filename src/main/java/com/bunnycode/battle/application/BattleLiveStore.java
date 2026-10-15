package com.bunnycode.battle.application;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.OptionalInt;

/** Port to the fast store for per-player state that changes on every keystroke. */
public interface BattleLiveStore {

    void open(long battleId, Collection<Long> playerIds, int chances, Duration ttl);

    /** State of each listed player that still has live state. */
    Map<Long, PlayerState> players(long battleId, Collection<Long> playerIds);

    boolean isLive(long battleId, long playerId);

    void markReady(long battleId, long playerId);

    void updateCode(long battleId, long playerId, String code);

    /** Takes one submission; empty when the player has none left. Returns the chances remaining. */
    OptionalInt consumeChance(long battleId, long playerId);

    /** Gives back a submission whose judging failed for reasons outside the player's control. */
    void refundChance(long battleId, long playerId);

    void close(long battleId, Collection<Long> playerIds);
}
