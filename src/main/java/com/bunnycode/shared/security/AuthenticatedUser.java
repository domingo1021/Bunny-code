package com.bunnycode.shared.security;

import com.bunnycode.shared.error.BunnyException;
import java.security.Principal;
import java.util.Objects;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The caller, as asserted by a verified access token. The token subject is the user id, which
 * is also the {@link Principal#getName() principal name} used for STOMP user destinations.
 */
public record AuthenticatedUser(long id, String name) {

    public static final String NAME_CLAIM = "name";

    public AuthenticatedUser {
        Objects.requireNonNull(name, "name");
    }

    public static AuthenticatedUser from(Jwt jwt) {
        return new AuthenticatedUser(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString(NAME_CLAIM));
    }

    public static AuthenticatedUser from(Principal principal) {
        if (principal instanceof JwtAuthenticationToken token) {
            return from(token.getToken());
        }
        throw BunnyException.unauthenticated("auth.required", "Please sign in to continue.");
    }
}
