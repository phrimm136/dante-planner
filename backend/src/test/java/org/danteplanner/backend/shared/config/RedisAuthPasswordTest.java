package org.danteplanner.backend.shared.config;

import org.danteplanner.backend.config.TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the auth Redis password wiring: with {@code AUTH_REDIS_PASSWORD} set, the auth
 * connection factory authenticates against a {@code requirepass}-protected Redis and a
 * blacklist-style write+read round-trips. Without the password on the factory, every
 * command fails with {@code NOAUTH}.
 *
 * <p>Uses a raw {@link GenericContainer} (not {@code RedisContainer}) so the container
 * readiness check is a port wait rather than an unauthenticated PING, which a
 * {@code requirepass}-protected server rejects.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(TestConfig.class)
@Tag("containerized")
class RedisAuthPasswordTest {

    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final String REDIS_PASSWORD = "testpass";
    private static final int REDIS_PORT = 6379;

    static final GenericContainer<?> SECURED_REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(REDIS_PORT)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    static {
        SECURED_REDIS.start();
    }

    @DynamicPropertySource
    static void authRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("AUTH_REDIS_HOST", SECURED_REDIS::getHost);
        registry.add("AUTH_REDIS_PORT", SECURED_REDIS::getFirstMappedPort);
        registry.add("AUTH_REDIS_PASSWORD", () -> REDIS_PASSWORD);
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("With AUTH_REDIS_PASSWORD set, a blacklist write+read round-trips against a requirepass Redis")
    void authRedis_whenPasswordConfigured_blacklistWriteReadRoundTrips() {
        String key = "bl:password-wiring-test";

        stringRedisTemplate.opsForValue().set(key, "1");

        assertThat(stringRedisTemplate.opsForValue().get(key)).isEqualTo("1");
    }
}
