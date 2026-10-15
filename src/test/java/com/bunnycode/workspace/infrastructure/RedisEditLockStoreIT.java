package com.bunnycode.workspace.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bunnycode.RedisTestConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

@DataRedisTest
@Import({RedisTestConfiguration.class, RedisEditLockStore.class})
@DisplayName("Edit locks in Redis")
class RedisEditLockStoreIT {

    private static final Duration TTL = Duration.ofMinutes(5);

    @Autowired
    private RedisEditLockStore store;
    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void flush() {
        redis.execute(connection -> {
            connection.serverCommands().flushAll();
            return null;
        }, true);
    }

    @Test
    @DisplayName("the first session gets the lock, a second one does not, and the holder can re-acquire")
    void exclusive() {
        assertThat(store.tryAcquire(1L, "tab-1", TTL)).isTrue();
        assertThat(store.tryAcquire(1L, "tab-2", TTL)).isFalse();
        assertThat(store.tryAcquire(1L, "tab-1", TTL)).isTrue();
        assertThat(redis.getExpire("edit-lock:version:1")).isPositive();
    }

    @Test
    @DisplayName("taking over returns the previous holder and transfers the lock")
    void takeOver() {
        store.tryAcquire(1L, "tab-1", TTL);

        assertThat(store.takeOver(1L, "tab-2", TTL)).contains("tab-1");
        assertThat(store.tryAcquire(1L, "tab-1", TTL)).isFalse();
        assertThat(store.takeOver(2L, "tab-2", TTL)).isEmpty();
    }

    @Test
    @DisplayName("only the holder can release; a stale release after a take-over is ignored")
    void compareAndRelease() {
        store.tryAcquire(1L, "tab-1", TTL);
        store.takeOver(1L, "tab-2", TTL);

        assertThat(store.release(1L, "tab-1")).isFalse();
        assertThat(store.release(1L, "tab-2")).isTrue();
        assertThat(store.tryAcquire(1L, "tab-3", TTL)).isTrue();
    }

    @Test
    @DisplayName("a disconnecting session releases every lock it held, and nothing else")
    void releaseAll() {
        store.tryAcquire(1L, "tab-1", TTL);
        store.tryAcquire(2L, "tab-1", TTL);
        store.tryAcquire(3L, "tab-2", TTL);

        store.releaseAll("tab-1");

        assertThat(store.tryAcquire(1L, "tab-9", TTL)).isTrue();
        assertThat(store.tryAcquire(2L, "tab-9", TTL)).isTrue();
        assertThat(store.tryAcquire(3L, "tab-9", TTL)).isFalse();
    }
}
