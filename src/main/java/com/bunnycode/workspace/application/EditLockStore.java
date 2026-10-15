package com.bunnycode.workspace.application;

import java.time.Duration;
import java.util.Optional;

/** Port to the shared store holding "which session edits which version". */
public interface EditLockStore {

    /** Acquires (or re-acquires) the lock unless another session holds it. */
    boolean tryAcquire(long versionId, String sessionId, Duration ttl);

    /** Takes the lock unconditionally and returns the previous holder, if any. */
    Optional<String> takeOver(long versionId, String sessionId, Duration ttl);

    /** Releases the lock only if {@code sessionId} holds it. */
    boolean release(long versionId, String sessionId);

    /** Releases every lock held by a session (it disconnected). */
    void releaseAll(String sessionId);
}
