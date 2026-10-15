package com.bunnycode.execution.application;

import com.bunnycode.execution.CodeExecutor;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.ratelimit.RateLimiter;
import com.bunnycode.shared.security.AuthenticatedUser;
import java.time.Duration;
import org.springframework.stereotype.Service;

/** Workspace "Run" button: signed-in users only, rate-limited per user (v1 was open to anyone). */
@Service
public class ExecutionService {

    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    private final CodeExecutor executor;
    private final RateLimiter rateLimiter;
    private final ExecutionProperties properties;

    ExecutionService(CodeExecutor executor, RateLimiter rateLimiter, ExecutionProperties properties) {
        this.executor = executor;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    public ExecutionOutcome run(AuthenticatedUser user, String code) {
        if (code.isBlank()) {
            throw BunnyException.invalid("execution.empty", "Why not write some code first? Happy coding!");
        }
        if (!rateLimiter.tryAcquire("execution:" + user.id(), properties.runsPerMinute(), RATE_WINDOW)) {
            throw BunnyException.tooManyRequests("execution.rate-limited",
                    "You are running code very quickly — please wait a minute and try again.");
        }
        return ExecutionOutcome.from(executor.execute(code));
    }
}
