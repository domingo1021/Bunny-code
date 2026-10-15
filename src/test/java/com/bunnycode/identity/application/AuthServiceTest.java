package com.bunnycode.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bunnycode.identity.domain.UserAccount;
import com.bunnycode.identity.domain.UserAccountRepository;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.error.ErrorKind;
import com.bunnycode.storage.FileStorage;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    private UserAccountRepository users;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenIssuer tokenIssuer;
    @Mock
    private FileStorage storage;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        given(passwordEncoder.encode(anyString())).willReturn("{bcrypt}hashed");
        authService = new AuthService(users, passwordEncoder, tokenIssuer, storage);
    }

    @Nested
    @DisplayName("Sign-up")
    class SignUp {

        @Test
        @DisplayName("Given a new e-mail and name, when signing up, then the account is saved with a hashed password and a token is issued")
        void createsAccount() {
            given(users.saveAndFlush(any(UserAccount.class))).willAnswer(invocation -> {
                UserAccount saved = invocation.getArgument(0);
                org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 7L);
                return saved;
            });
            given(tokenIssuer.issueFor(any())).willReturn(new AccessToken("jwt", Instant.EPOCH));
            given(storage.publicUrl(anyString())).willReturn("https://cdn/user/user_icon.png");

            AuthResult result = authService.signUp(" bunny ", "Bunny@Example.com", "secret1");

            assertThat(result.accessToken()).isEqualTo("jwt");
            assertThat(result.tokenType()).isEqualTo("Bearer");
            assertThat(result.user().name()).isEqualTo("bunny");
            assertThat(result.user().email()).isEqualTo("bunny@example.com");
            then(users).should().saveAndFlush(org.mockito.ArgumentMatchers.argThat(user ->
                    user.getPasswordHash().equals("{bcrypt}hashed") && !user.getPasswordHash().contains("secret1")));
        }

        @Test
        @DisplayName("Given the e-mail is registered, when signing up, then it conflicts and nothing is saved")
        void emailTaken() {
            given(users.existsByEmail("bunny@example.com")).willReturn(true);

            assertThatThrownBy(() -> authService.signUp("bunny", "bunny@example.com", "secret1"))
                    .isInstanceOfSatisfying(BunnyException.class, e -> assertThat(e.code()).isEqualTo("user.email-taken"));
            then(users).should(never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Given the name is taken, when signing up, then it conflicts")
        void nameTaken() {
            given(users.existsByName("bunny")).willReturn(true);

            assertThatThrownBy(() -> authService.signUp("bunny", "new@example.com", "secret1"))
                    .isInstanceOfSatisfying(BunnyException.class, e -> assertThat(e.code()).isEqualTo("user.name-taken"));
        }

        @Test
        @DisplayName("Given a concurrent sign-up wins the race, when the insert hits the unique key, then it conflicts")
        void raceLost() {
            given(users.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("duplicate"));

            assertThatThrownBy(() -> authService.signUp("bunny", "bunny@example.com", "secret1"))
                    .isInstanceOfSatisfying(BunnyException.class, e -> assertThat(e.kind()).isEqualTo(ErrorKind.CONFLICT));
        }
    }

    @Nested
    @DisplayName("Sign-in")
    class SignIn {

        @Test
        @DisplayName("Given an unknown e-mail, when signing in, then it fails like a wrong password and still spends bcrypt time")
        void unknownEmail() {
            given(users.findByEmail("ghost@example.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.signIn("ghost@example.com", "secret1"))
                    .isInstanceOfSatisfying(BunnyException.class, e -> {
                        assertThat(e.kind()).isEqualTo(ErrorKind.UNAUTHENTICATED);
                        assertThat(e.code()).isEqualTo("auth.invalid-credentials");
                    });
            then(passwordEncoder).should().matches(eq("secret1"), eq("{bcrypt}hashed"));
        }

        @Test
        @DisplayName("Given a wrong password, when signing in, then it fails with the same message")
        void wrongPassword() {
            UserAccount user = TokenIssuerTest.user(1L, "bunny");
            given(users.findByEmail("bunny@example.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrong", user.getPasswordHash())).willReturn(false);

            assertThatThrownBy(() -> authService.signIn("bunny@example.com", "wrong"))
                    .isInstanceOfSatisfying(BunnyException.class, e -> assertThat(e.code()).isEqualTo("auth.invalid-credentials"));
        }

        @Test
        @DisplayName("Given a legacy hash, when signing in successfully, then the password is re-hashed")
        void upgradesLegacyHash() {
            UserAccount user = TokenIssuerTest.user(1L, "bunny");
            user.replacePasswordHash("$2b$05$legacy");
            given(users.findByEmail("bunny@example.com")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("secret1", "$2b$05$legacy")).willReturn(true);
            given(passwordEncoder.upgradeEncoding("$2b$05$legacy")).willReturn(true);
            given(tokenIssuer.issueFor(user)).willReturn(new AccessToken("jwt", Instant.EPOCH));
            given(storage.publicUrl(anyString())).willReturn("https://cdn/pic");

            authService.signIn("bunny@example.com", "secret1");

            assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}hashed");
        }
    }
}
