package com.bunnycode.execution.infrastructure;

import static java.util.stream.Collectors.joining;

/**
 * Keeps what a learner needs from a Node error (the code frame and the message) and drops stack
 * frames and runtime banners, as v1 did.
 */
final class StderrCleaner {

    private StderrCleaner() {
    }

    static String clean(String stderr) {
        return stderr.lines()
                .filter(line -> !line.stripLeading().startsWith("at "))
                .filter(line -> !line.startsWith("Node.js v"))
                .collect(joining("\n"))
                .strip();
    }
}
