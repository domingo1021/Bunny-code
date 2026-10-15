package com.bunnycode.workspace.application;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** @param lockTimeToLive safety net: a lock left behind by a crashed node expires on its own */
@Validated
@ConfigurationProperties("bunny.workspace")
public record WorkspaceProperties(@NotNull Duration lockTimeToLive) {
}
