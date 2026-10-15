package com.bunnycode.battle.application;

import com.bunnycode.battle.application.BattleViews.InvitationView;

/** Port for pushing battle updates to connected clients. */
public interface BattleNotifier {

    void broadcastInvitation(InvitationView invitation);

    void notifyBattleCreated(long userId, long battleId);

    void publish(long battleId, BattleEvent event);

    /** For failures that happen after the request returned (asynchronous judging). */
    void notifyError(long userId, String sessionId, String code, String message);
}
