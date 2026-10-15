package com.bunnycode.identity.infrastructure;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * New hashes are {@code {bcrypt}}-prefixed with cost 12. v1 stored un-prefixed {@code $2b$05$}
 * hashes; those still verify (bcrypt is the fallback for un-prefixed values) and
 * {@link PasswordEncoder#upgradeEncoding(String)} reports them as outdated, so they are re-hashed
 * on the user's next successful sign-in.
 */
@Configuration(proxyBeanMethods = false)
class PasswordEncoderConfiguration {

    static final int BCRYPT_STRENGTH = 12;

    @Bean
    PasswordEncoder passwordEncoder() {
        var bcrypt = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
        var encoder = new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", bcrypt));
        encoder.setDefaultPasswordEncoderForMatches(bcrypt);
        return encoder;
    }
}
