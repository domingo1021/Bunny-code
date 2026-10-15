package com.bunnycode.battle.application;

import java.time.Duration;
import java.util.Optional;

/** Port to the store of short-lived battle invitations. */
public interface InvitationStore {

    /** Saves an invitation unless its inviter already has one pending or its name is being used. */
    SaveOutcome save(Invitation invitation, Duration ttl);

    Optional<Invitation> peek(String invitationId);

    /** Atomically reads and deletes, so at most one user can accept an invitation. */
    Optional<Invitation> claim(String invitationId);

    enum SaveOutcome {
        SAVED,
        INVITER_BUSY,
        NAME_PENDING
    }
}
