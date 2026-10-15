package com.bunnycode.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("Password encoding")
class PasswordEncoderConfigurationTest {

    private final PasswordEncoder encoder = new PasswordEncoderConfiguration().passwordEncoder();

    @Nested
    @DisplayName("Given a hash written by v1 (un-prefixed bcrypt, cost 5, $2b$)")
    class LegacyHash {

        private final String legacy = new BCryptPasswordEncoder(BCryptPasswordEncoder.BCryptVersion.$2B, 5)
                .encode("test1234");

        @Test
        @DisplayName("then the right password still verifies")
        void stillVerifies() {
            assertThat(legacy).startsWith("$2b$05$");
            assertThat(encoder.matches("test1234", legacy)).isTrue();
            assertThat(encoder.matches("wrong-password1", legacy)).isFalse();
        }

        @Test
        @DisplayName("then it is reported as needing an upgrade")
        void needsUpgrade() {
            assertThat(encoder.upgradeEncoding(legacy)).isTrue();
        }
    }

    @Test
    @DisplayName("When encoding a new password, then the hash is {bcrypt}-prefixed with cost 12 and needs no upgrade")
    void encodesNewPasswords() {
        String hash = encoder.encode("test1234");

        assertThat(hash).startsWith("{bcrypt}$2a$12$").hasSizeLessThanOrEqualTo(100);
        assertThat(encoder.matches("test1234", hash)).isTrue();
        assertThat(encoder.upgradeEncoding(hash)).isFalse();
    }
}
