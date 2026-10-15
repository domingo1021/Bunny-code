package com.bunnycode.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("CSRF applies only to cookie-authenticated writes")
class CookieAuthenticatedWriteMatcherTest {

    private final CookieAuthenticatedWriteMatcher matcher = new CookieAuthenticatedWriteMatcher("bunny_session");

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void cookieWritesNeedCsrf(String method) {
        assertThat(matcher.matches(request(method, true, false))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    void safeMethodsDoNot(String method) {
        assertThat(matcher.matches(request(method, true, false))).isFalse();
    }

    @Test
    @DisplayName("Requests with an explicit Authorization header cannot be forged cross-site")
    void bearerRequestsDoNot() {
        assertThat(matcher.matches(request("POST", true, true))).isFalse();
        assertThat(matcher.matches(request("POST", false, true))).isFalse();
    }

    @Test
    @DisplayName("Anonymous writes (no cookie) have nothing to protect")
    void anonymousDoNot() {
        assertThat(matcher.matches(request("POST", false, false))).isFalse();
    }

    private static MockHttpServletRequest request(String method, boolean cookie, boolean header) {
        var request = new MockHttpServletRequest(method, "/api/v2/projects");
        if (cookie) {
            request.setCookies(new Cookie("bunny_session", "token"));
        }
        if (header) {
            request.addHeader("Authorization", "Bearer token");
        }
        return request;
    }
}
