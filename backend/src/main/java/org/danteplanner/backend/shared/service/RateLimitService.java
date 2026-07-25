package org.danteplanner.backend.shared.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Service;

import org.danteplanner.backend.shared.config.RateLimitProperties;
import org.danteplanner.backend.shared.config.RateLimitProperties.BucketConfig;
import org.danteplanner.backend.shared.exception.RateLimitExceededException;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;

/**
 * Charges one token against a {@link RateLimitPolicy}'s bucket, or refuses the request.
 *
 * <p>Buckets are held by a bucket4j {@link ProxyManager} over the per-region ephemeral
 * rate-limit Redis, keyed by the UTF-8 bytes of the policy's key. A refusal raises
 * {@link RateLimitExceededException}, which the global handler renders as 429.</p>
 */
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final ProxyManager<byte[]> proxyManager;
    private final RateLimitProperties properties;

    /**
     * Charges a user against a policy that names its own endpoint.
     *
     * @param policy the policy to charge
     * @param userId the user the bucket is keyed by
     * @throws RateLimitExceededException if the bucket is empty
     * @throws IllegalArgumentException   if the policy expects a caller-named endpoint
     */
    public void check(RateLimitPolicy policy, Long userId) {
        check(policy, userId, requireOwnEndpoint(policy));
    }

    /**
     * Charges a user against one route of a policy, giving each route its own bucket.
     *
     * @param policy   the policy to charge
     * @param userId   the user the bucket is keyed by
     * @param endpoint the route label distinguishing this bucket from the policy's others
     * @throws RateLimitExceededException if the bucket is empty
     */
    public void check(RateLimitPolicy policy, Long userId, String endpoint) {
        consumeOrThrow(policy, policy.subjectPrefix() + userId, endpoint, userId);
    }

    /**
     * Charges a client identifier against a policy, for endpoints reachable before a user exists.
     *
     * @param policy     the policy to charge
     * @param identifier the client identifier the bucket is keyed by ({@code ip:} / {@code device:})
     * @throws RateLimitExceededException if the bucket is empty
     * @throws IllegalArgumentException   if the policy expects a caller-named endpoint
     */
    public void check(RateLimitPolicy policy, String identifier) {
        consumeOrThrow(policy, policy.subjectPrefix() + identifier, requireOwnEndpoint(policy), null);
    }

    /**
     * Charges a device against a policy, for endpoints guests reach without a user id.
     *
     * @param policy   the policy to charge
     * @param deviceId the device the bucket is keyed by
     * @throws RateLimitExceededException if the bucket is empty
     * @throws IllegalArgumentException   if the policy expects a caller-named endpoint
     */
    public void check(RateLimitPolicy policy, UUID deviceId) {
        consumeOrThrow(policy, policy.subjectPrefix() + deviceId, requireOwnEndpoint(policy), null);
    }

    private static String requireOwnEndpoint(RateLimitPolicy policy) {
        String endpoint = policy.endpoint();
        if (endpoint == null) {
            throw new IllegalArgumentException(policy + " keys its buckets by a caller-named endpoint");
        }
        return endpoint;
    }

    private void consumeOrThrow(RateLimitPolicy policy, String subject, String endpoint, Long userId) {
        if (!tryConsume(subject + ":" + endpoint, policy.bucket(properties))) {
            throw new RateLimitExceededException(userId, endpoint);
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
