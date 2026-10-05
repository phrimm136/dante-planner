package org.danteplanner.backend.integration;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Account deletion's token revocation is a guard, and a guard runs before the commit it protects.
 *
 * <p>Auth is token-only: the request filter does no per-request lookup of the user row, so a
 * deleted account keeps authenticating until its tokens are revoked. Revoking after the commit
 * would leave a window where a deleted account is still usable, and a crash inside that window
 * leaves nothing durable to retry from. Ordered before the commit instead, an unreachable Redis
 * costs the deletion rather than the revocation.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class AccountDeletionGuardIT extends SharedMySqlContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @TestBean(name = "tokenBlacklistService")
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User user;
    private Cookie accessToken;

    static TokenBlacklistService tokenBlacklistService() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                SharedRedisContainerSupport.host(), SharedRedisContainerSupport.port()));
        factory.afterPropertiesSet();
        return new RevocationFailsBlacklist(factory, new StringRedisTemplate(factory), new SimpleMeterRegistry(),
                TokenBlacklistService.DEFAULT_REFRESH_TOKEN_EXPIRY_MS);
    }

    static final class RevocationFailsBlacklist extends TokenBlacklistService
            implements ApplicationListener<ContextClosedEvent> {

        private final LettuceConnectionFactory connectionFactory;

        RevocationFailsBlacklist(LettuceConnectionFactory connectionFactory, StringRedisTemplate template,
                SimpleMeterRegistry meterRegistry, long refreshTokenExpiry) {
            super(template, template, meterRegistry, refreshTokenExpiry);
            this.connectionFactory = connectionFactory;
        }

        @Override
        public void invalidateUserTokens(Long userId) {
            throw new RedisConnectionFailureException("token store unreachable");
        }

        @Override
        public void onApplicationEvent(ContextClosedEvent event) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        user = TestDataFactory.createTestUser(userRepository, "deletion-guard@example.com");
        accessToken = AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, user));
    }

    @Test
    void redisOutageAbortsAccountDeletion_WhenTokenRevocationThrows_RollsBackAndDegrades() throws Exception {
        mockMvc.perform(delete("/api/user/me").with(withCsrf()).cookie(accessToken))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_TEMPORARILY_UNAVAILABLE"));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(reloaded.isDeleted())
                .as("a deletion whose revocation failed must not survive, or the account is gone "
                        + "while its tokens still work")
                .isFalse();
        assertThat(reloaded.getPermanentDeleteScheduledAt()).isNull();
    }
}
