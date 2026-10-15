package com.bunnycode.workspace.application;

import java.util.Objects;

public record EditLockStatus(long versionId, EditLockState state, String message) {

    public EditLockStatus {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(message, "message");
    }
}
