package com.bunnycode.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.bunnycode.identity.application.AuthResult;
import com.bunnycode.identity.application.AuthService;
import com.bunnycode.identity.application.UserProfileView;
import com.bunnycode.identity.application.UserQueryService;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.security.WebSecurityTestConfiguration;
import com.bunnycode.shared.security.WebSecurityTestConfiguration.TestTokens;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest({AuthController.class, UserController.class})
@ActiveProfiles("test")
@Import(WebSecurityTestConfiguration.class)
@DisplayName("Identity HTTP API")
class AuthControllerTest {

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private TestTokens tokens;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private UserQueryService userQueryService;

    private final UserProfileView bunny = new UserProfileView(1L, "bunny", "bunny@example.com", "https://cdn/p.png", null, 1);

    @Nested
    @DisplayName("POST /api/v2/auth/sign-up")
    class SignUp {

        @Test
        @DisplayName("Given valid details, then 201 with an access token and the new user")
        void created() {
            given(authService.signUp("bunny", "bunny@example.com", "secret1"))
                    .willReturn(new AuthResult("jwt", "Bearer", Instant.parse("2026-10-15T00:00:00Z"), bunny));

            assertThat(mvc.post().uri("/api/v2/auth/sign-up").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"bunny","email":"bunny@example.com","password":"secret1"}"""))
                    .hasStatus(HttpStatus.CREATED)
                    .bodyJson().satisfies(json -> {
                        json.assertThat().extractingPath("$.accessToken").isEqualTo("jwt");
                        json.assertThat().extractingPath("$.user.email").isEqualTo("bunny@example.com");
                    });
        }

        @Test
        @DisplayName("Given a weak password, then 400 problem detail naming the field and the rule")
        void weakPassword() {
            assertThat(mvc.post().uri("/api/v2/auth/sign-up").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"bunny","email":"bunny@example.com","password":"short"}"""))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson().satisfies(json -> {
                        json.assertThat().extractingPath("$.code").isEqualTo("request.invalid");
                        json.assertThat().extractingPath("$.errors[0].field").isEqualTo("password");
                        json.assertThat().extractingPath("$.detail").asString().contains("6–30 characters");
                    });
        }

        @Test
        @DisplayName("Given a taken e-mail, then 409 with the service's message")
        void conflict() {
            given(authService.signUp("bunny", "bunny@example.com", "secret1"))
                    .willThrow(BunnyException.conflict("user.email-taken", "That e-mail address is already registered."));

            assertThat(mvc.post().uri("/api/v2/auth/sign-up").contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"name":"bunny","email":"bunny@example.com","password":"secret1"}"""))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson().extractingPath("$.code").isEqualTo("user.email-taken");
        }
    }

    @Test
    @DisplayName("POST /api/v2/auth/sign-in with wrong credentials → 401 problem detail")
    void signInFails() {
        given(authService.signIn("bunny@example.com", "nope"))
                .willThrow(BunnyException.unauthenticated("auth.invalid-credentials", "The e-mail or password is incorrect."));

        assertThat(mvc.post().uri("/api/v2/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"bunny@example.com","password":"nope"}"""))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.code").isEqualTo("auth.invalid-credentials");
    }

    @Nested
    @DisplayName("GET /api/v2/users/me")
    class Me {

        @Test
        @DisplayName("Without a token → 401 auth.required")
        void anonymous() {
            assertThat(mvc.get().uri("/api/v2/users/me"))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson().extractingPath("$.code").isEqualTo("auth.required");
        }

        @Test
        @DisplayName("With a forged token → 401 auth.invalid-token")
        void forged() {
            assertThat(mvc.get().uri("/api/v2/users/me").header("Authorization", "Bearer not.a.jwt"))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson().extractingPath("$.code").isEqualTo("auth.invalid-token");
        }

        @Test
        @DisplayName("With a valid token → my own profile, including e-mail")
        void ownProfile() {
            given(userQueryService.ownProfileOf(1L)).willReturn(bunny);

            assertThat(mvc.get().uri("/api/v2/users/me").header("Authorization", tokens.bearer(1L, "bunny")))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.email").isEqualTo("bunny@example.com");
        }
    }

    @Test
    @DisplayName("GET /api/v2/users/{id} is public and omits the e-mail")
    void publicProfile() {
        given(userQueryService.profileOf(2L)).willReturn(new UserProfileView(2L, "other", null, "https://cdn/p.png", null, 1));

        assertThat(mvc.get().uri("/api/v2/users/2"))
                .hasStatusOk()
                .bodyJson().doesNotHavePath("$.email");
    }
}
