package com.bunnycode.execution.application;

import com.bunnycode.execution.ExecutionResult;
import java.util.Objects;

/**
 * What the workspace terminal shows. Program failures are a normal outcome with text the user is
 * meant to read, so they are returned with 200 rather than as HTTP errors.
 */
public record ExecutionOutcome(Status status, String output) {

    public ExecutionOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(output, "output");
    }

    static ExecutionOutcome from(ExecutionResult result) {
        return switch (result) {
            case ExecutionResult.Succeeded(String stdout) -> new ExecutionOutcome(Status.SUCCEEDED, stdout);
            case ExecutionResult.Failed(String stdout, String stderr) -> new ExecutionOutcome(Status.FAILED,
                    stdout.isEmpty() ? stderr : stdout + System.lineSeparator() + stderr);
            case ExecutionResult.TimedOut(var limit) -> new ExecutionOutcome(Status.TIMED_OUT,
                    "Your code ran for more than %d seconds and was stopped.".formatted(limit.toSeconds()));
            case ExecutionResult.OutOfMemory() -> new ExecutionOutcome(Status.OUT_OF_MEMORY,
                    "Your code ran out of memory and was stopped. Check for runaway loops or very large data.");
        };
    }

    public enum Status {
        SUCCEEDED,
        FAILED,
        TIMED_OUT,
        OUT_OF_MEMORY
    }
}
