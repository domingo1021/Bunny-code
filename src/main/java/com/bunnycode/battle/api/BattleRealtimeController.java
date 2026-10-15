package com.bunnycode.battle.api;

import com.bunnycode.battle.application.BattleService;
import com.bunnycode.battle.application.BattleViews.BattleSnapshot;
import com.bunnycode.battle.application.InvitationService;
import com.bunnycode.battle.domain.QuestionLevel;
import com.bunnycode.shared.security.AuthenticatedUser;
import com.bunnycode.shared.validation.ResourceName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * STOMP API of Battle Valley. Failures go to {@code /user/queue/errors}; everything else is
 * broadcast by {@code StompBattleNotifier}.
 *
 * <pre>
 * SEND      /app/battle-invitations                {battleName, level}
 * SEND      /app/battle-invitations/{id}/accept
 * SUBSCRIBE /app/battles/{id}                      one-off snapshot reply
 * SUBSCRIBE /topic/battles/{id}                    live BattleEvents
 * SEND      /app/battles/{id}/ready
 * SEND      /app/battles/{id}/code                 {code}
 * SEND      /app/battles/{id}/submissions          {code}
 * SEND      /app/battles/{id}/leave
 * </pre>
 * Disconnecting from a running battle counts as leaving it.
 */
@Controller
class BattleRealtimeController {

    static final String BATTLE_ATTRIBUTE = "bunny.battleId";

    private final InvitationService invitations;
    private final BattleService battles;

    BattleRealtimeController(InvitationService invitations, BattleService battles) {
        this.invitations = invitations;
        this.battles = battles;
    }

    @MessageMapping("/battle-invitations")
    void invite(@Valid @Payload InviteRequest request, Principal principal) {
        invitations.invite(AuthenticatedUser.from(principal), request.battleName(), request.level());
    }

    @MessageMapping("/battle-invitations/{invitationId}/accept")
    void accept(@DestinationVariable String invitationId, Principal principal) {
        invitations.accept(AuthenticatedUser.from(principal), invitationId);
    }

    @SubscribeMapping("/battles/{battleId}")
    BattleSnapshot enter(@DestinationVariable long battleId, Principal principal, SimpMessageHeaderAccessor headers) {
        BattleSnapshot snapshot = battles.snapshot(battleId, AuthenticatedUser.from(principal));
        Map<String, Object> session = headers.getSessionAttributes();
        if (session != null) {
            session.put(BATTLE_ATTRIBUTE, battleId);
        }
        return snapshot;
    }

    @MessageMapping("/battles/{battleId}/ready")
    void ready(@DestinationVariable long battleId, Principal principal) {
        battles.markReady(battleId, AuthenticatedUser.from(principal));
    }

    @MessageMapping("/battles/{battleId}/code")
    void code(@DestinationVariable long battleId, @Valid @Payload CodeRequest request, Principal principal) {
        battles.updateCode(battleId, AuthenticatedUser.from(principal), request.code());
    }

    @MessageMapping("/battles/{battleId}/submissions")
    void submit(@DestinationVariable long battleId, @Valid @Payload CodeRequest request, Principal principal,
                @Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId) {
        battles.submit(battleId, AuthenticatedUser.from(principal), sessionId, request.code());
    }

    @MessageMapping("/battles/{battleId}/leave")
    void leave(@DestinationVariable long battleId, Principal principal, SimpMessageHeaderAccessor headers) {
        battles.leave(battleId, AuthenticatedUser.from(principal));
        Map<String, Object> session = headers.getSessionAttributes();
        if (session != null) {
            session.remove(BATTLE_ATTRIBUTE);
        }
    }

    @EventListener
    void onDisconnect(SessionDisconnectEvent event) {
        Map<String, Object> session = SimpMessageHeaderAccessor.getSessionAttributes(event.getMessage().getHeaders());
        if (session != null && session.get(BATTLE_ATTRIBUTE) instanceof Long battleId && event.getUser() != null) {
            battles.leave(battleId, AuthenticatedUser.from(event.getUser()));
        }
    }

    record InviteRequest(@ResourceName String battleName, @NotNull QuestionLevel level) {
    }

    record CodeRequest(@NotNull @Size(max = 200_000) String code) {
    }
}
