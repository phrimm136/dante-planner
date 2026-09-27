package org.danteplanner.backend.shared.config;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>Defining an explicit {@link Primary} {@code stringRedisTemplate} makes Spring Boot's
 * auto-configured template back off, so every by-type {@code StringRedisTemplate} injection
 * resolves to it.</p>
 *
 * <p>The default {@code LettuceConnectionFactory} opens no connection at startup, so the beans
 * exist without a live Redis behind them.</p>
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
        return BoundedRedisConnections.connectionFactory(standaloneConfiguration(auth));
    }

    @Bean
    public LettuceConnectionFactory rateLimitRedisConnectionFactory() {
        return BoundedRedisConnections.connectionFactory(standaloneConfiguration(rateLimit));
    }

    @Bean
    public LettuceConnectionFactory sseLocalRedisConnectionFactory() {
        return BoundedRedisConnections.connectionFactory(standaloneConfiguration(sseLocal));
    }

    @Bean
    public LettuceConnectionFactory authLocalRedisConnectionFactory() {
        return BoundedRedisConnections.connectionFactory(standaloneConfiguration(authLocal));
    }

    private static RedisStandaloneConfiguration standaloneConfiguration(Endpoint endpoint) {
        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration(endpoint.getHost(), endpoint.getPort());
        if (StringUtils.hasText(endpoint.getPassword())) {
            configuration.setPassword(RedisPassword.of(endpoint.getPassword()));
        }
        return configuration;
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

    /**
     * Building the underlying proxy manager opens a Lettuce connection.
     */
    @Bean
    public ProxyManager<byte[]> rateLimitProxyManager() {
        return new LazyConnectingProxyManager(() -> buildRateLimitProxyManager(
                rateLimit.getHost(), rateLimit.getPort(), Duration.ofSeconds(rateLimit.getBucketTtlSeconds())));
    }

    public static ProxyManager<byte[]> buildRateLimitProxyManager(String host, int port, Duration bucketTtl) {
        RedisClient client = BoundedRedisConnections.redisClient(host, port);
        StatefulRedisConnection<byte[], byte[]> connection;
        try {
            connection = client.connect(ByteArrayCodec.INSTANCE);
        } catch (RuntimeException e) {
            client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
            throw e;
        }
        return LettuceBasedProxyManager.builderFor(connection)
                .withClientSideConfig(ClientSideConfig.getDefault()
                        .withRequestTimeout(Duration.ofMillis(TimeoutHierarchy.RATE_LIMIT_FUTURE_TIMEOUT_MS)))
                .withExpirationStrategy(ExpirationAfterWriteStrategy.fixedTimeToLive(bucketTtl))
                .build();
    }

    static final class LazyConnectingProxyManager implements ProxyManager<byte[]> {

        private final Supplier<ProxyManager<byte[]>> connector;
        private final ReentrantLock connectLock = new ReentrantLock();
        private volatile ProxyManager<byte[]> target;

        LazyConnectingProxyManager(Supplier<ProxyManager<byte[]>> connector) {
            this.connector = connector;
        }

        private ProxyManager<byte[]> target() {
            ProxyManager<byte[]> connected = target;
            if (connected != null) {
                return connected;
            }
            if (!connectLock.tryLock()) {
                throw new RedisConnectionException("Rate-limit store connect already in progress");
            }
            try {
                connected = target;
                if (connected == null) {
                    connected = connector.get();
                    target = connected;
                }
                return connected;
            } finally {
                connectLock.unlock();
            }
        }

        @Override
        public RemoteBucketBuilder<byte[]> builder() {
            return target().builder();
        }

        @Override
        public Optional<BucketConfiguration> getProxyConfiguration(byte[] key) {
            return target().getProxyConfiguration(key);
        }

        @Override
        public void removeProxy(byte[] key) {
            target().removeProxy(key);
        }

        @Override
        public boolean isAsyncModeSupported() {
            return target().isAsyncModeSupported();
        }

        @Override
        public AsyncProxyManager<byte[]> asAsync() {
            return target().asAsync();
        }
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

        /** Empty = connect without AUTH; non-empty = the endpoint's requirepass value. */
        private String password = "";
    }
}
