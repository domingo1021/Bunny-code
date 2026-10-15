package com.bunnycode.shared.error;

import java.io.Serial;
import java.util.Objects;

/**
 * The single exception type for expected business failures.
 *
 * <p>The {@linkplain #getMessage() message} is written for end users and is returned to clients
 * verbatim; never put SQL, stack details or secrets in it. The {@link #code()} is a stable,
 * machine-readable identifier (e.g. {@code project.not-found}) that clients can branch on.
 *
 * <p>Instances are created through the static factories, one per {@link ErrorKind}.
 */
public final class BunnyException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorKind kind;
    private final String code;

    private BunnyException(ErrorKind kind, String code, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.kind = Objects.requireNonNull(kind, "kind");
        this.code = Objects.requireNonNull(code, "code");
    }

    public static BunnyException invalid(String code, String message) {
        return new BunnyException(ErrorKind.INVALID_INPUT, code, message);
    }

    public static BunnyException unauthenticated(String code, String message) {
        return new BunnyException(ErrorKind.UNAUTHENTICATED, code, message);
    }

    public static BunnyException forbidden(String code, String message) {
        return new BunnyException(ErrorKind.FORBIDDEN, code, message);
    }

    public static BunnyException notFound(String code, String message) {
        return new BunnyException(ErrorKind.NOT_FOUND, code, message);
    }

    public static BunnyException conflict(String code, String message) {
        return new BunnyException(ErrorKind.CONFLICT, code, message);
    }

    public static BunnyException tooManyRequests(String code, String message) {
        return new BunnyException(ErrorKind.TOO_MANY_REQUESTS, code, message);
    }

    public static BunnyException unavailable(String code, String message) {
        return new BunnyException(ErrorKind.UNAVAILABLE, code, message);
    }

    public ErrorKind kind() {
        return kind;
    }

    public String code() {
        return code;
    }
}
