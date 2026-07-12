package org.danteplanner.backend.shared.config;

import com.redis.testcontainers.RedisContainer;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the password back-compat contract: with {@code AUTH_REDIS_PASSWORD} left at its
 * empty default, the auth connection factory sends no AUTH command and a write+read
 * still round-trips against a passwordless Redis — the single-region deployment keeps
 * working until the operator provisions the password.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(TestConfig.class)
@Tag("containerized")
class RedisAuthPasswordBackCompatTest {

    private static final String REDIS_IMAGE = "redis:7-alpine";

    static final RedisContainer OPEN_REDIS = new RedisContainer(REDIS_IMAGE);

    static {
        OPEN_REDIS.start();
    }

    @DynamicPropertySource
    static void authRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("AUTH_REDIS_HOST", OPEN_REDIS::getRedisHost);
        registry.add("AUTH_REDIS_PORT", OPEN_REDIS::getRedisPort);
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("With AUTH_REDIS_PASSWORD unset, a write+read round-trips against a no-auth Redis")
    void authRedis_whenPasswordEmpty_writeReadRoundTripsWithoutAuth() {
        String key = "bl:password-backcompat-test";

        stringRedisTemplate.opsForValue().set(key, "1");

        assertThat(stringRedisTemplate.opsForValue().get(key)).isEqualTo("1");
    }
}
