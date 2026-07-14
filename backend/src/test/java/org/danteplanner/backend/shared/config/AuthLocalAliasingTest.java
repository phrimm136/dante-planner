package org.danteplanner.backend.shared.config;

import com.redis.testcontainers.RedisContainer;
import org.danteplanner.backend.config.TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins single-region auth-local aliasing: when {@code AUTH_LOCAL_REDIS_*} is unset, the
 * nested default in application.properties makes the auth-local Redis endpoint follow the
 * auth Redis endpoint set via {@code AUTH_REDIS_*}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(TestConfig.class)
@Tag("containerized")
class AuthLocalAliasingTest {

    private static final String REDIS_IMAGE = "redis:7-alpine";

    static final RedisContainer ALIAS_REDIS = new RedisContainer(REDIS_IMAGE);

    static {
        ALIAS_REDIS.start();
    }

    @DynamicPropertySource
    static void authRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("AUTH_REDIS_HOST", ALIAS_REDIS::getRedisHost);
        registry.add("AUTH_REDIS_PORT", ALIAS_REDIS::getRedisPort);
    }

    @Autowired
    @Qualifier("authRedisConnectionFactory")
    private LettuceConnectionFactory authFactory;

    @Autowired
    @Qualifier("authLocalRedisConnectionFactory")
    private LettuceConnectionFactory authLocalFactory;

    @Test
    @DisplayName("When AUTH_LOCAL_REDIS_* is unset, auth-local resolves to the same endpoint as auth")
    void authLocal_whenVarsUnset_aliasesToAuthEndpoint() {
        String authHost = authFactory.getStandaloneConfiguration().getHostName();
        int authPort = authFactory.getStandaloneConfiguration().getPort();
        String authLocalHost = authLocalFactory.getStandaloneConfiguration().getHostName();
        int authLocalPort = authLocalFactory.getStandaloneConfiguration().getPort();

        assertThat(authLocalHost).isEqualTo(authHost);
        assertThat(authLocalPort).isEqualTo(authPort);
        assertThat(authPort).isEqualTo(ALIAS_REDIS.getRedisPort());
    }
}
