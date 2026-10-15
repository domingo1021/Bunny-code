package com.bunnycode.project.application;

import com.bunnycode.shared.error.BunnyException;

final class ProjectErrors {

    private ProjectErrors() {
    }

    /** Also used for private projects of other users, so their existence is not revealed. */
    static BunnyException projectNotFound() {
        return BunnyException.notFound("project.not-found", "That project does not exist.");
    }

    static BunnyException versionNotFound() {
        return BunnyException.notFound("version.not-found", "That version does not exist.");
    }

    static BunnyException notOwner() {
        return BunnyException.forbidden("project.not-owner", "Only the project owner can do that.");
    }
}
