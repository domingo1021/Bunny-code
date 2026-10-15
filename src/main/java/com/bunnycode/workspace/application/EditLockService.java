package com.bunnycode.workspace.application;

import com.bunnycode.project.ProjectAccess;
import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.security.AuthenticatedUser;
import org.springframework.stereotype.Service;

/**
 * Exclusive editing of a project version, per browser session.
 *
 * <p>Only the owner can edit (the check v1 got wrong), and a recorded version is replay-only.
 * Taking over notifies the previous session so it can switch to read-only and explain why,
 * instead of v1's forced socket disconnect.
 */
@Service
public class EditLockService {

    private final ProjectAccess projectAccess;
    private final EditLockStore store;
    private final EditLockNotifier notifier;
    private final WorkspaceProperties properties;

    EditLockService(ProjectAccess projectAccess, EditLockStore store, EditLockNotifier notifier,
                    WorkspaceProperties properties) {
        this.projectAccess = projectAccess;
        this.store = store;
        this.notifier = notifier;
        this.properties = properties;
    }

    public EditLockStatus acquire(AuthenticatedUser user, String sessionId, long versionId, boolean takeOver) {
        switch (projectAccess.editabilityOf(user.id(), versionId)) {
            case NOT_FOUND -> throw BunnyException.notFound("version.not-found", "That version does not exist.");
            case NOT_OWNER -> {
                return new EditLockStatus(versionId, EditLockState.READ_ONLY,
                        "You're viewing someone else's project, so it's read-only.");
            }
            case ALREADY_RECORDED -> {
                return new EditLockStatus(versionId, EditLockState.RECORDED,
                        "This version is recorded. Replay it, or create a new version to keep coding.");
            }
            case EDITABLE -> {
                // continue below
            }
        }
        if (takeOver) {
            store.takeOver(versionId, sessionId, properties.lockTimeToLive())
                    .filter(previous -> !previous.equals(sessionId))
                    .ifPresent(previous -> notifier.notifyRevoked(user.id(), previous, new EditLockStatus(versionId,
                            EditLockState.REVOKED, "You started editing this version in another window, so this one is now read-only.")));
            return editing(versionId);
        }
        if (store.tryAcquire(versionId, sessionId, properties.lockTimeToLive())) {
            return editing(versionId);
        }
        return new EditLockStatus(versionId, EditLockState.LOCKED,
                "You're already editing this version in another window. Take over editing here?");
    }

    public EditLockStatus release(String sessionId, long versionId) {
        store.release(versionId, sessionId);
        return new EditLockStatus(versionId, EditLockState.RELEASED, "You stopped editing.");
    }

    public void releaseAll(String sessionId) {
        store.releaseAll(sessionId);
    }

    private static EditLockStatus editing(long versionId) {
        return new EditLockStatus(versionId, EditLockState.EDITING, "You're editing this version.");
    }
}
