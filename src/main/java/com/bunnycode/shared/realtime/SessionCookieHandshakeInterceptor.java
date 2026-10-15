package com.bunnycode.shared.realtime;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.WebUtils;

/**
 * Browsers cannot set headers on a WebSocket handshake, but they do send cookies. This copies the
 * session cookie into the WebSocket session so the STOMP CONNECT frame can be authenticated
 * without the client handling the token itself.
 */
final class SessionCookieHandshakeInterceptor implements HandshakeInterceptor {

    static final String TOKEN_ATTRIBUTE = "bunny.sessionToken";

    private final String cookieName;

    SessionCookieHandshakeInterceptor(String cookieName) {
        this.cookieName = cookieName;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            Cookie cookie = WebUtils.getCookie(servletRequest.getServletRequest(), cookieName);
            if (cookie != null && !cookie.getValue().isBlank()) {
                attributes.put(TOKEN_ATTRIBUTE, cookie.getValue());
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // nothing to do
    }
}
