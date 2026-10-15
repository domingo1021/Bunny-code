package com.bunnycode.workspace.application;

/** What the editor should show. Clients render each state explicitly (never infer it from typing). */
public enum EditLockState {
    /** This session holds the lock and may edit. */
    EDITING,
    /** The owner is editing this version in another window; offer to take over. */
    LOCKED,
    /** Someone else's project. */
    READ_ONLY,
    /** The version has a recording and is replay-only. */
    RECORDED,
    /** Another window of the owner took over; this session is now read-only. */
    REVOKED,
    RELEASED
}
