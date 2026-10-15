package com.bunnycode.project.application;

/**
 * v1 wrote events with hand-built line protocol, so a lone {@code "} was stored as {@code ""}
 * and {@code ""} as {@code """"}, and line breaks come back as {@code \r\n}; clients must undo
 * that for {@link #LEGACY} recordings. {@link #V2} events are returned exactly as recorded.
 */
public enum EventFormat {
    LEGACY,
    V2
}
