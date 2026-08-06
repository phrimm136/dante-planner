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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
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
@Import({TestConfig.class, AccountDeletionGuardIT.UnreachableRevocationConfig.class})
class AccountDeletionGuardIT extends SharedMySqlContainerSupport {

    @TestConfiguration
    static class UnreachableRevocationConfig {
        @Bean
        @Primary
        TokenBlacklistService tokenBlacklistService() {
            return Mockito.mock(TokenBlacklistService.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private User user;
    private Cookie accessToken;

    @BeforeEach
    void setUp() {
        Mockito.reset(tokenBlacklistService);
        user = TestDataFactory.createTestUser(userRepository, "deletion-guard@example.com");
        accessToken = AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, user));
    }

    @Test
    @DisplayName("Redis outage aborts account deletion")
    void redisOutageAbortsAccountDeletion_WhenTokenRevocationThrows_RollsBackAndDegrades() throws Exception {
        doThrow(new RedisConnectionFailureException("token store unreachable"))
                .when(tokenBlacklistService).invalidateUserTokens(anyLong());

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
