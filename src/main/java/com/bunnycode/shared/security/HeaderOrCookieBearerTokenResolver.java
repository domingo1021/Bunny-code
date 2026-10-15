package com.bunnycode.shared.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.web.util.WebUtils;

/**
 * Accepts the access token from {@code Authorization: Bearer …} (API clients, Next.js server
 * components) or, failing that, from the httpOnly session cookie set by the Next.js app for
 * browser calls. The header wins when both are present.
 */
final class HeaderOrCookieBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
    private final String cookieName;

    HeaderOrCookieBearerTokenResolver(String cookieName) {
        this.cookieName = cookieName;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String fromHeader = headerResolver.resolve(request);
        if (fromHeader != null) {
            return fromHeader;
        }
        Cookie cookie = WebUtils.getCookie(request, cookieName);
        return cookie == null || cookie.getValue().isBlank() ? null : cookie.getValue();
    }
}
