package com.bunnycode.shared.web;

import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.error.ErrorKind;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every failure to an RFC 9457 {@link ProblemDetail}. Each body carries a machine-readable
 * {@code code} and a user-facing {@code detail}; validation failures also carry {@code errors}.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String CODE = "code";

    @ExceptionHandler(BunnyException.class)
    ResponseEntity<ProblemDetail> handleBusinessFailure(BunnyException ex) {
        HttpStatus status = statusOf(ex.kind());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setProperty(CODE, ex.code());
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side. Please try again.");
        problem.setProperty(CODE, "internal");
        return ResponseEntity.internalServerError().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::toError)
                .toList();
        return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> Map.of(
                                "field", result.getMethodParameter().getParameterName(),
                                "message", String.valueOf(error.getDefaultMessage()))))
                .toList();
        return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
    }

    private static ProblemDetail validationProblem(List<Map<String, String>> errors) {
        String detail = errors.isEmpty() ? "The request is invalid." : errors.getFirst().get("message");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setProperty(CODE, "request.invalid");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static Map<String, String> toError(FieldError error) {
        return Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage()));
    }

    static HttpStatus statusOf(ErrorKind kind) {
        return switch (kind) {
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
