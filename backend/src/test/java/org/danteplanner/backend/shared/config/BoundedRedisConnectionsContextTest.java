package org.danteplanner.backend.shared.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The axis is derived from the context, so a connection factory declared anywhere — a new
 * endpoint, another configuration class — fails here unless it is built through
 * {@link BoundedRedisConnections}.
 */
@SpringBootTest
@ActiveProfiles("test")
class BoundedRedisConnectionsContextTest {

    @Autowired
    private Map<String, LettuceConnectionFactory> connectionFactories;

    @Test
    @DisplayName("every LettuceConnectionFactory bean carries the bounded command timeout")
    void connectionFactoryBeans_WhenContextLoads_CarryBoundedCommandTimeout() {
        assertThat(connectionFactories)
                .as("the axis is derived from the context; an empty map would pass vacuously")
                .isNotEmpty();

        connectionFactories.forEach((beanName, factory) -> assertThat(
                factory.getClientConfiguration().getCommandTimeout())
                .as("%s command timeout", beanName)
                .isEqualTo(BoundedRedisConnections.COMMAND_TIMEOUT));
    }
}
