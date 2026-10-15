package com.bunnycode.shared.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Security settings. Validated at startup: a missing or short JWT secret stops the application
 * instead of silently signing tokens with an empty key.
 */
@Validated
@ConfigurationProperties("bunny.security")
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @NotBlank String sessionCookieName,
        @NotEmpty List<@NotBlank String> allowedOrigins) {

    public SecurityProperties {
        allowedOrigins = List.copyOf(allowedOrigins);
    }

    public record Jwt(
            @NotBlank @Size(min = 32, message = "must be at least 32 characters (HS256 needs a 256-bit key)") String secret,
            @NotBlank String issuer,
            @NotNull Duration timeToLive) {

        @Override
        public String toString() {
            return "Jwt[secret=<redacted>, issuer=" + issuer + ", timeToLive=" + timeToLive + "]";
        }
    }
}
