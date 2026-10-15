package com.bunnycode.shared.error;

/**
 * Transport-agnostic category of a business failure. The web and realtime layers map each kind
 * to their own representation (HTTP status, STOMP error frame), so domain code never needs to
 * know about HTTP.
 */
public enum ErrorKind {
    INVALID_INPUT,
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    TOO_MANY_REQUESTS,
    UNAVAILABLE
}
