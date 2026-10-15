package com.bunnycode.battle.infrastructure;

import com.bunnycode.battle.application.BattleEvent;
import com.bunnycode.battle.application.BattleNotifier;
import com.bunnycode.battle.application.BattleViews.BattleCreated;
import com.bunnycode.battle.application.BattleViews.InvitationView;
import com.bunnycode.shared.realtime.RealtimeError;
import com.bunnycode.shared.realtime.SessionMessages;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Destinations:
 * <ul>
 *   <li>{@value #INVITATIONS} — open invitations, for everyone online</li>
 *   <li>{@code /topic/battles/{id}} — {@link BattleEvent}s of one battle room</li>
 *   <li>{@code /user/queue/battles} — "your battle is ready" for the two players</li>
 * </ul>
 */
@Component
class StompBattleNotifier implements BattleNotifier {

    static final String INVITATIONS = "/topic/battle-invitations";
    static final String ROOM_PREFIX = "/topic/battles/";
    static final String PLAYER_QUEUE = "/queue/battles";
    static final String ERROR_QUEUE = "/queue/errors";

    private final SimpMessagingTemplate messaging;

    StompBattleNotifier(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @Override
    public void broadcastInvitation(InvitationView invitation) {
        messaging.convertAndSend(INVITATIONS, invitation);
    }

    @Override
    public void notifyBattleCreated(long userId, long battleId) {
        messaging.convertAndSendToUser(String.valueOf(userId), PLAYER_QUEUE, new BattleCreated(battleId));
    }

    @Override
    public void publish(long battleId, BattleEvent event) {
        messaging.convertAndSend(ROOM_PREFIX + battleId, event);
    }

    @Override
    public void notifyError(long userId, String sessionId, String code, String message) {
        messaging.convertAndSendToUser(String.valueOf(userId), ERROR_QUEUE, new RealtimeError(code, message),
                SessionMessages.forSession(sessionId));
    }
}
