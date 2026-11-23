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
import java.util.List;

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
    private volatile State state = new State.Disconnected();

    public RateLimitStore(ClientResources clientResources, String host, int port, Duration bucketTtl) {
        this.uri = BoundedRedisConnections.redisUri(host, port);
        this.client = BoundedRedisConnections.redisClient(clientResources, host, port);
        client.setOptions(ClientOptions.builder()
                .autoReconnect(false)
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
            switch (state) {
                case State.Closed closed -> {
                    return CompletableFuture.failedFuture(new RedisConnectionException("Rate-limit store closed"));
                }
                case State.Connected connected -> {
                    if (connected.connection().redis().isOpen()) {
                        return CONNECTED;
                    }
                    state = new State.Disconnected();
                    var unused = connected.connection().redis().closeAsync();
                }
                case State.Connecting connecting -> {
                    if (!connecting.attempt().isDone()) {
                        return connecting.attempt();
                    }
                }
                case State.Disconnected disconnected -> {
                }
            }
            CompletableFuture<Void> attempt =
                    client.connectAsync(ByteArrayCodec.INSTANCE, uri).toCompletableFuture().thenAccept(this::adopt);
            if (!attempt.isDone()) {
                state = new State.Connecting(attempt);
            }
            return attempt;
        } finally {
            attemptLock.unlock();
        }
    }

    public boolean isConnected() {
        return switch (state) {
            case State.Connected connected -> connected.connection().redis().isOpen();
            case State.Closed closed -> closed.isOpen();
            case State.Connecting connecting -> false;
            case State.Disconnected disconnected -> false;
        };
    }

    @Override
    public void close() {
        attemptLock.lock();
        try {
            state = switch (state) {
                case State.Connected connected -> new State.Closed(List.of(connected.connection()));
                case State.Connecting connecting -> new State.Closed(List.of());
                case State.Disconnected disconnected -> new State.Closed(List.of());
                case State.Closed closed -> closed;
            };
        } finally {
            attemptLock.unlock();
        }
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
        Connection adopted = new Connection(redis, LettuceBasedProxyManager.builderFor(redis)
                .withClientSideConfig(ClientSideConfig.getDefault()
                        .withRequestTimeout(Duration.ofMillis(TimeoutHierarchy.RATE_LIMIT_FUTURE_TIMEOUT_MS)))
                .withExpirationStrategy(ExpirationAfterWriteStrategy.fixedTimeToLive(bucketTtl))
                .build());
        attemptLock.lock();
        try {
            state = switch (state) {
                case State.Closed closed -> new State.Closed(List.of(adopted));
                case State.Connected connected -> new State.Connected(adopted);
                case State.Connecting connecting -> new State.Connected(adopted);
                case State.Disconnected disconnected -> new State.Connected(adopted);
            };
        } finally {
            attemptLock.unlock();
        }
    }

    private ProxyManager<byte[]> proxyManager() {
        return switch (state) {
            case State.Connected connected -> connected.connection().proxyManager();
            case State.Closed closed when !closed.held().isEmpty() -> closed.held().getFirst().proxyManager();
            case State.Closed closed -> throw notConnected();
            case State.Connecting connecting -> throw notConnected();
            case State.Disconnected disconnected -> throw notConnected();
        };
    }

    private static RedisConnectionException notConnected() {
        return new RedisConnectionException("Rate-limit store not connected");
    }

    private sealed interface State {

        record Disconnected() implements State {
        }

        record Connecting(CompletableFuture<Void> attempt) implements State {
        }

        record Connected(Connection connection) implements State {
        }

        record Closed(List<Connection> held) implements State {

            boolean isOpen() {
                return held.stream().anyMatch(connection -> connection.redis().isOpen());
            }
        }
    }

    private record Connection(StatefulRedisConnection<byte[], byte[]> redis, ProxyManager<byte[]> proxyManager) {
    }
}
