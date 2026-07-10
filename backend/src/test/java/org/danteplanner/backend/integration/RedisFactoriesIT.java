package org.danteplanner.backend.integration;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase-1 scenario test: the two production Redis connection factories are the foundation
 * deliverable.
 *
 * <p>Pins the narrowest Redis-bean contract — two distinct
 * {@link LettuceConnectionFactory} beans exist and are injectable by their distinct
 * qualifiers: {@code authRedisConnectionFactory} (auth Redis, Oregon primary) and
 * {@code rateLimitRedisConnectionFactory} (the local ephemeral rate-limit Redis). It
 * deliberately does NOT exercise any Redis command path — that is downstream (blacklist,
 * rate limiter). It asserts injectability and instance-distinctness only.</p>
 *
 * <p>See {@code mechanics.md §1} (two Redis roles) and {@code §9.5} (qualifier naming,
 * PLAN-TIME); {@code plan.md} "Redis connection topology".</p>
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
    @Qualifier("rateLimitRedisConnectionFactory")
    private LettuceConnectionFactory rateLimitRedisConnectionFactory;

    @Test
    @DisplayName("Auth and rate-limit LettuceConnectionFactory beans are distinct injectable instances")
    void redisFactories_authAndRateLimit_areDistinctInjectableBeans() {
        assertThat(authRedisConnectionFactory).isNotNull();
        assertThat(rateLimitRedisConnectionFactory).isNotNull();
        assertThat(authRedisConnectionFactory).isNotSameAs(rateLimitRedisConnectionFactory);
    }
}
