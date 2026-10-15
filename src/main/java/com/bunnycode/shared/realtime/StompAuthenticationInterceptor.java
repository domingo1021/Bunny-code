package com.bunnycode.shared.realtime;

import java.util.Map;
import java.util.Set;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP {@code CONNECT} frame once per connection — from its
 * {@code Authorization: Bearer …} native header, or from the session cookie captured during the
 * handshake — and rejects any later frame from an unauthenticated session.
 */
@Component
class StompAuthenticationInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<StompCommand> REQUIRE_USER = Set.of(StompCommand.SEND, StompCommand.SUBSCRIBE);

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();

    StompAuthenticationInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.CONNECT) {
            String token = tokenOf(accessor);
            if (token == null) {
                throw new MessageDeliveryException("Missing access token");
            }
            try {
                accessor.setUser(authenticationConverter.convert(jwtDecoder.decode(token)));
            } catch (JwtException e) {
                throw new MessageDeliveryException("Invalid access token");
            }
        } else if (REQUIRE_USER.contains(accessor.getCommand()) && accessor.getUser() == null) {
            throw new MessageDeliveryException("Not authenticated");
        }
        return message;
    }

    private static String tokenOf(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX) && header.length() > BEARER_PREFIX.length()) {
            return header.substring(BEARER_PREFIX.length());
        }
        Map<String, Object> attributes = accessor.getSessionAttributes();
        return attributes == null ? null : (String) attributes.get(SessionCookieHandshakeInterceptor.TOKEN_ATTRIBUTE);
    }
}
