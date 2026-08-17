package org.danteplanner.backend.integration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.test.context.DynamicPropertyRegistry;

import com.redis.testcontainers.RedisContainer;

/**
 * One {@code redis-server} per Gradle fork, started on first use so the Docker-free unit tier never
 * pays for it.
 *
 * <p>Without this the endpoints resolve to {@code application.properties}' {@code localhost:6379},
 * which is whatever the developer happens to be running.</p>
 */
public final class SharedRedisContainerSupport {

    private static final String REDIS_IMAGE = "redis:7-alpine";

    /** Keyed by the database name its owner passed to {@code registerOwnDatabase}. */
    private static final Map<String, RedisContainer> OWNED = new ConcurrentHashMap<>();

    private SharedRedisContainerSupport() {
    }

    private static final class Holder {
        private static final RedisContainer INSTANCE = start();
    }

    private static RedisContainer start() {
        RedisContainer container = new RedisContainer(REDIS_IMAGE);
        container.start();
        return container;
    }

    static void registerSharedRedis(DynamicPropertyRegistry registry) {
        registerEndpoints(registry, SharedRedisContainerSupport::host, SharedRedisContainerSupport::port);
    }

    /**
     * Binds a caller to a {@code redis-server} of its own.
     *
     * <p>For a class that owns its database. The auth store keys user invalidation as
     * {@code uinv:<userId>} and every rate-limit bucket as {@code <userId>:<endpoint>}, both bare
     * auto-increment ids whose uniqueness holds only within one database. A per-class database
     * restarts that sequence at 1, so classes isolating their data would still meet on
     * {@code uinv:1} in a Redis they share, and one would revoke the other's token.</p>
     *
     * @param registry the Spring dynamic property registry
     * @param name     a name unique to the calling class
     */
    static void registerOwnRedis(DynamicPropertyRegistry registry, String name) {
        RedisContainer container = OWNED.computeIfAbsent(name, key -> start());
        registerEndpoints(registry, container::getRedisHost, container::getRedisPort);
    }

    private static void registerEndpoints(DynamicPropertyRegistry registry,
            Supplier<Object> host, Supplier<Object> port) {
        registry.add("redis.auth.host", host);
        registry.add("redis.auth.port", port);
        registry.add("redis.auth-local.host", host);
        registry.add("redis.auth-local.port", port);
        registry.add("redis.rate-limit.host", host);
        registry.add("redis.rate-limit.port", port);
        registry.add("redis.sse-local.host", host);
        registry.add("redis.sse-local.port", port);
    }

    public static String host() {
        return Holder.INSTANCE.getRedisHost();
    }

    public static int port() {
        return Holder.INSTANCE.getRedisPort();
    }
}
