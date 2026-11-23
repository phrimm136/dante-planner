package org.danteplanner.backend.shared.ratelimit;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

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

    private static final String NO_USER = "null";
    private static final long MIN_RETRY_AFTER_SECONDS = 1;
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final ProxyManager<byte[]> proxyManager;
    private final RateLimitProperties properties;

    public void check(RateLimitPolicy policy, Long userId) {
        check(policy, userId, requireOwnEndpoint(policy));
    }

    public void check(RateLimitPolicy policy, Long userId, String endpoint) {
        consumeOrThrow(policy, policy.subjectPrefix() + userId, endpoint, userId.toString());
    }

    public void check(RateLimitPolicy policy, String identifier) {
        consumeOrThrow(policy, policy.subjectPrefix() + identifier, requireOwnEndpoint(policy),
                NO_USER);
    }

    public void check(RateLimitPolicy policy, UUID deviceId) {
        consumeOrThrow(policy, policy.subjectPrefix() + deviceId, requireOwnEndpoint(policy),
                NO_USER);
    }

    private static String requireOwnEndpoint(RateLimitPolicy policy) {
        return switch (policy.endpoint()) {
            case RateLimitPolicy.Endpoint.Fixed fixed -> fixed.name();
            case RateLimitPolicy.Endpoint.CallerNamed callerNamed ->
                    throw new IllegalArgumentException(policy + " keys its buckets by a caller-named endpoint");
        };
    }

    private enum Charge {
        ADMITTED,
        REFUSED;

        static Charge of(boolean consumed) {
            return consumed ? ADMITTED : REFUSED;
        }
    }

    private void consumeOrThrow(RateLimitPolicy policy, String subject, String endpoint,
            String refusedUser) {
        ConsumptionProbe probe = consume(subject + ":" + endpoint, policy.bucket(properties));
        switch (Charge.of(probe.isConsumed())) {
            case ADMITTED -> { }
            case REFUSED -> throw new RateLimitExceededException(
                    refusedUser, endpoint, retryAfterSeconds(probe.getNanosToWaitForRefill()));
        }
    }

    static long retryAfterSeconds(long nanosToWaitForRefill) {
        return Math.max(MIN_RETRY_AFTER_SECONDS, Math.ceilDiv(nanosToWaitForRefill, NANOS_PER_SECOND));
    }

    private ConsumptionProbe consume(String key, BucketConfig config) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        BucketConfiguration bucketConfiguration = buildConfiguration(config);
        return proxyManager.builder()
                .withImplicitConfigurationReplacement(properties.configVersion(), TokensInheritanceStrategy.AS_IS)
                .build(keyBytes, () -> bucketConfiguration)
                .tryConsumeAndReturnRemaining(1);
    }

    private BucketConfiguration buildConfiguration(BucketConfig config) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(config.capacity())
                .refillGreedy(config.refillTokens(), Duration.ofSeconds(config.refillDurationSeconds()))
                .build();
        return BucketConfiguration.builder().addLimit(limit).build();
    }
}
