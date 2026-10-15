package com.bunnycode.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("HeaderOrCookieBearerTokenResolver")
class HeaderOrCookieBearerTokenResolverTest {

    private final HeaderOrCookieBearerTokenResolver resolver = new HeaderOrCookieBearerTokenResolver("bunny_session");

    @Test
    @DisplayName("Given both a header and a cookie, then the Authorization header wins")
    void headerWins() {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer from-header");
        request.setCookies(new Cookie("bunny_session", "from-cookie"));

        assertThat(resolver.resolve(request)).isEqualTo("from-header");
    }

    @Test
    @DisplayName("Given only the session cookie, then its value is the token")
    void cookieFallback() {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("bunny_session", "from-cookie"));

        assertThat(resolver.resolve(request)).isEqualTo("from-cookie");
    }

    @Test
    @DisplayName("Given neither, or a blank cookie, then there is no token")
    void none() {
        var blank = new MockHttpServletRequest();
        blank.setCookies(new Cookie("bunny_session", " "));

        assertThat(resolver.resolve(new MockHttpServletRequest())).isNull();
        assertThat(resolver.resolve(blank)).isNull();
    }
}
