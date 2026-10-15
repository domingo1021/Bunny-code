package com.bunnycode.project.application;

import java.time.Instant;
import java.util.List;

/**
 * Everything a client needs to replay a session: the starting content and the ordered events.
 * {@code format} tells the client whether events use the v1 encoding (see {@link EventFormat}).
 */
public record RecordingView(long id, String baseFileUrl, Instant startedAt, Instant endedAt,
                            EventFormat format, List<RecordedEventView> events) {

    public RecordingView {
        events = List.copyOf(events);
    }

    /** {@code timestamp} is epoch milliseconds. */
    public record RecordedEventView(String action, int line, int index, String code, long timestamp) {
    }
}
