package com.bunnycode.shared.realtime;

import com.bunnycode.shared.error.BunnyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * Replies to the failing session only. v1 emitted a bare {@code "Internal server error"} event
 * name; clients now always get a code and a readable message.
 */
@ControllerAdvice
class RealtimeExceptionHandler {

    static final String ERROR_QUEUE = "/queue/errors";

    private static final Logger log = LoggerFactory.getLogger(RealtimeExceptionHandler.class);

    @MessageExceptionHandler
    @SendToUser(destinations = ERROR_QUEUE, broadcast = false)
    RealtimeError handleBusinessFailure(BunnyException ex) {
        return new RealtimeError(ex.code(), ex.getMessage());
    }

    @MessageExceptionHandler
    @SendToUser(destinations = ERROR_QUEUE, broadcast = false)
    RealtimeError handleInvalidPayload(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult() == null ? "The message is invalid."
                : ex.getBindingResult().getFieldErrors().stream()
                        .findFirst()
                        .map(FieldError::getDefaultMessage)
                        .orElse("The message is invalid.");
        return new RealtimeError("request.invalid", message);
    }

    @MessageExceptionHandler
    @SendToUser(destinations = ERROR_QUEUE, broadcast = false)
    RealtimeError handleUnexpected(Exception ex) {
        log.error("Unhandled exception in message handler", ex);
        return new RealtimeError("internal", "Something went wrong on our side. Please try again.");
    }
}
