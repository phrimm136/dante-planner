package org.danteplanner.backend.shared.config;

import org.danteplanner.backend.config.TestConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

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
class RedisAuthPasswordBackCompatIT extends SharedAuthRedisSupport {

    static {
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void authRedis_WhenPasswordEmpty_WriteReadRoundTripsWithoutAuth() {
        String key = "bl:password-backcompat-test";

        stringRedisTemplate.opsForValue().set(key, "1");

        assertThat(stringRedisTemplate.opsForValue().get(key)).isEqualTo("1");
    }
}
