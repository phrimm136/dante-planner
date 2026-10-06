package org.danteplanner.backend.auth.token;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.integration.SharedMySqlContainerSupport;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.api.SessionEndHandler;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class AccountDeletionSessionEndIT extends SharedMySqlContainerSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserAccountLifecycleService lifecycleService;
    @Autowired private SessionEndHandler sessionEndHandler;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private StringRedisTemplate stringRedisTemplate;

    @Test
    void endSession_WhenTokenIssuedInTheCutoffSecond_RejectsItAsBlacklisted() throws Exception {
        User user = TestDataFactory.createTestUser(userRepository, "same-second-cutoff@example.com");
        lifecycleService.deleteAccount(user.getId());
        long cutoff = Long.parseLong(stringRedisTemplate.opsForValue()
                .get(TokenBlacklistService.USER_INVALIDATION_KEY_PREFIX + user.getId()));
        String sameSecondToken = new JwtTokenService(
                jwtProperties, Clock.fixed(Instant.ofEpochMilli(cutoff), ZoneOffset.UTC))
                .generateAccessToken(user.getId(), UserRole.NORMAL);

        mockMvc.perform(get("/api/auth/me").cookie(AuthCookies.accessToken(sameSecondToken)))
                .andExpect(status().isOk());

        sessionEndHandler.endSession(sameSecondToken, null);

        mockMvc.perform(get("/api/auth/me").cookie(AuthCookies.accessToken(sameSecondToken)))
                .andExpect(status().isNoContent());
    }
}
