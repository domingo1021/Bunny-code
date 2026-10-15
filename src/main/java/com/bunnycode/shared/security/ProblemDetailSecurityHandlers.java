package com.bunnycode.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

/** Writes 401/403 as RFC 9457 problem details so clients see one error shape everywhere. */
final class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        if (exception instanceof InvalidBearerTokenException) {
            write(response, HttpStatus.UNAUTHORIZED, "auth.invalid-token",
                    "Your session has expired or is invalid. Please sign in again.");
        } else {
            write(response, HttpStatus.UNAUTHORIZED, "auth.required", "Please sign in to continue.");
        }
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        if (exception instanceof CsrfException) {
            write(response, HttpStatus.FORBIDDEN, "auth.csrf", "The request is missing a valid CSRF token.");
        } else {
            write(response, HttpStatus.FORBIDDEN, "auth.forbidden", "You are not allowed to do that.");
        }
    }

    private static void write(HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        // Fixed strings only — no user input — so hand-written JSON is safe here.
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s","code":"%s"}"""
                .formatted(status.getReasonPhrase(), status.value(), detail, code));
    }
}
