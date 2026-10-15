package com.bunnycode.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bunnycode.RedisTestConfiguration;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;

@DataRedisTest
@Import({RedisTestConfiguration.class, RedisRateLimiter.class})
@DisplayName("Redis rate limiter")
class RedisRateLimiterIT {

    @Autowired
    private RedisRateLimiter limiter;

    @Test
    @DisplayName("allows up to the limit within a window, then resets when the window expires")
    void fixedWindow() {
        String key = "test:" + System.nanoTime();

        assertThat(limiter.tryAcquire(key, 2, Duration.ofMillis(500))).isTrue();
        assertThat(limiter.tryAcquire(key, 2, Duration.ofMillis(500))).isTrue();
        assertThat(limiter.tryAcquire(key, 2, Duration.ofMillis(500))).isFalse();
        Awaitility.await().atMost(Duration.ofSeconds(3))
                .until(() -> limiter.tryAcquire(key, 2, Duration.ofMillis(500)));
    }
}
