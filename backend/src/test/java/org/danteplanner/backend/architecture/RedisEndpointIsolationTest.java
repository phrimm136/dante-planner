package org.danteplanner.backend.architecture;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A test context reaches no Redis outside its own run.
 *
 * <p>{@code application.properties} defaults every endpoint to {@code localhost:6379}, and the
 * token blacklist is keyed by bare user id while every test database restarts auto-increment at 1,
 * so a shared Redis lets one class revoke another's freshly minted token.</p>
 *
 * <p>The axis is derived from the context's own {@link LettuceConnectionFactory} beans, so an
 * endpoint added to {@code RedisConnectionConfig} without a matching default fails here. The
 * expectation is an exact equality because a port-range test would admit 6379.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class RedisEndpointIsolationTest {

    private static final String REFUSED_ENDPOINT = "127.0.0.1:1";

    @Autowired
    private Map<String, LettuceConnectionFactory> connectionFactories;

    @Test
    @DisplayName("every configured Redis endpoint resolves to the refused port, not the machine's Redis")
    void redisEndpoint_WhenConfigured_IsIsolatedFromDeveloperMachine() {
        assertThat(connectionFactories)
                .as("the axis is derived from the context; an empty map would pass vacuously")
                .isNotEmpty();

        connectionFactories.forEach((beanName, factory) -> {
            RedisStandaloneConfiguration configuration = factory.getStandaloneConfiguration();

            assertThat(configuration.getHostName() + ":" + configuration.getPort())
                    .as("%s resolves outside this test run", beanName)
                    .isEqualTo(REFUSED_ENDPOINT);
        });
    }
}
