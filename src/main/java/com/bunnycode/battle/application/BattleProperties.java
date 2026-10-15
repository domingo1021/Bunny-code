package com.bunnycode.battle.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param visibleTestCases test cases 1..n show their details; later ones are hidden
 * @param liveStateTimeToLive how long abandoned live state (ready flags, code) survives in Redis
 * @param judgeConcurrency    submissions judged at the same time; further ones wait their turn
 */
@Validated
@ConfigurationProperties("bunny.battle")
public record BattleProperties(
        @NotNull Duration invitationTimeToLive,
        @Positive int submissionChances,
        @Positive int visibleTestCases,
        @DefaultValue("24h") @NotNull Duration liveStateTimeToLive,
        @DefaultValue("16") @Positive int judgeConcurrency) {
}
