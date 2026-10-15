package com.bunnycode.execution;

/** Runs untrusted JavaScript in an isolated, resource-limited sandbox. */
public interface CodeExecutor {

    /**
     * Runs a complete program and classifies the outcome. Program failures (errors, timeouts,
     * out-of-memory) are results, not exceptions.
     *
     * @throws com.bunnycode.shared.error.BunnyException with kind {@code UNAVAILABLE} when no
     *         sandbox can take the job right now
     */
    ExecutionResult execute(String program);
}
