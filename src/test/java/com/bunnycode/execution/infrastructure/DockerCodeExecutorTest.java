package com.bunnycode.execution.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.bunnycode.execution.ExecutionResult;
import com.bunnycode.execution.application.ExecutionProperties;
import com.bunnycode.execution.application.ExecutionProperties.Mode;
import com.bunnycode.execution.infrastructure.ProcessRunner.ProcessResult;
import com.bunnycode.shared.error.BunnyException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Classifying sandbox runs")
class DockerCodeExecutorTest {

    private final ExecutionProperties properties = DockerCommandsTest.properties(Mode.LOCAL, Duration.ofSeconds(10));
    private final ProcessRunner runner = mock(ProcessRunner.class);
    private final SandboxTargetSelector selector = mock(SandboxTargetSelector.class);
    private final DockerCodeExecutor executor =
            new DockerCodeExecutor(properties, selector, new DockerCommands(properties), runner);

    @BeforeEach
    void localTarget() {
        given(selector.select()).willReturn(new CommandTransport.Local());
    }

    private void givenRun(ProcessResult result) {
        given(runner.run(argThat(isRun()), any(), any(), anyInt())).willReturn(result);
    }

    private void givenOomKilled(boolean killed) {
        given(runner.run(argThat(command -> command.contains("inspect")), any(), any(), anyInt()))
                .willReturn(new ProcessResult(0, killed + "\n", "", false, false));
    }

    private static org.mockito.ArgumentMatcher<List<String>> isRun() {
        return command -> command != null && command.contains("run");
    }

    @Test
    @DisplayName("exit 0 → Succeeded with stdout")
    void success() {
        givenRun(new ProcessResult(0, "hi\n", "", false, false));
        assertThat(executor.execute("console.log('hi')")).isEqualTo(new ExecutionResult.Succeeded("hi\n"));
    }

    @Test
    @DisplayName("timeout → TimedOut with the configured limit")
    void timeout() {
        givenRun(new ProcessResult(-1, "", "", true, false));
        assertThat(executor.execute("while(true){}")).isEqualTo(new ExecutionResult.TimedOut(Duration.ofSeconds(10)));
    }

    @Test
    @DisplayName("killed by the kernel OOM killer → OutOfMemory")
    void oomKilled() {
        givenRun(new ProcessResult(137, "", "", false, false));
        givenOomKilled(true);
        assertThat(executor.execute("big()")).isEqualTo(new ExecutionResult.OutOfMemory());
    }

    @Test
    @DisplayName("V8 heap exhaustion → OutOfMemory")
    void heapOom() {
        givenRun(new ProcessResult(134, "", "FATAL ERROR: Reached heap limit Allocation failed - JavaScript heap out of memory", false, false));
        assertThat(executor.execute("big()")).isEqualTo(new ExecutionResult.OutOfMemory());
    }

    @Test
    @DisplayName("user error → Failed with a cleaned stderr")
    void userError() {
        givenRun(new ProcessResult(1, "partial\n", "Error: boom\n    at [stdin]:1:7\nNode.js v24", false, false));
        givenOomKilled(false);
        assertThat(executor.execute("throw new Error('boom')"))
                .isEqualTo(new ExecutionResult.Failed("partial\n", "Error: boom"));
    }

    @Test
    @DisplayName("docker/ssh failures are infrastructure problems, not user errors")
    void infrastructureFailure() {
        givenRun(new ProcessResult(125, "", "docker: Error response from daemon", false, false));
        assertThatThrownBy(() -> executor.execute("1")).isInstanceOfSatisfying(BunnyException.class,
                e -> assertThat(e.code()).isEqualTo("execution.unavailable"));
    }

    @Test
    @DisplayName("the container is always removed, even after a timeout")
    void alwaysCleansUp() {
        givenRun(new ProcessResult(-1, "", "", true, false));

        executor.execute("while(true){}");

        then(runner).should().run(argThat(command -> command.containsAll(List.of("rm", "--force"))), any(), any(), anyInt());
    }
}
