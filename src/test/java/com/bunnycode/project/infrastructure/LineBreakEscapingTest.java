package com.bunnycode.project.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Line-break escaping for InfluxDB")
class LineBreakEscapingTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "plain", "\n", "\r\n", "\\", "\\n", "a\\\\nb", "trailing\\", "tab\tand \"quotes\""})
    @DisplayName("round-trips exactly, including literal backslash sequences")
    void roundTrips(String value) {
        assertThat(LineBreakEscaping.unescape(LineBreakEscaping.escape(value))).isEqualTo(value);
    }

    @Test
    @DisplayName("escaped values contain no raw CR or LF")
    void noRawLineBreaks() {
        assertThat(LineBreakEscaping.escape("a\nb\r\nc")).doesNotContain("\n", "\r").isEqualTo("a\\nb\\r\\nc");
    }

    @Test
    @DisplayName("round-trips arbitrary generated strings built from the tricky characters")
    void randomRoundTrips() {
        char[] alphabet = {'\\', 'n', 'r', '\n', '\r', 'a', '"', ' '};
        Random random = new Random(42);
        IntStream.range(0, 2_000).forEach(i -> {
            var value = new StringBuilder();
            IntStream.range(0, random.nextInt(12)).forEach(j -> value.append(alphabet[random.nextInt(alphabet.length)]));
            assertThat(LineBreakEscaping.unescape(LineBreakEscaping.escape(value.toString()))).isEqualTo(value.toString());
        });
    }
}
