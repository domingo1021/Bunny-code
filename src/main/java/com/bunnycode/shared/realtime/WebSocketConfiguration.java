package com.bunnycode.shared.realtime;

import com.bunnycode.shared.security.SecurityProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over a native WebSocket at {@value #ENDPOINT}.
 *
 * <ul>
 *   <li>{@code /app/**} — client commands, handled by {@code @MessageMapping} methods;</li>
 *   <li>{@code /topic/**} — broadcasts (battle rooms, invitations);</li>
 *   <li>{@code /user/queue/**} — replies to one user or one session.</li>
 * </ul>
 *
 * <p>The in-memory simple broker limits the API to one instance. Scaling out means switching to
 * a broker relay (e.g. RabbitMQ STOMP) — a configuration change here, not an application change.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {

    static final String ENDPOINT = "/api/v2/ws";

    private final SecurityProperties securityProperties;
    private final StompAuthenticationInterceptor authenticationInterceptor;

    WebSocketConfiguration(SecurityProperties securityProperties,
                           StompAuthenticationInterceptor authenticationInterceptor) {
        this.securityProperties = securityProperties;
        this.authenticationInterceptor = authenticationInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Keep each session's inbound frames in order (e.g. code updates) while handlers run concurrently.
        registry.setPreserveReceiveOrder(true);
        registry.addEndpoint(ENDPOINT)
                // Origin check is the defence against cross-site WebSocket hijacking with the cookie.
                .setAllowedOriginPatterns(securityProperties.allowedOrigins().toArray(String[]::new))
                .addInterceptors(new SessionCookieHandshakeInterceptor(securityProperties.sessionCookieName()));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authenticationInterceptor);
    }
}
