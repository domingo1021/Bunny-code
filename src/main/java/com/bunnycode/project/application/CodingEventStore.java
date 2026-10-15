package com.bunnycode.project.application;

import com.bunnycode.project.domain.CodingEvent;
import java.time.Instant;
import java.util.List;

/** Port to the time-series store holding recorded coding sessions. */
public interface CodingEventStore {

    /** Appends a session's events, preserving their order even when timestamps are equal. */
    void append(long projectId, long versionId, long fileId, List<CodingEvent> events);

    /** Events of one version within {@code [from, to)}, oldest first. */
    List<RecordedEvent> read(long projectId, long versionId, Instant from, Instant to);
}
