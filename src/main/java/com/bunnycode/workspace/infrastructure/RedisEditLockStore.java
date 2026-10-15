package com.bunnycode.workspace.infrastructure;

import com.bunnycode.workspace.application.EditLockStore;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis is the single source of truth for edit locks (v1 also kept a MySQL flag that drifted).
 * Each operation is one Lua script, so check-and-set is atomic without WATCH/MULTI retries.
 *
 * <pre>
 * edit-lock:version:{versionId}  → holder session id (with TTL)
 * edit-lock:session:{sessionId}  → set of version ids the session holds (for disconnect cleanup)
 * </pre>
 */
@Component
class RedisEditLockStore implements EditLockStore {

    private static final RedisScript<Long> ACQUIRE = RedisScript.of("""
            local holder = redis.call('GET', KEYS[1])
            if holder and holder ~= ARGV[1] then
              return 0
            end
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            redis.call('SADD', KEYS[2], ARGV[3])
            redis.call('PEXPIRE', KEYS[2], ARGV[2])
            return 1
            """, Long.class);

    private static final RedisScript<String> TAKE_OVER = RedisScript.of("""
            local previous = redis.call('GET', KEYS[1])
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            redis.call('SADD', KEYS[2], ARGV[3])
            redis.call('PEXPIRE', KEYS[2], ARGV[2])
            return previous
            """, String.class);

    private static final RedisScript<Long> RELEASE = RedisScript.of("""
            redis.call('SREM', KEYS[2], ARGV[2])
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    RedisEditLockStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(long versionId, String sessionId, Duration ttl) {
        Long acquired = redis.execute(ACQUIRE, keys(versionId, sessionId),
                sessionId, String.valueOf(ttl.toMillis()), String.valueOf(versionId));
        return Long.valueOf(1).equals(acquired);
    }

    @Override
    public Optional<String> takeOver(long versionId, String sessionId, Duration ttl) {
        return Optional.ofNullable(redis.execute(TAKE_OVER, keys(versionId, sessionId),
                sessionId, String.valueOf(ttl.toMillis()), String.valueOf(versionId)));
    }

    @Override
    public boolean release(long versionId, String sessionId) {
        Long released = redis.execute(RELEASE, keys(versionId, sessionId), sessionId, String.valueOf(versionId));
        return Long.valueOf(1).equals(released);
    }

    @Override
    public void releaseAll(String sessionId) {
        Set<String> versionIds = redis.opsForSet().members(sessionKey(sessionId));
        if (versionIds != null) {
            versionIds.forEach(versionId -> release(Long.parseLong(versionId), sessionId));
        }
        redis.delete(sessionKey(sessionId));
    }

    private static List<String> keys(long versionId, String sessionId) {
        return List.of("edit-lock:version:" + versionId, sessionKey(sessionId));
    }

    private static String sessionKey(String sessionId) {
        return "edit-lock:session:" + sessionId;
    }
}
