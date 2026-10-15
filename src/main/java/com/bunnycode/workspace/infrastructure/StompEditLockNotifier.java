package com.bunnycode.workspace.infrastructure;

import com.bunnycode.shared.realtime.SessionMessages;
import com.bunnycode.workspace.application.EditLockNotifier;
import com.bunnycode.workspace.application.EditLockStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
class StompEditLockNotifier implements EditLockNotifier {

    static final String QUEUE = "/queue/edit-lock";

    private final SimpMessagingTemplate messaging;

    StompEditLockNotifier(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    /** Targets one session: the owner's other tabs must not be told they lost the lock. */
    @Override
    public void notifyRevoked(long userId, String sessionId, EditLockStatus status) {
        messaging.convertAndSendToUser(String.valueOf(userId), QUEUE, status, SessionMessages.forSession(sessionId));
    }
}
