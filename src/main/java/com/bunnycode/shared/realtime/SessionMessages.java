package com.bunnycode.shared.realtime;

import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;

/**
 * Headers that narrow a {@code convertAndSendToUser} call to a single WebSocket session, so a
 * message reaches one browser tab rather than every tab of that user.
 */
public final class SessionMessages {

    private SessionMessages() {
    }

    public static MessageHeaders forSession(String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        return accessor.getMessageHeaders();
    }
}
