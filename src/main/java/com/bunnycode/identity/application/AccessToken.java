package com.bunnycode.identity.application;

import java.time.Instant;
import java.util.Objects;

public record AccessToken(String value, Instant expiresAt) {

    public AccessToken {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    @Override
    public String toString() {
        return "AccessToken[value=<redacted>, expiresAt=" + expiresAt + "]";
    }
}
