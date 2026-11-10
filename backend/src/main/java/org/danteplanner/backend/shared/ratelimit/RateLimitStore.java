package org.danteplanner.backend.shared.ratelimit;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.resource.ClientResources;

import org.danteplanner.backend.shared.config.BoundedRedisConnections;
import org.danteplanner.backend.shared.config.TimeoutHierarchy;
import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RoleConnector;

public class RateLimitStore implements ProxyManager<byte[]>, RoleConnector, AutoCloseable {

    private static final CompletableFuture<Void> CONNECTED = CompletableFuture.completedFuture(null);

    private final RedisClient client;
    private final RedisURI uri;
    private final Duration bucketTtl;
    private final ReentrantLock attemptLock = new ReentrantLock();
    private volatile Connection connection;
    private CompletableFuture<Void> attempt;

    public RateLimitStore(ClientResources clientResources, String host, int port, Duration bucketTtl) {
        this.uri = BoundedRedisConnections.redisUri(host, port);
        this.client = BoundedRedisConnections.redisClient(clientResources, host, port);
        client.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(BoundedRedisConnections.COMMAND_TIMEOUT).build())
                .build());
        this.bucketTtl = bucketTtl;
    }

    @Override
    public RedisRole role() {
        return RedisRole.RATE_LIMIT;
    }

    @Override
    public CompletableFuture<Void> connect() {
        attemptLock.lock();
        try {
            Connection current = connection;
            if (current != null) {
                return current.redis().isOpen() ? CONNECTED
                        : CompletableFuture.failedFuture(new RedisConnectionException("Rate-limit store reconnecting"));
            }
            if (attempt == null || attempt.isDone()) {
                attempt = client.connectAsync(ByteArrayCodec.INSTANCE, uri).toCompletableFuture().thenAccept(this::adopt);
            }
            return attempt;
        } finally {
            attemptLock.unlock();
        }
    }

    public boolean isConnected() {
        return connection != null;
    }

    @Override
    public void close() {
        client.shutdown();
    }

    @Override
    public RemoteBucketBuilder<byte[]> builder() {
        return proxyManager().builder();
    }

    @Override
    public Optional<BucketConfiguration> getProxyConfiguration(byte[] key) {
        return proxyManager().getProxyConfiguration(key);
    }

    @Override
    public void removeProxy(byte[] key) {
        proxyManager().removeProxy(key);
    }

    @Override
    public boolean isAsyncModeSupported() {
        return proxyManager().isAsyncModeSupported();
    }

    @Override
    public AsyncProxyManager<byte[]> asAsync() {
        return proxyManager().asAsync();
    }

    private void adopt(StatefulRedisConnection<byte[], byte[]> redis) {
        connection = new Connection(redis, LettuceBasedProxyManager.builderFor(redis)
                .withClientSideConfig(ClientSideConfig.getDefault()
                        .withRequestTimeout(Duration.ofMillis(TimeoutHierarchy.RATE_LIMIT_FUTURE_TIMEOUT_MS)))
                .withExpirationStrategy(ExpirationAfterWriteStrategy.fixedTimeToLive(bucketTtl))
                .build());
    }

    private ProxyManager<byte[]> proxyManager() {
        Connection current = connection;
        if (current == null) {
            throw new RedisConnectionException("Rate-limit store not connected");
        }
        return current.proxyManager();
    }

    private record Connection(StatefulRedisConnection<byte[], byte[]> redis, ProxyManager<byte[]> proxyManager) {
    }
}
