package org.danteplanner.backend.shared.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import lombok.Getter;
import lombok.Setter;

/**
 * Wires the three logical Redis roles as distinct {@link LettuceConnectionFactory} beans.
 *
 * <ul>
 *   <li>{@code authRedisConnectionFactory} — the auth Redis (Oregon primary, durable):
 *       token-family rotation, blacklist, delete tombstones, SSE pub/sub. Marked
 *       {@link Primary} so Spring Data's auto-configured {@code RedisTemplate} resolves
 *       against the auth store when it autowires a single {@code RedisConnectionFactory}
 *       by type.</li>
 *   <li>{@code rateLimitRedisConnectionFactory} — the per-region local ephemeral Redis
 *       backing rate-limit buckets (no persistence, no replication).</li>
 *   <li>{@code sseLocalRedisConnectionFactory} — the per-region local Redis a pod
 *       subscribes to for SSE fan-out; single-region it is the same host as auth,
 *       multi-region it is the regional replica.</li>
 *   <li>{@code authLocalRedisConnectionFactory} — the per-region auth read replica;
 *       single-region it is the same host as auth, multi-region it is the regional
 *       replica serving local reads.</li>
 * </ul>
 *
 * <p>The explicit {@link Primary} {@code stringRedisTemplate} is the auth write template:
 * defining it makes Spring Boot's auto-configured template back off, so every by-type
 * {@code StringRedisTemplate} injection resolves deterministically to the auth (write-global)
 * store, while the non-{@code @Primary} {@code authLocalStringRedisTemplate} serves the
 * read-local path.</p>
 *
 * <p>Each endpoint's host/port is environment-specific and bound from
 * {@code application.properties} under the {@code redis} prefix. The factories connect
 * lazily — the default {@code LettuceConnectionFactory} opens no connection at startup —
 * so the beans exist without a live Redis behind them.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "redis")
@Validated
@Getter
@Setter
public class RedisConnectionConfig {

    @Valid
    private Endpoint auth = new Endpoint();

    @Valid
    private Endpoint rateLimit = new Endpoint();

    @Valid
    private Endpoint sseLocal = new Endpoint();

    @Valid
    private Endpoint authLocal = new Endpoint();

    @Bean
    @Primary
    public LettuceConnectionFactory authRedisConnectionFactory() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(auth.getHost(), auth.getPort()));
    }

    @Bean
    public LettuceConnectionFactory rateLimitRedisConnectionFactory() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(rateLimit.getHost(), rateLimit.getPort()));
    }

    @Bean
    public LettuceConnectionFactory sseLocalRedisConnectionFactory() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(sseLocal.getHost(), sseLocal.getPort()));
    }

    @Bean
    public LettuceConnectionFactory authLocalRedisConnectionFactory() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(authLocal.getHost(), authLocal.getPort()));
    }

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        return new StringRedisTemplate(authRedisConnectionFactory());
    }

    @Bean
    public StringRedisTemplate authLocalStringRedisTemplate() {
        return new StringRedisTemplate(authLocalRedisConnectionFactory());
    }

    @Bean
    public ProxyManager<byte[]> rateLimitProxyManager() {
        return buildRateLimitProxyManager(
                rateLimit.getHost(), rateLimit.getPort(), Duration.ofSeconds(rateLimit.getBucketTtlSeconds()));
    }

    /**
     * Builds a bucket4j {@link ProxyManager} whose buckets live in the local ephemeral
     * rate-limit Redis, keyed by the UTF-8 bytes of the rate-limit key.
     *
     * @param host      rate-limit Redis host
     * @param port      rate-limit Redis port
     * @param bucketTtl per-bucket time-to-live for the Redis key
     * @return a byte[]-keyed proxy manager backed by the given Redis endpoint
     */
    public static ProxyManager<byte[]> buildRateLimitProxyManager(String host, int port, Duration bucketTtl) {
        RedisClient client = RedisClient.create("redis://" + host + ":" + port);
        StatefulRedisConnection<byte[], byte[]> connection = client.connect(ByteArrayCodec.INSTANCE);
        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(ExpirationAfterWriteStrategy.fixedTimeToLive(bucketTtl))
                .build();
    }

    @Getter
    @Setter
    public static class Endpoint {

        @NotBlank
        private String host;

        @Min(1)
        @Max(65535)
        private int port;

        @Min(1)
        private int bucketTtlSeconds = 3600;
    }
}
