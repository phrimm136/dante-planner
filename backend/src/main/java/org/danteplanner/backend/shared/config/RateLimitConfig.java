package org.danteplanner.backend.shared.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

import org.danteplanner.backend.shared.exception.RateLimitExceededException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "rate-limit")
@Getter
@Setter
public class RateLimitConfig {

    private final ProxyManager<byte[]> proxyManager;

    private BucketConfig crud;
    private BucketConfig importConfig;
    private BucketConfig sse;
    private BucketConfig auth;
    private BucketConfig comment;
    private BucketConfig report;
    private BucketConfig moderation;

    public RateLimitConfig(ProxyManager<byte[]> proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Getter
    @Setter
    public static class BucketConfig {
        private int capacity;
        private int refillTokens;
        private int refillDurationSeconds;
    }

    public void checkRateLimit(Long userId, String endpoint, BucketConfig config) {
        consumeOrThrow(userId + ":" + endpoint, config, () -> new RateLimitExceededException(userId, endpoint));
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
     * Check rate limit for auth endpoints using client identifier.
     *
     * @param identifier Client identifier (format: "ip:xxx" or "device:xxx")
     * @throws RateLimitExceededException if limit exceeded
     */
    public void checkAuthLimit(String identifier) {
        consumeOrThrow(identifier + ":auth", auth, () -> new RateLimitExceededException(null, "auth"));
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

    /**
     * Check rate limit for report operations (stricter: 3/hour).
     *
     * @param userId User ID for rate limiting
     * @throws RateLimitExceededException if limit exceeded
     */
    public void checkReportLimit(Long userId) {
        checkRateLimit(userId, "report", report);
    }

    /**
     * Check rate limit for moderation operations (ban, timeout, delete).
     * Stricter limit to prevent mass moderation abuse from compromised accounts.
     *
     * @param userId User ID (moderator/admin) for rate limiting
     * @throws RateLimitExceededException if limit exceeded
     */
    public void checkModerationLimit(Long userId) {
        checkRateLimit(userId, "moderation", moderation);
    }

    /**
     * Check rate limit for planner comment SSE connections (device-based, works for guests).
     * Uses same config as regular SSE.
     *
     * @param deviceId Device UUID for rate limiting
     * @throws RateLimitExceededException if limit exceeded
     */
    public void checkPlannerCommentSseLimit(UUID deviceId) {
        consumeOrThrow("device:" + deviceId + ":planner-comment-sse", sse,
                () -> new RateLimitExceededException(null, "planner-comment-sse"));
    }

    private void consumeOrThrow(String key, BucketConfig config, Supplier<RateLimitExceededException> onExceeded) {
        if (!tryConsume(key, config)) {
            throw onExceeded.get();
        }
    }

    private boolean tryConsume(String key, BucketConfig config) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        BucketConfiguration bucketConfiguration = buildConfiguration(config);
        return proxyManager.builder().build(keyBytes, () -> bucketConfiguration).tryConsume(1);
    }

    private BucketConfiguration buildConfiguration(BucketConfig config) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(config.getCapacity())
                .refillGreedy(config.getRefillTokens(), Duration.ofSeconds(config.getRefillDurationSeconds()))
                .build();
        return BucketConfiguration.builder().addLimit(limit).build();
    }
}
