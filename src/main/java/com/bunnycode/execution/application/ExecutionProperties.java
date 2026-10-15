package com.bunnycode.execution.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Sandbox limits and placement. Every value that ends up on a {@code docker}/{@code ssh} command
 * line is constrained by a pattern here, so configuration cannot smuggle in extra arguments.
 */
@Validated
@ConfigurationProperties("bunny.execution")
public record ExecutionProperties(
        @NotNull Mode mode,
        @NotBlank @Pattern(regexp = "^[a-z0-9][a-z0-9._/:-]{0,127}$") String image,
        @NotBlank @Pattern(regexp = "^\\d+(\\.\\d+)?$") String cpus,
        @NotBlank @Pattern(regexp = "^\\d+[kmg]$") String memory,
        @Positive int pidsLimit,
        @NotNull Duration timeout,
        @Positive int maxOutputBytes,
        @Positive int runsPerMinute,
        @Valid @NotNull Ssh ssh,
        @Valid @NotNull HostHealth hostHealth) {

    @AssertTrue(message = "bunny.execution.ssh.identity-file is required when mode is SSH")
    public boolean isSshIdentityConfigured() {
        return mode != Mode.SSH || ssh != null && ssh.identityFile() != null && !ssh.identityFile().isBlank();
    }

    public enum Mode {
        /** Docker on this machine. For development and tests only — never run user code on the API host. */
        LOCAL,
        /** Docker on remote sandbox hosts listed in the {@code host} table, reached over SSH. */
        SSH
    }

    public record Ssh(@NotBlank @Pattern(regexp = "^[a-z_][a-z0-9_-]{0,31}$") String user,
                      @Pattern(regexp = "^[\\w./-]*$") String identityFile) {
    }

    public record HostHealth(@Positive int metricsPort,
                             @NotNull Duration probeTimeout,
                             @DecimalMin("0.0") @DecimalMax("1.0") double cpuThreshold,
                             @DecimalMin("0.0") @DecimalMax("1.0") double memoryThreshold) {
    }
}
