package com.bunnycode.execution.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bunnycode.execution.infrastructure.ProcessRunner.ProcessResult;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.WINDOWS)
@DisplayName("ProcessRunner (real OS processes)")
class ProcessRunnerTest {

    private final ProcessRunner runner = new ProcessRunner();

    @Test
    @DisplayName("feeds stdin and captures stdout and the exit code")
    void echoesStdin() {
        ProcessResult result = runner.run(List.of("cat"), "hello\n".getBytes(StandardCharsets.UTF_8),
                Duration.ofSeconds(5), 1024);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("hello\n");
        assertThat(result.timedOut()).isFalse();
    }

    @Test
    @DisplayName("captures stderr and non-zero exit codes")
    void capturesFailure() {
        ProcessResult result = runner.run(List.of("sh", "-c", "echo oops >&2; exit 3"), new byte[0],
                Duration.ofSeconds(5), 1024);

        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(result.stderr()).isEqualTo("oops\n");
    }

    @Test
    @DisplayName("kills processes that exceed the timeout")
    void timesOut() {
        long started = System.nanoTime();
        ProcessResult result = runner.run(List.of("sleep", "30"), new byte[0], Duration.ofMillis(300), 1024);

        assertThat(result.timedOut()).isTrue();
        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("keeps only the first bytes of huge output but still drains it (no pipe deadlock)")
    void truncates() {
        ProcessResult result = runner.run(List.of("sh", "-c", "yes a | head -c 1000000"), new byte[0],
                Duration.ofSeconds(10), 100);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).hasSize(100);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    @DisplayName("does not interpret arguments with a shell")
    void noShell() {
        ProcessResult result = runner.run(List.of("echo", "$(whoami); rm -rf /"), new byte[0], Duration.ofSeconds(5), 1024);

        assertThat(result.stdout()).isEqualTo("$(whoami); rm -rf /\n");
    }
}
