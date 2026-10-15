package com.bunnycode.project.application;

import java.time.Instant;

/** An event as read back from the event store, legacy or current. */
public record RecordedEvent(String action, int line, int index, String code, Instant timestamp, EventFormat format) {
}
