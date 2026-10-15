package com.bunnycode.shared.security;

import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** Brings the real JWT and security setup into {@code @WebMvcTest} slices, plus a token minter. */
@TestConfiguration(proxyBeanMethods = false)
@Import(JwtConfiguration.class)
@EnableConfigurationProperties(SecurityProperties.class)
public class WebSecurityTestConfiguration {

    @Bean
    TestTokens testTokens(JwtEncoder encoder, SecurityProperties properties) {
        return new TestTokens(encoder, properties.jwt().issuer());
    }

    public record TestTokens(JwtEncoder encoder, String issuer) {

        public String forUser(long id, String name) {
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(issuer)
                    .subject(String.valueOf(id))
                    .issuedAt(now)
                    .expiresAt(now.plus(Duration.ofHours(1)))
                    .claim(AuthenticatedUser.NAME_CLAIM, name)
                    .build();
            return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                    .getTokenValue();
        }

        public String bearer(long id, String name) {
            return "Bearer " + forUser(id, name);
        }
    }
}
