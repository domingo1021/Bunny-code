package com.bunnycode.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("LikePatterns")
class LikePatternsTest {

    @ParameterizedTest(name = "blank input \"{0}\" matches everything")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void blankInputMatchesEverything(String input) {
        assertThat(LikePatterns.contains(input)).isEqualTo("%");
    }

    @Test
    @DisplayName("When the keyword contains wildcards, then they are escaped so they match literally")
    void escapesWildcards() {
        assertThat(LikePatterns.contains("50%_off\\")).isEqualTo("%50\\%\\_off\\\\%");
    }

    @Test
    @DisplayName("When the keyword has surrounding spaces, then they are trimmed")
    void trims() {
        assertThat(LikePatterns.contains("  bunny ")).isEqualTo("%bunny%");
    }
}
