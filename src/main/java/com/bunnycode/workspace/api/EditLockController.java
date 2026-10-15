package com.bunnycode.workspace.api;

import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.workspace.application.EditLockService;
import com.bunnycode.workspace.application.EditLockStatus;
import java.security.Principal;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * STOMP API of the workspace edit lock. Replies go to {@code /user/queue/edit-lock} of the
 * requesting session only.
 *
 * <ul>
 *   <li>{@code SEND /app/versions/{id}/edit-lock} {@code {"takeOver": false}} — acquire</li>
 *   <li>{@code SEND /app/versions/{id}/edit-lock} {@code {"takeOver": true}} — take over from another window</li>
 *   <li>{@code SEND /app/versions/{id}/edit-lock/release} — stop editing</li>
 * </ul>
 * Disconnecting releases everything the session held.
 */
@Controller
class EditLockController {

    private static final String QUEUE = "/queue/edit-lock";

    private final EditLockService editLocks;

    EditLockController(EditLockService editLocks) {
        this.editLocks = editLocks;
    }

    @MessageMapping("/versions/{versionId}/edit-lock")
    @SendToUser(destinations = QUEUE, broadcast = false)
    EditLockStatus acquire(@DestinationVariable long versionId, @Payload(required = false) AcquireRequest request,
                           Principal principal, @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId) {
        boolean takeOver = request != null && request.takeOver();
        return editLocks.acquire(AuthenticatedUser.from(principal), sessionId, versionId, takeOver);
    }

    @MessageMapping("/versions/{versionId}/edit-lock/release")
    @SendToUser(destinations = QUEUE, broadcast = false)
    EditLockStatus release(@DestinationVariable long versionId,
                           @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId) {
        return editLocks.release(sessionId, versionId);
    }

    @EventListener
    void onDisconnect(SessionDisconnectEvent event) {
        editLocks.releaseAll(event.getSessionId());
    }

    record AcquireRequest(boolean takeOver) {
    }
}
