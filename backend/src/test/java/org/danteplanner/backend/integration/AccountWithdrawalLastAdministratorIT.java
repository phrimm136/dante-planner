package org.danteplanner.backend.integration;

import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.support.AuthCookies;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountWithdrawalLastAdministratorIT extends ExclusiveDatabaseSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("UPDATE users SET role = 'NORMAL' WHERE role = 'ADMIN'");
    }

    @Test
    void deleteMyAccount_WhenTheOnlyActiveAdminWithdraws_RefusesAndKeepsTheAccountAndSessions() throws Exception {
        User admin = TestDataFactory.createAdmin(userRepository, "last-admin-" + UUID.randomUUID() + "@example.com");
        Cookie accessToken = accessTokenOf(admin);

        mockMvc.perform(delete("/api/user/me").with(withCsrf()).cookie(accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMINISTRATOR"));

        User reloaded = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(reloaded.getDeletedAt()).isNull();
        assertThat(reloaded.getPermanentDeleteScheduledAt()).isNull();
        mockMvc.perform(get("/api/auth/me").cookie(accessToken))
                .andExpect(status().isOk());
    }

    @Test
    void deleteMyAccount_WhenAnotherActiveAdminRemains_SchedulesTheDeletion() throws Exception {
        User admin = TestDataFactory.createAdmin(userRepository, "leaving-admin-" + UUID.randomUUID() + "@example.com");
        TestDataFactory.createAdmin(userRepository, "staying-admin-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(delete("/api/user/me").with(withCsrf()).cookie(accessTokenOf(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account scheduled for deletion"));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void deleteMyAccount_WhenANormalUserWithdrawsWithNoAdminLeft_SchedulesTheDeletion() throws Exception {
        User user = TestDataFactory.createTestUser(userRepository, "normal-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(delete("/api/user/me").with(withCsrf()).cookie(accessTokenOf(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account scheduled for deletion"));

        assertThat(userRepository.findById(user.getId()).orElseThrow().isDeleted()).isTrue();
    }

    private Cookie accessTokenOf(User user) {
        return AuthCookies.accessToken(TestDataFactory.generateAccessToken(jwtTokenService, user));
    }
}
