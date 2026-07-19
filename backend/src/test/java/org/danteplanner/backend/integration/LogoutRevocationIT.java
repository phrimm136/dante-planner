package org.danteplanner.backend.integration;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.redis.testcontainers.RedisContainer;
import io.lettuce.core.metrics.CommandLatencyRecorder;
import io.lettuce.core.protocol.ProtocolKeyword;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.auth.facade.AuthenticationFacade;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.user.entity.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Logout revocation seam. A logout with a valid access + refresh pair must blacklist both
 * tokens and revoke the refresh family in a SINGLE Redis round trip (one atomic script), and a
 * logout with no tokens must silently succeed (204) writing nothing.
 *
 * <p>Round trips are measured with a Lettuce {@link CommandLatencyRecorder} attached to the auth
 * store's client resources: it fires once per command sent over the wire, so commands executed
 * inside a server-side Lua script are not counted — only the outer {@code EVALSHA}. The
 * round-trip count and the blacklist/revocation state assertions together pin the atomic
 * single-script revocation.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("containerized")
@Import({TestConfig.class, LogoutRevocationIT.RoundTripCountingConfig.class})
class LogoutRevocationIT {

    private static final String REDIS_IMAGE = "redis:7-alpine";

    static final RedisContainer AUTH_REDIS = new RedisContainer(REDIS_IMAGE);
    static final RedisContainer AUTH_LOCAL_REDIS = new RedisContainer(REDIS_IMAGE);

    static final AtomicLong AUTH_COMMAND_COUNT = new AtomicLong();

    static {
        AUTH_REDIS.start();
        AUTH_LOCAL_REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("redis.auth.host", AUTH_REDIS::getRedisHost);
        registry.add("redis.auth.port", AUTH_REDIS::getRedisPort);
        registry.add("redis.auth-local.host", AUTH_LOCAL_REDIS::getRedisHost);
        registry.add("redis.auth-local.port", AUTH_LOCAL_REDIS::getRedisPort);
    }

    /**
     * Overrides the {@code @Primary} auth {@link StringRedisTemplate} with one whose Lettuce
     * client resources carry a command-counting latency recorder, so the test can observe how
     * many commands the logout path sends to the auth store over the wire.
     */
    @TestConfiguration
    static class RoundTripCountingConfig {

        @Bean
        @Primary
        StringRedisTemplate stringRedisTemplate() {
            CommandLatencyRecorder recorder = new CommandLatencyRecorder() {
                @Override
                public void recordCommandLatency(SocketAddress local, SocketAddress remote,
                        ProtocolKeyword commandType, long firstResponseLatency, long completionLatency) {
                    AUTH_COMMAND_COUNT.incrementAndGet();
                }
            };
            ClientResources resources = DefaultClientResources.builder()
                    .commandLatencyRecorder(recorder)
                    .build();
            LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                    .clientResources(resources)
                    .build();
            LettuceConnectionFactory factory = new LettuceConnectionFactory(
                    new RedisStandaloneConfiguration(
                            AUTH_REDIS.getRedisHost(), AUTH_REDIS.getRedisPort()),
                    clientConfig);
            factory.afterPropertiesSet();
            return new StringRedisTemplate(factory);
        }
    }

    @Autowired
    private AuthenticationFacade authFacade;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private StringRedisTemplate authStringRedisTemplate;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authStringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("logout_WhenLuaRevocation_RevokesAllThreeAtomically")
    void logout_WhenLuaRevocation_RevokesAllThreeAtomically() {
        long userId = 4242L;
        String familyId = "logout-family-42";
        String accessToken = jwtTokenService.generateAccessToken(userId, UserRole.NORMAL);
        String refreshToken = jwtTokenService.generateRefreshToken(userId, familyId, null);
        String familyKey = "rt:fam:{" + familyId + "}";

        // Instrument sanity: one wire command must register exactly one recorder callback.
        long beforeProbe = AUTH_COMMAND_COUNT.get();
        authStringRedisTemplate.opsForValue().set("roundtrip:probe", "1");
        assertThat(AUTH_COMMAND_COUNT.get() - beforeProbe)
                .as("command-latency recorder must count each wire round trip")
                .isEqualTo(1L);

        AUTH_COMMAND_COUNT.set(0L);
        authFacade.logout(accessToken, refreshToken);
        long roundTrips = AUTH_COMMAND_COUNT.get();

        assertThat(authStringRedisTemplate.keys("bl:*"))
                .as("access and refresh tokens are both blacklisted")
                .hasSize(2);
        assertThat(authStringRedisTemplate.opsForHash().hasKey(familyKey, "__revoked__"))
                .as("refresh token family is revoked")
                .isTrue();
        assertThat(roundTrips)
                .as("all three revocations land in a single atomic Redis round trip")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("logout_WhenTokensAbsent_SucceedsSilently")
    void logout_WhenTokensAbsent_SucceedsSilently() throws Exception {
        AUTH_COMMAND_COUNT.set(0L);

        mockMvc.perform(post("/api/auth/logout").with(withCsrf()))
                .andExpect(status().isNoContent());

        assertThat(AUTH_COMMAND_COUNT.get())
                .as("a logout with no tokens issues no auth-store writes")
                .isZero();
        assertThat(authStringRedisTemplate.keys("bl:*")).isEmpty();
    }
}
