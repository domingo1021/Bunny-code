package com.bunnycode.execution;

import java.time.Duration;
import java.util.Objects;

/** The four ways a sandboxed program can end. Exhaustive, so callers can {@code switch} without a default. */
public sealed interface ExecutionResult {

    record Succeeded(String stdout) implements ExecutionResult {
        public Succeeded {
            Objects.requireNonNull(stdout, "stdout");
        }
    }

    /** The program itself failed (uncaught error, non-zero exit). {@code stderr} is cleaned of stack frames. */
    record Failed(String stdout, String stderr) implements ExecutionResult {
        public Failed {
            Objects.requireNonNull(stdout, "stdout");
            Objects.requireNonNull(stderr, "stderr");
        }
    }

    record TimedOut(Duration limit) implements ExecutionResult {
        public TimedOut {
            Objects.requireNonNull(limit, "limit");
        }
    }

    record OutOfMemory() implements ExecutionResult {
    }
}
