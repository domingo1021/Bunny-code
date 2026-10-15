package com.bunnycode.execution.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.bunnycode.execution.CodeExecutor;
import com.bunnycode.execution.ExecutionResult;
import com.bunnycode.execution.application.ExecutionProperties;
import com.bunnycode.execution.infrastructure.ProcessRunner.ProcessResult;
import com.bunnycode.shared.error.BunnyException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs a program in a throw-away container and classifies the result. The container is always
 * removed, whatever happened — including timeouts, where removing it is what stops the program.
 */
@Component
class DockerCodeExecutor implements CodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(DockerCodeExecutor.class);

    /** docker: 125 daemon error, 126 cannot invoke, 127 not found; ssh: 255 connection failure. */
    private static final Set<Integer> INFRASTRUCTURE_FAILURES = Set.of(125, 126, 127, 255);
    private static final Duration HOUSEKEEPING_TIMEOUT = Duration.ofSeconds(10);
    private static final int HOUSEKEEPING_OUTPUT_BYTES = 4096;

    private final ExecutionProperties properties;
    private final SandboxTargetSelector targetSelector;
    private final DockerCommands docker;
    private final ProcessRunner processRunner;

    DockerCodeExecutor(ExecutionProperties properties, SandboxTargetSelector targetSelector, DockerCommands docker,
                       ProcessRunner processRunner) {
        this.properties = properties;
        this.targetSelector = targetSelector;
        this.docker = docker;
        this.processRunner = processRunner;
    }

    @Override
    public ExecutionResult execute(String program) {
        CommandTransport target = targetSelector.select();
        String container = "bunny-" + UUID.randomUUID();
        try {
            ProcessResult run = processRunner.run(target.wrap(docker.run(container)), program.getBytes(UTF_8),
                    properties.timeout(), properties.maxOutputBytes());
            return classify(run, target, container);
        } finally {
            removeQuietly(target, container);
        }
    }

    private ExecutionResult classify(ProcessResult run, CommandTransport target, String container) {
        if (run.timedOut()) {
            return new ExecutionResult.TimedOut(properties.timeout());
        }
        if (run.exitCode() == 0) {
            return new ExecutionResult.Succeeded(run.stdout());
        }
        if (INFRASTRUCTURE_FAILURES.contains(run.exitCode())) {
            log.error("Sandbox on {} failed to run (exit {}): {}", target.describe(), run.exitCode(), run.stderr());
            throw BunnyException.unavailable("execution.unavailable",
                    "The code runner is unavailable right now — try again in a moment.");
        }
        if (run.stderr().contains("heap out of memory") || wasOomKilled(target, container)) {
            return new ExecutionResult.OutOfMemory();
        }
        return new ExecutionResult.Failed(run.stdout(), StderrCleaner.clean(run.stderr()));
    }

    private boolean wasOomKilled(CommandTransport target, String container) {
        ProcessResult inspect = processRunner.run(target.wrap(docker.inspectOomKilled(container)), new byte[0],
                HOUSEKEEPING_TIMEOUT, HOUSEKEEPING_OUTPUT_BYTES);
        return inspect.exitCode() == 0 && inspect.stdout().strip().equals("true");
    }

    private void removeQuietly(CommandTransport target, String container) {
        try {
            processRunner.run(target.wrap(docker.remove(container)), new byte[0],
                    HOUSEKEEPING_TIMEOUT, HOUSEKEEPING_OUTPUT_BYTES);
        } catch (RuntimeException e) {
            log.warn("Could not remove sandbox container {} on {}", container, target.describe(), e);
        }
    }
}
