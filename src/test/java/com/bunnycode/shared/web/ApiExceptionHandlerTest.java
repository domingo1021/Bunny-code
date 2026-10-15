package com.bunnycode.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bunnycode.shared.error.BunnyException;
import com.bunnycode.shared.error.ErrorKind;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

@DisplayName("ApiExceptionHandler")
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    static Stream<Arguments> factories() {
        return Stream.of(
                Arguments.of((BiFunction<String, String, BunnyException>) BunnyException::invalid, HttpStatus.BAD_REQUEST),
                Arguments.of((BiFunction<String, String, BunnyException>) BunnyException::unauthenticated, HttpStatus.UNAUTHORIZED),
                Arguments.of((BiFunction<String, String, BunnyException>) BunnyException::forbidden, HttpStatus.FORBIDDEN),
                Arguments.of((BiFunction<String, String, BunnyException>) BunnyException::notFound, HttpStatus.NOT_FOUND),
                Arguments.of((BiFunction<String, String, BunnyException>) BunnyException::conflict, HttpStatus.CONFLICT),
                Arguments.of((BiFunction<String, String, BunnyException>) BunnyException::tooManyRequests, HttpStatus.TOO_MANY_REQUESTS),
                Arguments.of((BiFunction<String, String, BunnyException>) BunnyException::unavailable, HttpStatus.SERVICE_UNAVAILABLE));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("factories")
    @DisplayName("Given a business failure, when handled, then the problem detail carries status, code and the user-facing message")
    void mapsBusinessFailures(BiFunction<String, String, BunnyException> factory, HttpStatus expected) {
        var response = handler.handleBusinessFailure(factory.apply("some.code", "Readable message"));

        assertThat(response.getStatusCode()).isEqualTo(expected);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getDetail()).isEqualTo("Readable message");
        assertThat(body.getProperties()).containsEntry("code", "some.code");
    }

    @ParameterizedTest
    @EnumSource(ErrorKind.class)
    @DisplayName("Every error kind maps to a 4xx/5xx status")
    void everyKindHasAStatus(ErrorKind kind) {
        assertThat(ApiExceptionHandler.statusOf(kind).isError()).isTrue();
    }

    @Test
    @DisplayName("Given an unexpected exception, when handled, then internals are not leaked to the client")
    void hidesUnexpectedFailures() {
        var response = handler.handleUnexpected(new IllegalStateException("SELECT * FROM secret_table"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getDetail()).doesNotContain("secret_table");
        assertThat(response.getBody().getProperties()).containsEntry("code", "internal");
    }
}
