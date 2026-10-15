package com.bunnycode.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

/**
 * Spring Security loads CSRF tokens lazily, so the {@code XSRF-TOKEN} cookie would only appear
 * after the first protected request failed. For browser sessions this forces it out early so the
 * client can echo it in {@code X-XSRF-TOKEN}. API clients using bearer headers never get one.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    private final String sessionCookieName;

    CsrfCookieFilter(String sessionCookieName) {
        this.sessionCookieName = sessionCookieName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (WebUtils.getCookie(request, sessionCookieName) != null
                && request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
        chain.doFilter(request, response);
    }
}
