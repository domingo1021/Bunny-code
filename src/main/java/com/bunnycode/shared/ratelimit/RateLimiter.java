package com.bunnycode.shared.ratelimit;

import java.time.Duration;

/** Fixed-window rate limiter shared by all application instances. */
public interface RateLimiter {

    /**
     * Records one hit for {@code key} and reports whether it is still within {@code limit} hits
     * for the current {@code window}. The window starts at the first hit.
     */
    boolean tryAcquire(String key, int limit, Duration window);
}
