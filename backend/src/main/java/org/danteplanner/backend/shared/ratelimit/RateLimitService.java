package org.danteplanner.backend.shared.ratelimit;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    private static final long MIN_RETRY_AFTER_SECONDS = 1;
    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final ProxyManager<byte[]> proxyManager;
    private final RateLimitProperties properties;

    public ConsumptionProbe charge(RateLimitPolicy policy, ChargeKey key) {
        return consume(key.spelled(), policy.bucket(properties));
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
