package org.danteplanner.backend.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Token-bucket sizes bound from the {@code rate-limit} property prefix.
 *
 * <p>Each field is one bucket family. Which family a request consumes from is decided by
 * {@code org.danteplanner.backend.shared.service.RateLimitPolicy}; this class only carries the
 * numbers, so a limit is retuned by editing {@code application.properties} alone.</p>
 */
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

    /**
     * One bucket's shape: it holds {@code capacity} tokens and regains {@code refillTokens} of
     * them every {@code refillDurationSeconds}.
     */
    @Getter
    @Setter
    public static class BucketConfig {
        private int capacity;
        private int refillTokens;
        private int refillDurationSeconds;
    }
}
