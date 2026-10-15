package com.bunnycode.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.error.ErrorKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("EmailAddress")
class EmailAddressTest {

    @Test
    @DisplayName("When created from user input, then it is trimmed and lower-cased")
    void normalises() {
        assertThat(EmailAddress.of("  Bunny@Example.COM ").value()).isEqualTo("bunny@example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"bunny", "bunny@", "@example.com", "bunny@example", "bun ny@example.com"})
    @DisplayName("When the address is malformed, then it is rejected as invalid input")
    void rejectsMalformed(String raw) {
        assertThatThrownBy(() -> EmailAddress.of(raw))
                .isInstanceOfSatisfying(BunnyException.class, e -> {
                    assertThat(e.kind()).isEqualTo(ErrorKind.INVALID_INPUT);
                    assertThat(e.code()).isEqualTo("user.email-invalid");
                });
    }

    @Test
    @DisplayName("When the address is longer than the column, then it is rejected")
    void rejectsTooLong() {
        String tooLong = "a".repeat(250) + "@example.com";
        assertThatThrownBy(() -> EmailAddress.of(tooLong)).isInstanceOf(BunnyException.class);
    }
}
