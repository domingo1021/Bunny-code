package com.bunnycode.project.application;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A version with its current file and, once the version has been recorded, its recording.
 * A recorded version is replay-only.
 */
public record VersionView(long id, String name, int number, FileView file,
                          @JsonInclude(JsonInclude.Include.ALWAYS) RecordingSummaryView recording) {

    public record FileView(long id, String name, String url, String revision) {
    }
}
