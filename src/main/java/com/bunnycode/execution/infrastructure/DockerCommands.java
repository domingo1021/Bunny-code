package com.bunnycode.execution.infrastructure;

import com.bunnycode.execution.application.ExecutionProperties;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The docker invocations, as argument vectors. Limits are v1's ({@code --cpus 0.2 --memory 20m},
 * 10 s) plus hardening v1 lacked: no network, read-only root filesystem, no capabilities, no
 * privilege escalation, bounded process count and no swap.
 */
@Component
class DockerCommands {

    private final ExecutionProperties properties;

    DockerCommands(ExecutionProperties properties) {
        this.properties = properties;
    }

    /** {@code node -} reads the program from stdin, so user code never touches a file path or argument. */
    List<String> run(String containerName) {
        return List.of(
                "docker", "run", "--interactive",
                "--name", containerName,
                "--network", "none",
                "--cpus", properties.cpus(),
                "--memory", properties.memory(),
                "--memory-swap", properties.memory(),
                "--pids-limit", String.valueOf(properties.pidsLimit()),
                "--read-only",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--entrypoint", "node",
                properties.image(),
                "-");
    }

    List<String> inspectOomKilled(String containerName) {
        return List.of("docker", "inspect", "--format", "{{.State.OOMKilled}}", containerName);
    }

    List<String> remove(String containerName) {
        return List.of("docker", "rm", "--force", containerName);
    }
}
