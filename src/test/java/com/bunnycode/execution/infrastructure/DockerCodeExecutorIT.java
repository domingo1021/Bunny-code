package com.bunnycode.execution.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bunnycode.execution.ExecutionResult;
import com.bunnycode.execution.application.ExecutionProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The real sandbox: Docker with the production limits. Also proves the hardening holds — no
 * network, no host access, injection attempts are inert.
 */
@DisplayName("Docker sandbox (real containers)")
class DockerCodeExecutorIT {

    private final ExecutionProperties properties = new ExecutionProperties(ExecutionProperties.Mode.LOCAL,
            "node:24-alpine", "0.2", "20m", 64, Duration.ofSeconds(4), 65_536, 100,
            new ExecutionProperties.Ssh("ubuntu", ""), new ExecutionProperties.HostHealth(9100, Duration.ofSeconds(1), 0.8, 0.8));
    private final DockerCodeExecutor executor = new DockerCodeExecutor(properties,
            new SandboxTargetSelector(properties, null, null), new DockerCommands(properties), new ProcessRunner());

    @Test
    @DisplayName("runs a program and returns what it printed")
    void helloWorld() {
        assertThat(executor.execute("console.log('Hello, Bunny!')"))
                .isEqualTo(new ExecutionResult.Succeeded("Hello, Bunny!\n"));
    }

    @Test
    @DisplayName("reports the user's own error with the code frame but without stack frames")
    void userError() {
        assertThat(executor.execute("const a = 1;\nundefinedFunction();")).isInstanceOfSatisfying(ExecutionResult.Failed.class,
                failed -> assertThat(failed.stderr()).contains("ReferenceError: undefinedFunction is not defined")
                        .doesNotContain("    at "));
    }

    @Test
    @DisplayName("stops an infinite loop at the time limit and removes the container")
    void timeout() {
        assertThat(executor.execute("while (true) {}")).isEqualTo(new ExecutionResult.TimedOut(Duration.ofSeconds(4)));
    }

    @Test
    @DisplayName("stops a program that exhausts the 20 MB memory limit")
    void outOfMemory() {
        assertThat(executor.execute("const hog = []; while (true) { hog.push(new Array(1_000_000).fill(7)); }"))
                .isEqualTo(new ExecutionResult.OutOfMemory());
    }

    @Test
    @DisplayName("has no network access")
    void noNetwork() {
        ExecutionResult result = executor.execute("""
                const socket = require('net').connect(53, '1.1.1.1');
                socket.on('connect', () => { console.log('online'); process.exit(0); });
                socket.on('error', () => { console.log('offline'); process.exit(0); });
                setTimeout(() => { console.log('offline'); process.exit(0); }, 1500);
                """);
        assertThat(result).isEqualTo(new ExecutionResult.Succeeded("offline\n"));
    }

    @Test
    @DisplayName("shell syntax in user code is just JavaScript text — nothing reaches a shell (v1 RCE)")
    void injectionIsInert() {
        assertThat(executor.execute("console.log('$(touch /tmp/pwned); `id`; rm -rf /')"))
                .isEqualTo(new ExecutionResult.Succeeded("$(touch /tmp/pwned); `id`; rm -rf /\n"));
    }

    @Test
    @DisplayName("cannot write to the container's root filesystem")
    void readOnlyFilesystem() {
        assertThat(executor.execute("require('fs').writeFileSync('/evil.txt', 'x')"))
                .isInstanceOfSatisfying(ExecutionResult.Failed.class,
                        failed -> assertThat(failed.stderr()).contains("EROFS"));
    }
}
