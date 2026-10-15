package com.bunnycode.project.application;

import java.time.Instant;

public record RecordingSummaryView(long id, String baseFileUrl, Instant startedAt, Instant endedAt) {
}
