package org.danteplanner.backend.shared.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "rate-limit")
@Getter
@Setter
public class RateLimitProperties {

    private BucketConfig crud;
    private BucketConfig importConfig;
    private BucketConfig sse;
    private BucketConfig auth;
    private BucketConfig comment;
    private BucketConfig report;
    private BucketConfig moderation;
    private BucketConfig publicRead;

    @Getter
    @Setter
    public static class BucketConfig {
        private int capacity;
        private int refillTokens;
        private int refillDurationSeconds;
    }
}
