package com.bunnycode.execution.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.bunnycode.execution.CodeExecutor;
import com.bunnycode.execution.ExecutionResult;
import com.bunnycode.execution.application.ExecutionOutcome.Status;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.error.ErrorKind;
import com.bunnycode.shared.ratelimit.RateLimiter;
import com.bunnycode.shared.security.AuthenticatedUser;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Running code from the workspace")
class ExecutionServiceTest {

    private final CodeExecutor executor = mock(CodeExecutor.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final ExecutionProperties properties = new ExecutionProperties(ExecutionProperties.Mode.LOCAL, "node",
            "0.2", "20m", 64, Duration.ofSeconds(10), 1000, 10, new ExecutionProperties.Ssh("ubuntu", ""),
            new ExecutionProperties.HostHealth(9100, Duration.ofSeconds(1), 0.8, 0.8));
    private final ExecutionService service = new ExecutionService(executor, rateLimiter, properties);
    private final AuthenticatedUser user = new AuthenticatedUser(1L, "bunny");

    static Stream<Arguments> outcomes() {
        return Stream.of(
                Arguments.of(new ExecutionResult.Succeeded("42\n"), Status.SUCCEEDED, "42\n"),
                Arguments.of(new ExecutionResult.Failed("", "ReferenceError: x is not defined"), Status.FAILED,
                        "ReferenceError: x is not defined"),
                Arguments.of(new ExecutionResult.TimedOut(Duration.ofSeconds(10)), Status.TIMED_OUT,
                        "Your code ran for more than 10 seconds and was stopped."),
                Arguments.of(new ExecutionResult.OutOfMemory(), Status.OUT_OF_MEMORY,
                        "Your code ran out of memory and was stopped. Check for runaway loops or very large data."));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("outcomes")
    @DisplayName("Each sandbox result becomes terminal output the user can read")
    void mapsOutcomes(ExecutionResult result, Status status, String output) {
        given(rateLimiter.tryAcquire(any(), anyInt(), any())).willReturn(true);
        given(executor.execute("code")).willReturn(result);

        assertThat(service.run(user, "code")).isEqualTo(new ExecutionOutcome(status, output));
    }

    @Test
    @DisplayName("Given blank code, then nothing is run")
    void blank() {
        assertThatThrownBy(() -> service.run(user, "  ")).isInstanceOfSatisfying(BunnyException.class,
                e -> assertThat(e.code()).isEqualTo("execution.empty"));
        then(executor).should(never()).execute(any());
    }

    @Test
    @DisplayName("Given the user exceeded the per-minute budget, then the run is refused with 429")
    void rateLimited() {
        given(rateLimiter.tryAcquire(eq("execution:1"), eq(10), eq(Duration.ofMinutes(1)))).willReturn(false);

        assertThatThrownBy(() -> service.run(user, "code")).isInstanceOfSatisfying(BunnyException.class,
                e -> assertThat(e.kind()).isEqualTo(ErrorKind.TOO_MANY_REQUESTS));
        then(executor).should(never()).execute(any());
    }
}
