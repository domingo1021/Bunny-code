package com.bunnycode.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bunnycode.identity.domain.EmailAddress;
import com.bunnycode.identity.domain.UserAccount;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.shared.security.SecurityProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("TokenIssuer")
class TokenIssuerTest {

    private static final String SECRET = "unit-test-secret-that-is-long-enough-for-hs256";
    private final SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final SecurityProperties properties = new SecurityProperties(
            new SecurityProperties.Jwt(SECRET, "bunny-code", Duration.ofHours(12)), "bunny_session", List.of("http://localhost"));

    @Test
    @DisplayName("Given a user, when a token is issued, then it carries id, name, issuer and a real expiry")
    void issuesVerifiableToken() {
        Instant now = Instant.now();
        var issuer = new TokenIssuer(new NimbusJwtEncoder(new ImmutableSecret<>(key)), properties,
                Clock.fixed(now, ZoneOffset.UTC));

        AccessToken token = issuer.issueFor(user(42L, "bunny"));
        Jwt jwt = decoder().decode(token.value());

        assertThat(AuthenticatedUser.from(jwt)).isEqualTo(new AuthenticatedUser(42L, "bunny"));
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("bunny-code");
        assertThat(token.expiresAt()).isEqualTo(now.plus(Duration.ofHours(12)));
        assertThat(jwt.getExpiresAt()).isEqualTo(token.expiresAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("Given a token issued long ago, when decoded, then it is rejected as expired (v1 tokens never expired)")
    void expiredTokensAreRejected() {
        Instant longAgo = Instant.now().minus(Duration.ofDays(2));
        var issuer = new TokenIssuer(new NimbusJwtEncoder(new ImmutableSecret<>(key)), properties,
                Clock.fixed(longAgo, ZoneOffset.UTC));

        String token = issuer.issueFor(user(1L, "old")).value();

        assertThatThrownBy(() -> decoder().decode(token)).isInstanceOf(JwtException.class).hasMessageContaining("expired");
    }

    @Test
    @DisplayName("The token's toString never reveals its value")
    void redactsToString() {
        assertThat(new AccessToken("secret-token", Instant.EPOCH).toString()).doesNotContain("secret-token");
    }

    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("bunny-code"));
        return decoder;
    }

    static UserAccount user(long id, String name) {
        UserAccount user = UserAccount.register(name, EmailAddress.of(name + "@example.com"), "{bcrypt}hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
