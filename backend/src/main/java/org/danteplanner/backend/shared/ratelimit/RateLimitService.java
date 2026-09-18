package org.danteplanner.backend.shared.ratelimit;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import org.danteplanner.backend.shared.ratelimit.RateLimitProperties.BucketConfig;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final ProxyManager<byte[]> proxyManager;
    private final RateLimitProperties properties;

    public void check(RateLimitPolicy policy, Long userId) {
        check(policy, userId, requireOwnEndpoint(policy));
    }

    public void check(RateLimitPolicy policy, Long userId, String endpoint) {
        consumeOrThrow(policy, policy.subjectPrefix() + userId, endpoint, userId);
    }

    public void check(RateLimitPolicy policy, String identifier) {
        consumeOrThrow(policy, policy.subjectPrefix() + identifier, requireOwnEndpoint(policy), null);
    }

    public void check(RateLimitPolicy policy, UUID deviceId) {
        consumeOrThrow(policy, policy.subjectPrefix() + deviceId, requireOwnEndpoint(policy), null);
    }

    private static String requireOwnEndpoint(RateLimitPolicy policy) {
        String endpoint = policy.endpoint();
        Assert.notNull(endpoint, () -> policy + " keys its buckets by a caller-named endpoint");
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
