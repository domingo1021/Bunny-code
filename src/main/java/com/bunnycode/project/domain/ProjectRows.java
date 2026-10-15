package com.bunnycode.project.domain;

import java.time.Instant;

/**
 * Read-model projections filled directly by JPQL constructor expressions. Lists and detail pages
 * read these instead of entities: no dirty checking, no lazy loading, only the needed columns.
 */
public final class ProjectRows {

    private ProjectRows() {
    }

    public record ProjectSummaryRow(Long id, String name, String description, Visibility visibility,
                                    long watchCount, long starCount, Instant createdAt, Long ownerId,
                                    String latestVersionName) {
    }

    /** A version joined with its current file; file columns are null when the version has none. */
    public record VersionFileRow(Long versionId, String versionName, int versionNumber,
                                 Long fileId, String fileName, String fileKey, String fileRevision) {
    }

    public record RecordingRow(Long id, Long versionId, String baseFileKey, Instant startedAt, Instant endedAt) {
    }

    public record VersionAccessRow(Long versionId, Long ownerId, boolean recorded) {
    }
}
