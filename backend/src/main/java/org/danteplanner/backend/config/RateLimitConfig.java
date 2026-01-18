package org.danteplanner.backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.exception.RateLimitExceededException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@ConfigurationProperties(prefix = "rate-limit")
@Getter
@Setter
@Slf4j
public class RateLimitConfig {

    private BucketConfig crud;
    private BucketConfig importConfig;
    private BucketConfig sse;
    private BucketConfig auth;
    private BucketConfig comment;
    private BucketConfig report;

    /**
     * TTL for bucket entries in seconds (default: 1 hour).
     * Buckets not accessed within this time are evicted.
     */
    private int bucketTtlSeconds = 3600;

    /**
     * Maximum number of buckets to keep in memory (default: 10000).
     * Prevents memory exhaustion from unique device IDs.
     */
    private int maxBuckets = 10000;

    private final ConcurrentHashMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    /**
     * Wrapper for Bucket with last access timestamp for TTL eviction.
     */
    private static class BucketEntry {
        final Bucket bucket;
        volatile Instant lastAccess;

        BucketEntry(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccess = Instant.now();
        }

        void touch() {
            this.lastAccess = Instant.now();
        }

        boolean isExpired(int ttlSeconds) {
            return Instant.now().isAfter(lastAccess.plusSeconds(ttlSeconds));
        }
    }

    @Getter
    @Setter
    public static class BucketConfig {
        private int capacity;
        private int refillTokens;
        private int refillDurationSeconds;
    }

    public void checkRateLimit(Long userId, String endpoint, BucketConfig config) {
        String key = userId + ":" + endpoint;
        BucketEntry entry = getOrCreateBucket(key, config);

        if (!entry.bucket.tryConsume(1)) {
            throw new RateLimitExceededException(userId, endpoint);
        }
    }

    private BucketEntry getOrCreateBucket(String key, BucketConfig config) {
        BucketEntry entry = buckets.computeIfAbsent(key, k -> new BucketEntry(createBucket(config)));
        entry.touch();
        return entry;
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
        String key = identifier + ":auth";
        BucketEntry entry = getOrCreateBucket(key, auth);

        if (!entry.bucket.tryConsume(1)) {
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
     * Check rate limit for planner comment SSE connections (device-based, works for guests).
     * Uses same config as regular SSE.
     *
     * @param deviceId Device UUID for rate limiting
     * @throws RateLimitExceededException if limit exceeded
     */
    public void checkPlannerCommentSseLimit(UUID deviceId) {
        String key = "device:" + deviceId + ":planner-comment-sse";
        BucketEntry entry = getOrCreateBucket(key, sse);
        if (!entry.bucket.tryConsume(1)) {
            throw new RateLimitExceededException(null, "planner-comment-sse");
        }
    }

    private Bucket createBucket(BucketConfig config) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(config.getCapacity())
                .refillGreedy(config.getRefillTokens(), Duration.ofSeconds(config.getRefillDurationSeconds()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Evicts expired bucket entries every 5 minutes.
     * Prevents memory leak from accumulating unique device IDs.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void evictExpiredBuckets() {
        int evicted = 0;
        Iterator<Map.Entry<String, BucketEntry>> iterator = buckets.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, BucketEntry> entry = iterator.next();
            if (entry.getValue().isExpired(bucketTtlSeconds)) {
                iterator.remove();
                evicted++;
            }
        }

        // Also enforce max bucket limit (evict oldest if over limit)
        if (buckets.size() > maxBuckets) {
            int toRemove = buckets.size() - maxBuckets;
            buckets.entrySet().stream()
                    .sorted((a, b) -> a.getValue().lastAccess.compareTo(b.getValue().lastAccess))
                    .limit(toRemove)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(buckets::remove);
            evicted += toRemove;
        }

        if (evicted > 0) {
            log.info("Rate limit bucket cleanup: evicted {} entries, {} remaining", evicted, buckets.size());
        }
    }

    /**
     * Returns current bucket count for monitoring.
     */
    public int getBucketCount() {
        return buckets.size();
    }
}
