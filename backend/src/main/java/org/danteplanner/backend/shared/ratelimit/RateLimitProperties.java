package org.danteplanner.backend.shared.ratelimit;

import org.springframework.validation.annotation.Validated;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@ConfigurationProperties(prefix = "rate-limit")
@Validated
public record RateLimitProperties(
        @Positive long configVersion,
        @NotNull @Valid BucketConfig crud,
        @NotNull @Valid BucketConfig sse,
        @NotNull @Valid BucketConfig auth,
        @NotNull @Valid BucketConfig comment,
        @NotNull @Valid BucketConfig report,
        @NotNull @Valid BucketConfig moderation,
        @NotNull @Valid BucketConfig publicRead,
        @NotNull @Valid BucketConfig plannerStats,
        @NotNull @Valid BucketConfig viewRecord) {

    public record BucketConfig(
            @Positive int capacity,
            @Positive int refillTokens,
            @Positive int refillDurationSeconds) {
    }
}
