package com.bunnycode.workspace.application;

/** Port for telling one specific session that it lost the lock. */
public interface EditLockNotifier {

    void notifyRevoked(long userId, String sessionId, EditLockStatus status);
}
