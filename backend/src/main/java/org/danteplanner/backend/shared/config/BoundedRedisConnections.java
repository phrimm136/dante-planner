package org.danteplanner.backend.shared.config;

import java.time.Duration;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;

/**
 * The one place a Redis connection is constructed.
 *
 * <p>Lettuce defaults an unconfigured connection to a minute-long command timeout, both on
 * {@link LettuceClientConfiguration} and on a {@link RedisURI} parsed from a {@code redis://}
 * string.</p>
 */
public final class BoundedRedisConnections {

    /** Bound on any Redis command. */
    public static final Duration COMMAND_TIMEOUT = Duration.ofMillis(3_000L);

    private BoundedRedisConnections() {
    }

    /**
     * Builds a bounded Spring Data connection factory for an endpoint.
     *
     * @param configuration the endpoint's host, port and password
     * @return a connection factory whose commands give up after {@link #COMMAND_TIMEOUT}
     */
    public static LettuceConnectionFactory connectionFactory(RedisStandaloneConfiguration configuration) {
        return new LettuceConnectionFactory(configuration, clientConfiguration());
    }

    /**
     * Builds a bounded raw Lettuce client for an endpoint Spring Data does not own.
     *
     * @param host endpoint host
     * @param port endpoint port
     * @return a client whose commands give up after {@link #COMMAND_TIMEOUT}
     */
    public static RedisClient redisClient(String host, int port) {
        return RedisClient.create(redisUri(host, port));
    }

    /**
     * The bounded URI behind {@link #redisClient(String, int)}; a client keeps its URI private,
     * so this is the only shape the bound can be asserted on without opening a connection.
     *
     * @param host endpoint host
     * @param port endpoint port
     * @return a URI carrying {@link #COMMAND_TIMEOUT}
     */
    public static RedisURI redisUri(String host, int port) {
        return RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withTimeout(COMMAND_TIMEOUT)
                .build();
    }

    private static LettuceClientConfiguration clientConfiguration() {
        return LettuceClientConfiguration.builder()
                .commandTimeout(COMMAND_TIMEOUT)
                .build();
    }
}
