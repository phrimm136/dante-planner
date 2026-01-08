package org.danteplanner.backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.Getter;
import lombok.Setter;
import org.danteplanner.backend.exception.RateLimitExceededException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@ConfigurationProperties(prefix = "rate-limit")
@Getter
@Setter
public class RateLimitConfig {

    private BucketConfig crud;
    private BucketConfig importConfig;
    private BucketConfig sse;
    private BucketConfig auth;
    private BucketConfig comment;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Getter
    @Setter
    public static class BucketConfig {
        private int capacity;
        private int refillTokens;
        private int refillDurationSeconds;
    }

    public void checkRateLimit(Long userId, String endpoint, BucketConfig config) {
        String key = userId + ":" + endpoint;
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(config));

        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException(userId, endpoint);
        }
    }

    public void checkCrudLimit(Long userId, String endpoint) {
        checkRateLimit(userId, endpoint, crud);
    }

    public void checkImportLimit(Long userId) {
        checkRateLimit(userId, "import", importConfig);
    }

    public void checkSseLimit(Long userId) {
        checkRateLimit(userId, "sse", sse);
    }

    /**
     * Check rate limit for auth endpoints using client IP.
     *
     * @param clientIp Client IP address for rate limiting
     * @throws RateLimitExceededException if limit exceeded
     */
    public void checkAuthLimit(String clientIp) {
        String key = "auth:" + clientIp;
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(auth));

        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException(null, "auth");
        }
    }

    /**
     * Check rate limit for comment operations (create, edit, vote).
     *
     * @param userId User ID for rate limiting
     * @throws RateLimitExceededException if limit exceeded
     */
    public void checkCommentLimit(Long userId) {
        checkRateLimit(userId, "comment", comment);
    }

    private Bucket createBucket(BucketConfig config) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(config.getCapacity())
                .refillGreedy(config.getRefillTokens(), Duration.ofSeconds(config.getRefillDurationSeconds()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
