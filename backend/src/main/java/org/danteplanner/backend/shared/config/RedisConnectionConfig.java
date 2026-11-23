package org.danteplanner.backend.shared.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
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
@EnableConfigurationProperties(RedisConnectionConfig.Endpoints.class)
public class RedisConnectionConfig {

    private final Endpoints endpoints;

    public RedisConnectionConfig(Endpoints endpoints) {
        this.endpoints = endpoints;
    }

    public Endpoints endpoints() {
        return endpoints;
    }

    @Bean(destroyMethod = "shutdown")
    public ClientResources redisClientResources() {
        return DefaultClientResources.create();
    }

    @Bean
    @Primary
    public LettuceConnectionFactory authRedisConnectionFactory() {
        return BoundedRedisConnections.connectionFactory(standaloneConfiguration(endpoints.auth()), redisClientResources());
    }

    @Bean
    public LettuceConnectionFactory sseLocalRedisConnectionFactory() {
        return BoundedRedisConnections.connectionFactory(standaloneConfiguration(endpoints.sseLocal()), redisClientResources());
    }

    @Bean
    public LettuceConnectionFactory authLocalRedisConnectionFactory() {
        return BoundedRedisConnections.connectionFactory(standaloneConfiguration(endpoints.authLocal()), redisClientResources());
    }

    private static RedisStandaloneConfiguration standaloneConfiguration(Endpoints.Endpoint endpoint) {
        RedisStandaloneConfiguration configuration =
                new RedisStandaloneConfiguration(endpoint.host(), endpoint.port());
        if (StringUtils.hasText(endpoint.password())) {
            configuration.setPassword(RedisPassword.of(endpoint.password()));
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
        return new RateLimitStore(redisClientResources(), endpoints.rateLimit().host(), endpoints.rateLimit().port(),
                Duration.ofSeconds(endpoints.rateLimit().bucketTtlSeconds()));
    }

    @ConfigurationProperties(prefix = "redis")
    @Validated
    public record Endpoints(
            @Valid @DefaultValue Endpoint auth,
            @Valid @DefaultValue Endpoint rateLimit,
            @Valid @DefaultValue Endpoint sseLocal,
            @Valid @DefaultValue Endpoint authLocal) {

        public record Endpoint(
                @NotBlank String host,
                @Min(1) @Max(65535) int port,
                @Min(1) @DefaultValue(DEFAULT_BUCKET_TTL_SECONDS) int bucketTtlSeconds,
                @DefaultValue("") String password) {

            static final String DEFAULT_BUCKET_TTL_SECONDS = "3600";

            public static Endpoint at(String host, int port) {
                return new Endpoint(host, port, Integer.parseInt(DEFAULT_BUCKET_TTL_SECONDS), "");
            }
        }
    }
}
