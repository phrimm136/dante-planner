package org.danteplanner.backend.shared.config;

import java.time.Duration;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;

/**
 * Lettuce defaults an unconfigured connection to a minute-long command timeout, both on
 * {@link LettuceClientConfiguration} and on a {@link RedisURI} parsed from a {@code redis://}
 * string.
 */
public final class BoundedRedisConnections {

    public static final Duration COMMAND_TIMEOUT = Duration.ofMillis(3_000L);

    private BoundedRedisConnections() {
    }

    public static LettuceConnectionFactory connectionFactory(RedisStandaloneConfiguration configuration) {
        return new LettuceConnectionFactory(configuration, clientConfiguration());
    }

    public static RedisClient redisClient(String host, int port) {
        return RedisClient.create(redisUri(host, port));
    }

    /**
     * A Lettuce client keeps its URI private, so this is the only shape the bound can be asserted
     * on without opening a connection.
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
