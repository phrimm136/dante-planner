package org.danteplanner.backend.config;

import java.util.function.Supplier;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test configuration class providing beans needed for integration tests.
 *
 * <p>This configuration provides a Jackson 2.x ObjectMapper since the application
 * code uses com.fasterxml.jackson while Spring Boot 4.0.0-SNAPSHOT auto-configures
 * Jackson 3.x (tools.jackson).</p>
 */
@TestConfiguration
public class TestConfig {

    /**
     * Provides a Jackson 2.x ObjectMapper for use in tests.
     *
     * @return configured ObjectMapper instance
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Inert rate-limit proxy manager for the hermetic {@code test} profile: every bucket
     * always allows consumption, so rate-limited endpoints serve without a live Redis.
     *
     * <p>Guarded to the {@code test} profile because {@code it}-profile tests
     * (e.g. {@code DegradationIT}) assert the behavior of the real Redis-backed manager.
     * Rate-limit semantics themselves are covered by the containerized
     * {@code RateLimitConfigTest}.</p>
     *
     * <p>Deliberately NOT named {@code rateLimitProxyManager}: with
     * {@code allow-bean-definition-overriding=true}, the component-scanned definition of the
     * same name would register later and silently replace this one. A distinct name plus
     * {@code @Primary} wins by-type injection instead.</p>
     *
     * @return a ProxyManager whose buckets never exhaust
     */
    @Bean
    @Primary
    @Profile("test")
    @SuppressWarnings("unchecked")
    public ProxyManager<byte[]> testRateLimitProxyManager() {
        BucketProxy alwaysAllow = mock(BucketProxy.class);
        when(alwaysAllow.tryConsume(anyLong())).thenReturn(true);
        RemoteBucketBuilder<byte[]> builder = mock(RemoteBucketBuilder.class);
        when(builder.build(any(byte[].class), any(Supplier.class))).thenReturn(alwaysAllow);
        ProxyManager<byte[]> proxyManager = mock(ProxyManager.class);
        when(proxyManager.builder()).thenReturn(builder);
        return proxyManager;
    }
}
