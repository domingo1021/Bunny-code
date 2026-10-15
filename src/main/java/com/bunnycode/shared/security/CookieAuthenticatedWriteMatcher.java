package com.bunnycode.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.WebUtils;

/**
 * CSRF only matters when the browser attaches credentials automatically, i.e. when a
 * state-changing request is authenticated by the session cookie. Requests that carry an explicit
 * {@code Authorization} header cannot be forged cross-site, so they are exempt.
 */
final class CookieAuthenticatedWriteMatcher implements RequestMatcher {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final String cookieName;

    CookieAuthenticatedWriteMatcher(String cookieName) {
        this.cookieName = cookieName;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return !SAFE_METHODS.contains(request.getMethod())
                && request.getHeader(HttpHeaders.AUTHORIZATION) == null
                && WebUtils.getCookie(request, cookieName) != null;
    }
}
