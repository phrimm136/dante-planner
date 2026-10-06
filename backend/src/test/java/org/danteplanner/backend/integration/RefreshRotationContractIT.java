package org.danteplanner.backend.integration;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;

import org.danteplanner.backend.auth.token.RefreshRotationService;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.support.RefreshRotationContract;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.TestJwt;

@Tag("containerized")
class RefreshRotationContractIT implements RefreshRotationContract {

    private static StringRedisTemplate authRedis;

    private final TestJwt jwt = new TestJwt();

    @BeforeAll
    static void connect() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                SharedRedisContainerSupport.host(), SharedRedisContainerSupport.port()));
        factory.afterPropertiesSet();
        authRedis = new StringRedisTemplate(factory);
        authRedis.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        ((LettuceConnectionFactory) authRedis.getRequiredConnectionFactory()).destroy();
    }

    @Override
    public TestJwt jwt() {
        return jwt;
    }

    @Override
    public RefreshRotationService rotation(boolean legacyAdmitEnabled, long retryReuseWindowMs) {
        return new RefreshRotationService(authRedis, jwt.service(), jwt.service(), jwt.properties(),
                new SimpleMeterRegistry(), legacyAdmitEnabled, retryReuseWindowMs);
    }

    @Override
    public TokenBlacklistService blacklist() {
        return new TokenBlacklistService(authRedis, authRedis, new SimpleMeterRegistry(),
                jwt.properties().getRefreshTokenExpiry());
    }

    @Override
    public Long freshUserId() {
        return TestDataFactory.nextUserId();
    }
}
