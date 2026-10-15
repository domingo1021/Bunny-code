package com.bunnycode.shared.web;

/**
 * Builds SQL {@code LIKE} patterns from user input. Escapes the wildcard characters so a search
 * for {@code "50%"} matches literally instead of matching everything. Queries using these
 * patterns must declare {@code escape '\'}.
 */
public final class LikePatterns {

    private LikePatterns() {
    }

    /** Returns {@code %input%} with {@code \}, {@code %} and {@code _} escaped; {@code %} for blank input. */
    public static String contains(String input) {
        if (input == null || input.isBlank()) {
            return "%";
        }
        String escaped = input.strip()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
