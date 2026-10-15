package com.bunnycode.identity.application;

import com.bunnycode.identity.domain.UserAccount;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.shared.security.SecurityProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
class TokenIssuer {

    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;

    TokenIssuer(JwtEncoder encoder, SecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    AccessToken issueFor(UserAccount user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.jwt().timeToLive());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim(AuthenticatedUser.NAME_CLAIM, user.getName())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, expiresAt);
    }
}
