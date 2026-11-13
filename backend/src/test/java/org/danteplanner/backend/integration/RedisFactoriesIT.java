package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.shared.ratelimit.RateLimitStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the narrowest Redis-bean contract: the auth and auth-local {@link LettuceConnectionFactory}
 * beans are distinct and injectable by their qualifiers, and the rate-limit store is its own bean.
 * It deliberately does NOT exercise any Redis command path — that is downstream (blacklist,
 * rate limiter). It asserts injectability and instance-distinctness only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class RedisFactoriesIT extends CausalHarnessSupport {

    @Autowired
    @Qualifier("authRedisConnectionFactory")
    private LettuceConnectionFactory authRedisConnectionFactory;

    @Autowired
    private RateLimitStore rateLimitStore;

    @Autowired(required = false)
    @Qualifier("authLocalRedisConnectionFactory")
    private LettuceConnectionFactory authLocalRedisConnectionFactory;

    @Test
    void redisFactories_WhenAuthAndRateLimit_AreDistinctInjectableBeans() {
        assertThat(authRedisConnectionFactory).isNotNull();
        assertThat(rateLimitStore).isNotNull();
    }

    @Test
    void authLocalFactory_WhenContextLoads_IsDistinctInjectableBean() {
        assertThat(authLocalRedisConnectionFactory).isNotNull();
        assertThat(authLocalRedisConnectionFactory).isNotSameAs(authRedisConnectionFactory);
    }
}
