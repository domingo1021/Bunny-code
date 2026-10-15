package com.bunnycode.shared.ratelimit;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * {@code INCR} + {@code PEXPIRE} in one Lua call, so the counter can never be left without a TTL
 * if the process dies between the two commands.
 */
@Component
class RedisRateLimiter implements RateLimiter {

    private static final String KEY_PREFIX = "rate-limit:";
    private static final RedisScript<Long> HIT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;

    RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
        Long count = redis.execute(HIT, List.of(KEY_PREFIX + key), String.valueOf(window.toMillis()));
        return count != null && count <= limit;
    }
}
