package com.bunnycode.battle.application;

import com.bunnycode.battle.domain.QuestionLevel;
import java.time.Instant;
import java.util.Objects;

public record Invitation(String id, String battleName, QuestionLevel level, long inviterId, String inviterName,
                         Instant expiresAt) {

    public Invitation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(battleName, "battleName");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(inviterName, "inviterName");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
