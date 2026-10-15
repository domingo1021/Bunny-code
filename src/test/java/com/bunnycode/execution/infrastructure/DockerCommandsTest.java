package com.bunnycode.execution.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bunnycode.execution.application.ExecutionProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Docker sandbox command")
class DockerCommandsTest {

    static ExecutionProperties properties(ExecutionProperties.Mode mode, Duration timeout) {
        return new ExecutionProperties(mode, "node-tool", "0.2", "20m", 64, timeout, 65_536, 10,
                new ExecutionProperties.Ssh("ubuntu", "/keys/k"),
                new ExecutionProperties.HostHealth(9100, Duration.ofSeconds(3), 0.8, 0.8));
    }

    private final DockerCommands docker = new DockerCommands(properties(ExecutionProperties.Mode.LOCAL, Duration.ofSeconds(10)));

    @Test
    @DisplayName("keeps v1's limits: 0.2 CPU, 20 MB memory, no swap")
    void keepsLimits() {
        List<String> run = docker.run("bunny-1");
        assertThat(run).containsSubsequence("--cpus", "0.2")
                .containsSubsequence("--memory", "20m")
                .containsSubsequence("--memory-swap", "20m");
    }

    @Test
    @DisplayName("adds isolation v1 lacked: no network, read-only root, no capabilities, bounded processes")
    void hardens() {
        assertThat(docker.run("bunny-1"))
                .containsSubsequence("--network", "none")
                .contains("--read-only")
                .containsSubsequence("--cap-drop", "ALL")
                .containsSubsequence("--security-opt", "no-new-privileges")
                .containsSubsequence("--pids-limit", "64");
    }

    @Test
    @DisplayName("reads the program from stdin, so user code is never part of the command line")
    void programFromStdin() {
        assertThat(docker.run("bunny-1")).containsSubsequence("--entrypoint", "node", "node-tool", "-")
                .contains("--interactive")
                .endsWith("-");
    }
}
