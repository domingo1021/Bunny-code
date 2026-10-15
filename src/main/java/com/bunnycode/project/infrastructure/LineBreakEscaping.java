package com.bunnycode.project.infrastructure;

/**
 * InfluxDB returns query results as CSV and turns a stored {@code \n} into {@code \r\n}, so raw
 * line breaks do not survive a round trip. v2 points store code with {@code \}, CR and LF
 * escaped; this is the exact inverse, so any string — including one containing literal
 * backslash sequences — comes back unchanged.
 */
final class LineBreakEscaping {

    private LineBreakEscaping() {
    }

    static String escape(String value) {
        var escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    static String unescape(String value) {
        var plain = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 == value.length()) {
                plain.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n' -> plain.append('\n');
                case 'r' -> plain.append('\r');
                case '\\' -> plain.append('\\');
                default -> plain.append(c).append(next);
            }
        }
        return plain.toString();
    }
}
