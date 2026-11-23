package org.danteplanner.backend.shared.ratelimit;

import org.springframework.validation.annotation.Validated;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@ConfigurationProperties(prefix = "rate-limit")
@Validated
public record RateLimitProperties(
        @Positive long configVersion,
        @NotNull BucketConfig crud,
        @NotNull BucketConfig sse,
        @NotNull BucketConfig auth,
        @NotNull BucketConfig comment,
        @NotNull BucketConfig report,
        @NotNull BucketConfig moderation,
        @NotNull BucketConfig publicRead,
        @NotNull BucketConfig plannerStats,
        @NotNull BucketConfig viewRecord) {

    public record BucketConfig(int capacity, int refillTokens, int refillDurationSeconds) {
    }
}
