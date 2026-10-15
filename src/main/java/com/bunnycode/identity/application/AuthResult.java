package com.bunnycode.identity.application;

import java.time.Instant;

public record AuthResult(String accessToken, String tokenType, Instant expiresAt, UserProfileView user) {

    static AuthResult of(AccessToken token, UserProfileView user) {
        return new AuthResult(token.value(), "Bearer", token.expiresAt(), user);
    }

    @Override
    public String toString() {
        return "AuthResult[accessToken=<redacted>, expiresAt=" + expiresAt + ", user=" + user + "]";
    }
}
