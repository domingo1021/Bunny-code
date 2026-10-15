package com.bunnycode.project.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CodingEvent")
class CodingEventTest {

    @ParameterizedTest
    @ValueSource(strings = {"create", "delete", "enter", "CopyLine", "DeleteLine", "line-start", "duplicate-line"})
    @DisplayName("Accepts v1 and v2 action names")
    void acceptsKnownActions(String action) {
        assertThatCode(() -> new CodingEvent(action, 0, 0, "x", Instant.EPOCH)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "drop table", "a,b=c", "-start", "abcdefghijklmnopqrstuvwxyzabcde"})
    @DisplayName("Rejects actions that are not plain words (they become InfluxDB tags)")
    void rejectsOddActions(String action) {
        assertThatThrownBy(() -> new CodingEvent(action, 0, 0, "x", Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects negative positions and oversized code")
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> new CodingEvent("create", -1, 0, "x", Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CodingEvent("create", 0, 0, "x".repeat(CodingEvent.MAX_CODE_LENGTH + 1), Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
