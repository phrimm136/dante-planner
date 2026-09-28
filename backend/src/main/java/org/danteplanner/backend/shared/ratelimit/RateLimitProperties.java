package org.danteplanner.backend.shared.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "rate-limit")
@Validated
@Getter
@Setter
public class RateLimitProperties {

    @Positive
    private long configVersion;
    private BucketConfig crud;
    private BucketConfig sse;
    private BucketConfig auth;
    private BucketConfig comment;
    private BucketConfig report;
    private BucketConfig moderation;
    private BucketConfig publicRead;
    private BucketConfig plannerStats;
    private BucketConfig viewRecord;

    @Getter
    @Setter
    public static class BucketConfig {
        private int capacity;
        private int refillTokens;
        private int refillDurationSeconds;
    }
}
