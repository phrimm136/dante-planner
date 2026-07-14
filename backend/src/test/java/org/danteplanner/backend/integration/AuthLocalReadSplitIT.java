package org.danteplanner.backend.integration;

import java.time.Duration;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.redis.testcontainers.RedisContainer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.shared.readpath.ContentTombstoneStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance test for the auth-Redis read-local / write-global split.
 *
 * <p>Blacklist writes ({@code blacklistToken}, {@code invalidateUserTokens}) land on the
 * {@code @Primary} <em>auth</em> store; their reads ({@code isBlacklisted},
 * {@code isUserTokenInvalidated}) are served from the <em>auth-local</em> replica. A value
 * written through the service is present on auth yet invisible via the read path until the
 * same key also exists on auth-local ("caught up").</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(TestConfig.class)
@Tag("containerized")
class AuthLocalReadSplitIT {

    private static final String REDIS_IMAGE = "redis:7-alpine";

    static final RedisContainer AUTH_REDIS = new RedisContainer(REDIS_IMAGE);
    static final RedisContainer AUTH_LOCAL_REDIS = new RedisContainer(REDIS_IMAGE);
    static final RedisContainer DEAD_AUTH_LOCAL_REDIS = new RedisContainer(REDIS_IMAGE);

    static String deadHost;
    static int deadPort;

    static {
        AUTH_REDIS.start();
        AUTH_LOCAL_REDIS.start();
        DEAD_AUTH_LOCAL_REDIS.start();
        deadHost = DEAD_AUTH_LOCAL_REDIS.getRedisHost();
        deadPort = DEAD_AUTH_LOCAL_REDIS.getRedisPort();
        DEAD_AUTH_LOCAL_REDIS.stop();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("redis.auth.host", AUTH_REDIS::getRedisHost);
        registry.add("redis.auth.port", AUTH_REDIS::getRedisPort);
        registry.add("redis.auth-local.host", AUTH_LOCAL_REDIS::getRedisHost);
        registry.add("redis.auth-local.port", AUTH_LOCAL_REDIS::getRedisPort);
    }

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ContentTombstoneStore contentTombstoneStore;

    private StringRedisTemplate authLocalTemplate;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(
                        AUTH_LOCAL_REDIS.getRedisHost(), AUTH_LOCAL_REDIS.getRedisPort()));
        factory.afterPropertiesSet();
        authLocalTemplate = new StringRedisTemplate(factory);
        authLocalTemplate.afterPropertiesSet();

        tokenBlacklistService.clear();

        Set<String> tombstoneKeys = stringRedisTemplate.keys("del:*");
        if (tombstoneKeys != null && !tombstoneKeys.isEmpty()) {
            stringRedisTemplate.delete(tombstoneKeys);
        }

        Set<String> localKeys = authLocalTemplate.keys("*");
        if (localKeys != null && !localKeys.isEmpty()) {
            authLocalTemplate.delete(localKeys);
        }
    }

    private void mirror(String pattern) {
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                authLocalTemplate.opsForValue().set(key, value);
            }
        }
    }

    @Test
    @DisplayName("Blacklist bl: and uinv: writes land on auth but are invisible via the read path until auth-local catches up")
    void blacklistReadWriteSplit_bl_and_uinv() {
        // bl: half
        String token = "phase14a-access-token";
        Date futureExpiry = new Date(System.currentTimeMillis() + Duration.ofHours(1).toMillis());

        tokenBlacklistService.blacklistToken(token, futureExpiry);

        assertThat(stringRedisTemplate.keys("bl:*"))
                .as("blacklist write landed on the auth (write/global) store")
                .isNotNull()
                .isNotEmpty();

        assertThat(tokenBlacklistService.isBlacklisted(token))
                .as("auth-local has not caught up: the read path must not yet see the blacklisted token")
                .isFalse();

        mirror("bl:*");

        assertThat(tokenBlacklistService.isBlacklisted(token))
                .as("after auth-local catches up, the read path reflects the blacklist")
                .isTrue();

        // uinv: half
        long userId = 4242L;

        tokenBlacklistService.invalidateUserTokens(userId);

        assertThat(stringRedisTemplate.keys("uinv:*"))
                .as("user-invalidation write landed on the auth (write/global) store")
                .isNotNull()
                .isNotEmpty();

        assertThat(tokenBlacklistService.isUserTokenInvalidated(userId, 0L))
                .as("auth-local has not caught up: the read path must not yet see the user invalidation")
                .isFalse();

        mirror("uinv:*");

        assertThat(tokenBlacklistService.isUserTokenInvalidated(userId, 0L))
                .as("after auth-local catches up, the read path reflects the user invalidation")
                .isTrue();
    }

    @Test
    @DisplayName("Tombstone del: write lands on auth but is invisible via the read path until auth-local catches up")
    void tombstoneRead_whenAuthLocalStale_returnsFalseUntilCaughtUp() {
        UUID id = UUID.randomUUID();

        contentTombstoneStore.writeTombstone("planner", id);

        assertThat(stringRedisTemplate.keys("del:*"))
                .as("tombstone write landed on the auth (write/global) store")
                .isNotNull()
                .isNotEmpty();

        assertThat(contentTombstoneStore.isTombstoned("planner", id))
                .as("auth-local has not caught up: the read path must not yet see the tombstone")
                .isFalse();

        mirror("del:*");

        assertThat(contentTombstoneStore.isTombstoned("planner", id))
                .as("after auth-local catches up, the read path reflects the tombstone")
                .isTrue();
    }

    @Test
    @DisplayName("Both read paths fail open (no exception) when auth-local is unreachable")
    void reads_whenAuthLocalUnreachable_failOpen() {
        LettuceConnectionFactory deadFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(deadHost, deadPort));
        deadFactory.afterPropertiesSet();
        StringRedisTemplate deadTemplate = new StringRedisTemplate(deadFactory);
        deadTemplate.afterPropertiesSet();

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TokenBlacklistService blSvc =
                new TokenBlacklistService(stringRedisTemplate, deadTemplate, registry);
        ContentTombstoneStore tsSvc =
                new ContentTombstoneStore(stringRedisTemplate, deadTemplate);

        String token = "phase14a-failopen-token";
        Date futureExpiry = new Date(System.currentTimeMillis() + Duration.ofHours(1).toMillis());
        blSvc.blacklistToken(token, futureExpiry);

        assertThat(blSvc.isBlacklisted(token))
                .as("fail-open: read routed to the unreachable auth-local, not the auth write store")
                .isFalse();

        assertThat(registry.get("blacklist_check_skipped_total").counter().count())
                .as("fail-open increments the skip counter")
                .isGreaterThanOrEqualTo(1.0);

        UUID id = UUID.randomUUID();
        tsSvc.writeTombstone("planner", id);

        assertThat(tsSvc.isTombstoned("planner", id))
                .as("fail-open: tombstone read routed to the unreachable auth-local, not the auth write store")
                .isFalse();
    }
}
