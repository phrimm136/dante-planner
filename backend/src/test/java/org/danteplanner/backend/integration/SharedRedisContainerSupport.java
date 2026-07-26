package org.danteplanner.backend.integration;

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

    private SharedRedisContainerSupport() {
    }

    private static final class Holder {
        private static final RedisContainer INSTANCE = start();

        private static RedisContainer start() {
            RedisContainer container = new RedisContainer(REDIS_IMAGE);
            container.start();
            return container;
        }
    }

    static void registerSharedRedis(DynamicPropertyRegistry registry) {
        registry.add("redis.auth.host", SharedRedisContainerSupport::host);
        registry.add("redis.auth.port", SharedRedisContainerSupport::port);
        registry.add("redis.auth-local.host", SharedRedisContainerSupport::host);
        registry.add("redis.auth-local.port", SharedRedisContainerSupport::port);
        registry.add("redis.rate-limit.host", SharedRedisContainerSupport::host);
        registry.add("redis.rate-limit.port", SharedRedisContainerSupport::port);
        registry.add("redis.sse-local.host", SharedRedisContainerSupport::host);
        registry.add("redis.sse-local.port", SharedRedisContainerSupport::port);
    }

    public static String host() {
        return Holder.INSTANCE.getRedisHost();
    }

    public static int port() {
        return Holder.INSTANCE.getRedisPort();
    }
}
