package com.bunnycode.shared.realtime;

/**
 * Failure delivered to the sender on {@code /user/queue/errors}. Mirrors the REST problem detail:
 * a stable {@code code} plus a user-facing {@code message}.
 */
public record RealtimeError(String code, String message) {
}
