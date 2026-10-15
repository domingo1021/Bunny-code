package com.bunnycode.bdd;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.awaitility.Awaitility;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;

/**
 * A real STOMP-over-WebSocket client, as a browser tab would be. Every message is kept per
 * destination, so steps can wait for a message or look back at what already arrived.
 */
public final class StompTestSession implements AutoCloseable {

    static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final StompSession session;
    private final Map<String, List<JsonNode>> received = new ConcurrentHashMap<>();

    private StompTestSession(StompSession session) {
        this.session = session;
    }

    static StompTestSession connect(int port, String token) {
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        var connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        try {
            StompSession session = client.connectAsync("ws://localhost:" + port + "/api/v2/ws", new WebSocketHttpHeaders(),
                    connectHeaders, new StompSessionHandlerAdapter() { }).get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            return new StompTestSession(session);
        } catch (Exception e) {
            throw new IllegalStateException("STOMP connection refused", e);
        }
    }

    public void subscribe(String destination) {
        List<JsonNode> messages = received.computeIfAbsent(destination, key -> Collections.synchronizedList(new ArrayList<>()));
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return JsonNode.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((JsonNode) payload);
            }
        });
    }

    public void send(String destination, Object payload) {
        session.send(destination, payload == null ? Map.of() : payload);
    }

    /** Waits until a message matching {@code condition} has arrived on {@code destination}. */
    public JsonNode await(String destination, Predicate<JsonNode> condition) {
        return Awaitility.await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(50))
                .until(() -> messages(destination).stream().filter(condition).reduce((first, second) -> second).orElse(null),
                        message -> message != null);
    }

    /** All messages received on {@code destination} so far (a snapshot). */
    public List<JsonNode> messages(String destination) {
        List<JsonNode> messages = received.get(destination);
        if (messages == null) {
            throw new IllegalStateException("Not subscribed to " + destination);
        }
        synchronized (messages) {
            return List.copyOf(messages);
        }
    }

    @Override
    public void close() {
        if (session.isConnected()) {
            session.disconnect();
        }
    }
}
