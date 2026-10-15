package com.bunnycode.battle.domain;

import java.util.Arrays;

/** Difficulty, stored as v1's numeric code (0 easy, 1 medium, 2 hard). */
public enum QuestionLevel {
    EASY(0),
    MEDIUM(1),
    HARD(2);

    private final int code;

    QuestionLevel(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static QuestionLevel fromCode(int code) {
        return Arrays.stream(values())
                .filter(level -> level.code == code)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown question level: " + code));
    }
}
