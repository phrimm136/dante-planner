package org.danteplanner.backend.shared.config;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
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
                rateLimit.getHost(), rateLimit.getPort(), Duration.ofSeconds(rateLimit.getBucketTtlSeconds())),
                System::nanoTime);
    }

    public static ProxyManager<byte[]> buildRateLimitProxyManager(String host, int port, Duration bucketTtl) {
        RedisClient client = BoundedRedisConnections.redisClient(host, port);
        StatefulRedisConnection<byte[], byte[]> connection;
        try {
            connection = client.connect(ByteArrayCodec.INSTANCE);
        } catch (RuntimeException e) {
            var unused = client.shutdownAsync(0, 2, TimeUnit.SECONDS);
            throw e;
        }
        return LettuceBasedProxyManager.builderFor(connection)
                .withClientSideConfig(ClientSideConfig.getDefault()
                        .withRequestTimeout(Duration.ofMillis(TimeoutHierarchy.RATE_LIMIT_FUTURE_TIMEOUT_MS)))
                .withExpirationStrategy(ExpirationAfterWriteStrategy.fixedTimeToLive(bucketTtl))
                .build();
    }

    static final class LazyConnectingProxyManager implements ProxyManager<byte[]> {

        private static final long RETRY_AFTER_NANOS =
                TimeUnit.MILLISECONDS.toNanos(TimeoutHierarchy.RATE_LIMIT_CONNECT_RETRY_AFTER_MS);

        private final Supplier<ProxyManager<byte[]>> connector;
        private final LongSupplier nanoTime;
        private final ReentrantLock stateLock = new ReentrantLock();
        private volatile ProxyManager<byte[]> target;
        private CompletableFuture<ProxyManager<byte[]>> attempt;
        private Throwable lastFailure;
        private long lastFailureNanos;

        LazyConnectingProxyManager(Supplier<ProxyManager<byte[]>> connector, LongSupplier nanoTime) {
            this.connector = connector;
            this.nanoTime = nanoTime;
        }

        private ProxyManager<byte[]> target() {
            ProxyManager<byte[]> connected = target;
            if (connected != null) {
                return connected;
            }
            CompletableFuture<ProxyManager<byte[]>> started = new CompletableFuture<>();
            CompletableFuture<ProxyManager<byte[]>> shared = join(started);
            return shared == started ? connect(started) : await(shared);
        }

        private CompletableFuture<ProxyManager<byte[]>> join(CompletableFuture<ProxyManager<byte[]>> started) {
            stateLock.lock();
            try {
                if (target != null) {
                    return CompletableFuture.completedFuture(target);
                }
                if (attempt != null) {
                    return attempt;
                }
                if (lastFailure != null && nanoTime.getAsLong() - lastFailureNanos < RETRY_AFTER_NANOS) {
                    return CompletableFuture.failedFuture(lastFailure);
                }
                attempt = started;
                return started;
            } finally {
                stateLock.unlock();
            }
        }

        private ProxyManager<byte[]> connect(CompletableFuture<ProxyManager<byte[]>> started) {
            try {
                ProxyManager<byte[]> connected = connector.get();
                succeeded(connected);
                started.complete(connected);
                return connected;
            } catch (RuntimeException | Error failure) {
                failed(failure);
                started.completeExceptionally(failure);
                throw failure;
            }
        }

        private void succeeded(ProxyManager<byte[]> connected) {
            stateLock.lock();
            try {
                target = connected;
                attempt = null;
            } finally {
                stateLock.unlock();
            }
        }

        private void failed(Throwable failure) {
            stateLock.lock();
            try {
                lastFailure = failure;
                lastFailureNanos = nanoTime.getAsLong();
                attempt = null;
            } finally {
                stateLock.unlock();
            }
        }

        private static ProxyManager<byte[]> await(CompletableFuture<ProxyManager<byte[]>> shared) {
            try {
                return shared.get(TimeoutHierarchy.RATE_LIMIT_CONNECT_WAIT_MS, TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                throw propagate(e.getCause());
            } catch (TimeoutException e) {
                throw new RedisConnectionException("Rate-limit store connect still in progress", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RedisConnectionException("Interrupted waiting for the rate-limit store connect", e);
            }
        }

        private static RuntimeException propagate(Throwable failure) {
            if (failure instanceof Error error) {
                throw error;
            }
            return (RuntimeException) failure;
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
