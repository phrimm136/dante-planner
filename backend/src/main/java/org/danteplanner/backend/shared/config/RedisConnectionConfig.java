package org.danteplanner.backend.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.validation.annotation.Validated;

/**
 * Wires the two logical Redis roles as distinct {@link LettuceConnectionFactory} beans.
 *
 * <ul>
 *   <li>{@code authRedisConnectionFactory} — the auth Redis (Oregon primary, durable):
 *       token-family rotation, blacklist, delete tombstones, SSE pub/sub. Marked
 *       {@link Primary} so Spring Data's auto-configured {@code RedisTemplate} resolves
 *       against the auth store when it autowires a single {@code RedisConnectionFactory}
 *       by type.</li>
 *   <li>{@code rateLimitRedisConnectionFactory} — the per-region local ephemeral Redis
 *       backing rate-limit buckets (no persistence, no replication).</li>
 * </ul>
 *
 * <p>Each endpoint's host/port is environment-specific and bound from
 * {@code application.properties} under the {@code redis} prefix. The factories connect
 * lazily — the default {@code LettuceConnectionFactory} opens no connection at startup —
 * so the beans exist without a live Redis behind them.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "redis")
@Validated
@Getter
@Setter
public class RedisConnectionConfig {

    @Valid
    private Endpoint auth = new Endpoint();

    @Valid
    private Endpoint rateLimit = new Endpoint();

    @Bean
    @Primary
    public LettuceConnectionFactory authRedisConnectionFactory() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(auth.getHost(), auth.getPort()));
    }

    @Bean
    public LettuceConnectionFactory rateLimitRedisConnectionFactory() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration(rateLimit.getHost(), rateLimit.getPort()));
    }

    @Getter
    @Setter
    public static class Endpoint {

        @NotBlank
        private String host;

        @Min(1)
        @Max(65535)
        private int port;
    }
}
