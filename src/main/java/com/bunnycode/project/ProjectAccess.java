package com.bunnycode.project;

/** Lets other modules ask who may edit a project version, without seeing project internals. */
public interface ProjectAccess {

    VersionEditability editabilityOf(long userId, long versionId);

    enum VersionEditability {
        /** The user owns the project and the version has not been recorded yet. */
        EDITABLE,
        /** Someone else's project: read-only. */
        NOT_OWNER,
        /** Recorded versions are replay-only. */
        ALREADY_RECORDED,
        NOT_FOUND
    }
}
