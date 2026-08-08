package org.danteplanner.backend.integration;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.repository.UserSettingsRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.danteplanner.backend.support.AuthCookies.performAuthed;
import static org.danteplanner.backend.support.CsrfMockMvcSupport.withCsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Settings read seam: a GET on a missing settings row returns defaults with a 200 and writes
 * nothing (a defensive read, not a lazy insert), and a row whose sync choice has not been made
 * reports sync off alongside the unanswered prompt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class UserSettingsReadIT extends SharedMySqlContainerSupport {


    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingsRepository userSettingsRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = TestDataFactory.createTestUser(userRepository, "settings@example.com");
        token = TestDataFactory.generateAccessToken(jwtTokenService, user);
    }

    @Test
    void settingsGet_WhenRowAbsent_YieldsDefaults() throws Exception {
        userSettingsRepository.findByUserId(user.getId()).ifPresent(userSettingsRepository::delete);

        performAuthed(mockMvc, get("/api/user/settings"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncEnabled").value(false))
                .andExpect(jsonPath("$.syncChoiceMade").value(false))
                .andExpect(jsonPath("$.notifyComments").value(true))
                .andExpect(jsonPath("$.notifyRecommendations").value(true))
                .andExpect(jsonPath("$.notifyNewPublications").value(false));

        assertThat(userSettingsRepository.findByUserId(user.getId()))
                .as("a read of a missing settings row must not lazily persist one")
                .isEmpty();
    }

    @Test
    void settingsGet_WhenChoiceNotMade_ReportsSyncOffAndPromptPending() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO user_settings (user_id, sync_enabled, sync_choice_made, notify_comments, "
                        + "notify_recommendations, notify_new_publications) "
                        + "VALUES (?, false, false, true, true, false)",
                user.getId());

        performAuthed(mockMvc, get("/api/user/settings"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncEnabled").value(false))
                .andExpect(jsonPath("$.syncChoiceMade").value(false));
    }

    @Test
    void settingsPut_WhenSyncChosen_MarksTheChoiceMade() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO user_settings (user_id, sync_enabled, sync_choice_made, notify_comments, "
                        + "notify_recommendations, notify_new_publications) "
                        + "VALUES (?, false, false, true, true, false)",
                user.getId());

        performAuthed(mockMvc, put("/api/user/settings").with(withCsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"syncEnabled\":true}"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncEnabled").value(true))
                .andExpect(jsonPath("$.syncChoiceMade").value(true));
    }
}
