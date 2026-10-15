package com.bunnycode.project.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One editor action in a recorded coding session (a keystroke, a cursor move, a line copy…).
 * The client owns the action vocabulary; the server stores and returns it unchanged.
 */
public record CodingEvent(String action, int line, int index, String code, Instant timestamp) {

    public static final int MAX_CODE_LENGTH = 10_000;
    /** Words and hyphens (e.g. {@code create}, {@code line-start}); also safe as an InfluxDB tag. */
    public static final String ACTION_REGEX = "^[A-Za-z][A-Za-z-]{0,29}$";
    private static final Pattern ACTION = Pattern.compile(ACTION_REGEX);

    public CodingEvent {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(timestamp, "timestamp");
        if (!ACTION.matcher(action).matches()) {
            throw new IllegalArgumentException("Invalid action: " + action);
        }
        if (line < 0 || index < 0) {
            throw new IllegalArgumentException("line and index must not be negative");
        }
        if (code.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("code is longer than " + MAX_CODE_LENGTH);
        }
    }
}
