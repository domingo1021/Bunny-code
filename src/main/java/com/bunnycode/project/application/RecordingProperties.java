package com.bunnycode.project.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("bunny.recording")
public record RecordingProperties(@Valid @NotNull Influx influx, @Positive int maxEventsPerSession) {

    public record Influx(@NotBlank String url, @NotBlank String token, @NotBlank String org, @NotBlank String bucket) {

        @Override
        public String toString() {
            return "Influx[url=" + url + ", token=<redacted>, org=" + org + ", bucket=" + bucket + "]";
        }
    }
}
