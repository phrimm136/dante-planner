package org.danteplanner.backend.shared.ratelimit;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import org.danteplanner.backend.shared.ratelimit.RateLimitProperties.BucketConfig;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TokensInheritanceStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final long MIN_RETRY_AFTER_SECONDS = 1;
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

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
        ConsumptionProbe probe = consume(subject + ":" + endpoint, policy.bucket(properties));
        if (!probe.isConsumed()) {
            throw new RateLimitExceededException(userId, endpoint, retryAfterSeconds(probe.getNanosToWaitForRefill()));
        }
    }

    static long retryAfterSeconds(long nanosToWaitForRefill) {
        return Math.max(MIN_RETRY_AFTER_SECONDS, Math.ceilDiv(nanosToWaitForRefill, NANOS_PER_SECOND));
    }

    private ConsumptionProbe consume(String key, BucketConfig config) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        BucketConfiguration bucketConfiguration = buildConfiguration(config);
        return proxyManager.builder()
                .withImplicitConfigurationReplacement(properties.getConfigVersion(), TokensInheritanceStrategy.AS_IS)
                .build(keyBytes, () -> bucketConfiguration)
                .tryConsumeAndReturnRemaining(1);
    }

    private BucketConfiguration buildConfiguration(BucketConfig config) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(config.getCapacity())
                .refillGreedy(config.getRefillTokens(), Duration.ofSeconds(config.getRefillDurationSeconds()))
                .build();
        return BucketConfiguration.builder().addLimit(limit).build();
    }
}
