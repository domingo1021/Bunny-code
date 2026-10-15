package com.bunnycode.battle.infrastructure;

import com.bunnycode.battle.application.BattleLiveStore;
import com.bunnycode.battle.application.PlayerState;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * One hash per player: {@code battle:{battleId}:player:{userId}} → {@code ready}, {@code code},
 * {@code chances}. Every mutation is "only if the hash still exists", so a late keystroke after
 * the battle closed cannot resurrect state without a TTL.
 */
@Component
class RedisBattleLiveStore implements BattleLiveStore {

    private static final String READY = "ready";
    private static final String CODE = "code";
    private static final String CHANCES = "chances";

    private static final RedisScript<Long> SET_IF_LIVE = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return 0
            end
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            return 1
            """, Long.class);

    /** Returns the chances left after taking one, or -1 when there were none. */
    private static final RedisScript<Long> CONSUME_CHANCE = RedisScript.of("""
            local chances = tonumber(redis.call('HGET', KEYS[1], 'chances') or '0')
            if chances <= 0 then
              return -1
            end
            return redis.call('HINCRBY', KEYS[1], 'chances', -1)
            """, Long.class);

    private static final RedisScript<Long> REFUND_CHANCE = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return -1
            end
            return redis.call('HINCRBY', KEYS[1], 'chances', 1)
            """, Long.class);

    private final StringRedisTemplate redis;

    RedisBattleLiveStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void open(long battleId, Collection<Long> playerIds, int chances, Duration ttl) {
        for (long playerId : playerIds) {
            String key = key(battleId, playerId);
            redis.opsForHash().putAll(key, Map.of(READY, "0", CODE, "", CHANCES, String.valueOf(chances)));
            redis.expire(key, ttl);
        }
    }

    @Override
    public Map<Long, PlayerState> players(long battleId, Collection<Long> playerIds) {
        Map<Long, PlayerState> states = new LinkedHashMap<>();
        for (long playerId : playerIds) {
            Map<Object, Object> hash = redis.opsForHash().entries(key(battleId, playerId));
            if (!hash.isEmpty()) {
                states.put(playerId, new PlayerState(
                        "1".equals(hash.get(READY)),
                        String.valueOf(hash.getOrDefault(CODE, "")),
                        Integer.parseInt(String.valueOf(hash.getOrDefault(CHANCES, "0")))));
            }
        }
        return states;
    }

    @Override
    public boolean isLive(long battleId, long playerId) {
        return Boolean.TRUE.equals(redis.hasKey(key(battleId, playerId)));
    }

    @Override
    public void markReady(long battleId, long playerId) {
        redis.execute(SET_IF_LIVE, List.of(key(battleId, playerId)), READY, "1");
    }

    @Override
    public void updateCode(long battleId, long playerId, String code) {
        redis.execute(SET_IF_LIVE, List.of(key(battleId, playerId)), CODE, code);
    }

    @Override
    public OptionalInt consumeChance(long battleId, long playerId) {
        Long left = redis.execute(CONSUME_CHANCE, List.of(key(battleId, playerId)));
        return left == null || left < 0 ? OptionalInt.empty() : OptionalInt.of(left.intValue());
    }

    @Override
    public void refundChance(long battleId, long playerId) {
        redis.execute(REFUND_CHANCE, List.of(key(battleId, playerId)));
    }

    @Override
    public void close(long battleId, Collection<Long> playerIds) {
        redis.delete(playerIds.stream().map(playerId -> key(battleId, playerId)).toList());
    }

    private static String key(long battleId, long playerId) {
        return "battle:" + battleId + ":player:" + playerId;
    }
}
