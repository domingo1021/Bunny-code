package com.bunnycode.execution.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.bunnycode.execution.application.ExecutionOutcome;
import com.bunnycode.execution.application.ExecutionService;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.shared.security.WebSecurityTestConfiguration;
import com.bunnycode.shared.security.WebSecurityTestConfiguration.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ExecutionController.class)
@ActiveProfiles("test")
@Import(WebSecurityTestConfiguration.class)
@DisplayName("POST /api/v2/executions")
class ExecutionControllerTest {

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private TestTokens tokens;
    @MockitoBean
    private ExecutionService executions;

    @Test
    @DisplayName("Anonymous callers cannot run code (v1 let anyone run anything)")
    void anonymous() {
        assertThat(mvc.post().uri("/api/v2/executions").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"1\"}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("A program failure is a 200 result the terminal shows, not an HTTP error")
    void programFailureIs200() {
        given(executions.run(new AuthenticatedUser(1L, "bunny"), "boom()"))
                .willReturn(new ExecutionOutcome(ExecutionOutcome.Status.FAILED, "ReferenceError: boom is not defined"));

        assertThat(mvc.post().uri("/api/v2/executions").header("Authorization", tokens.bearer(1L, "bunny"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"boom()\"}"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("FAILED");
    }

    @Test
    @DisplayName("Too many runs → 429 problem detail")
    void rateLimited() {
        given(executions.run(new AuthenticatedUser(1L, "bunny"), "1"))
                .willThrow(BunnyException.tooManyRequests("execution.rate-limited", "slow down"));

        assertThat(mvc.post().uri("/api/v2/executions").header("Authorization", tokens.bearer(1L, "bunny"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"1\"}"))
                .hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }
}
