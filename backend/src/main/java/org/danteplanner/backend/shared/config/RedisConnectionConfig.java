package org.danteplanner.backend.shared.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import lombok.Getter;
import lombok.Setter;

import org.danteplanner.backend.shared.ratelimit.RateLimitStore;

/**
 * <p>Defining an explicit {@link Primary} {@code stringRedisTemplate} makes Spring Boot's
 * auto-configured template back off, so every by-type {@code StringRedisTemplate} injection
 * resolves to it.</p>
 *
 * <p>The default {@code LettuceConnectionFactory} opens no connection at startup, so the beans
 * exist without a live Redis behind them.</p>
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

    @Valid
    private Endpoint sseLocal = new Endpoint();

    @Valid
    private Endpoint authLocal = new Endpoint();

    @Bean(destroyMethod = "shutdown")
    public ClientResources redisClientResources() {
        return DefaultClientResources.create();
    }

    @Bean
    @Primary
    public LettuceConnectionFactory authRedisConnectionFactory() {
        return BoundedRedisConnections.connectionFactory(standaloneConfiguration(auth), redisClientResources());
    }

    @Bean
    public LettuceConnectionFactory sseLocalRedisConnectionFactory() {
        return BoundedRedisConnections.connectionFactory(standaloneConfiguration(sseLocal), redisClientResources());
    }

    @Bean
    public LettuceConnectionFactory authLocalRedisConnectionFactory() {
        return BoundedRedisConnections.connectionFactory(standaloneConfiguration(authLocal), redisClientResources());
    }

    private static RedisStandaloneConfiguration standaloneConfiguration(Endpoint endpoint) {
        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration(endpoint.getHost(), endpoint.getPort());
        if (StringUtils.hasText(endpoint.getPassword())) {
            configuration.setPassword(RedisPassword.of(endpoint.getPassword()));
        }
        return configuration;
    }

    @Bean
    @Primary
    public StringRedisTemplate stringRedisTemplate() {
        return new StringRedisTemplate(authRedisConnectionFactory());
    }

    @Bean
    public StringRedisTemplate authLocalStringRedisTemplate() {
        return new StringRedisTemplate(authLocalRedisConnectionFactory());
    }

    @Bean
    public RateLimitStore rateLimitProxyManager() {
        return new RateLimitStore(redisClientResources(), rateLimit.getHost(), rateLimit.getPort(),
                Duration.ofSeconds(rateLimit.getBucketTtlSeconds()));
    }

    @Getter
    @Setter
    public static class Endpoint {

        @NotBlank
        private String host;

        @Min(1)
        @Max(65535)
        private int port;

        @Min(1)
        private int bucketTtlSeconds = 3600;

        /** Empty = connect without AUTH; non-empty = the endpoint's requirepass value. */
        private String password = "";
    }
}
