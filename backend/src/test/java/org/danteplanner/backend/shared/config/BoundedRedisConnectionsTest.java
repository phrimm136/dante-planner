package org.danteplanner.backend.shared.config;

import java.time.Duration;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import io.lettuce.core.RedisURI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the bound carried by both shapes of Redis connection this application opens: the
 * Spring Data connection factory and the raw Lettuce client the rate-limit buckets ride on.
 */
class BoundedRedisConnectionsTest {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 6379;

    @Test
    @DisplayName("a connection factory from the helper carries the bounded command timeout")
    void connectionFactory_WhenBuilt_CarriesBoundedCommandTimeout() {
        LettuceConnectionFactory factory =
                BoundedRedisConnections.connectionFactory(new RedisStandaloneConfiguration(HOST, PORT));

        assertThat(factory.getClientConfiguration().getCommandTimeout())
                .isEqualTo(BoundedRedisConnections.COMMAND_TIMEOUT);
    }

    @Test
    @DisplayName("a Redis URI from the helper carries the bound, not Lettuce's default")
    void redisUri_WhenBuilt_CarriesBoundedTimeoutRatherThanLettuceDefault() {
        RedisURI uri = BoundedRedisConnections.redisUri(HOST, PORT);

        assertThat(uri.getHost()).isEqualTo(HOST);
        assertThat(uri.getPort()).isEqualTo(PORT);
        assertThat(uri.getTimeout()).isEqualTo(BoundedRedisConnections.COMMAND_TIMEOUT);
        assertThat(uri.getTimeout())
                .as("a bound equal to the default would make this suite vacuous")
                .isLessThan(RedisURI.DEFAULT_TIMEOUT_DURATION);
    }

    @Test
    @DisplayName("the bound is three seconds")
    void commandTimeout_WhenRead_IsThreeSeconds() {
        assertThat(BoundedRedisConnections.COMMAND_TIMEOUT).isEqualTo(Duration.ofSeconds(3));
    }
}
