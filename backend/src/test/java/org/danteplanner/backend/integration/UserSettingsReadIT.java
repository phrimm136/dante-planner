package org.danteplanner.backend.integration;

import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.repository.UserSettingsRepository;
import org.danteplanner.backend.support.TestDataFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Settings read seam: a GET on a missing settings row returns defaults with a 200 and writes
 * nothing (a defensive read, not a lazy insert), and a stored NULL sync_enabled is preserved
 * as JSON null rather than coerced to a boolean.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Testcontainers
@Tag("containerized")
@Import(TestConfig.class)
class UserSettingsReadIT {

    @Container
    static MySQLContainer mysqlContainer = new MySQLContainer("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand(
                    "--innodb-flush-log-at-trx-commit=0",
                    "--sync-binlog=0",
                    "--performance-schema=OFF",
                    "--skip-name-resolve");

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.flyway.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.flyway.user", mysqlContainer::getUsername);
        registry.add("spring.flyway.password", mysqlContainer::getPassword);
    }

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
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);
        user = TestDataFactory.createTestUser(userRepository, "settings@example.com");
        token = TestDataFactory.generateAccessToken(jwtTokenService, user);
    }

    private Cookie authCookie() {
        return new Cookie("accessToken", token);
    }

    @Test
    @DisplayName("settingsGet_WhenRowAbsent_YieldsDefaults")
    void settingsGet_WhenRowAbsent_YieldsDefaults() throws Exception {
        userSettingsRepository.findByUserId(user.getId()).ifPresent(userSettingsRepository::delete);

        mockMvc.perform(get("/api/user/settings").cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncEnabled").value(nullValue()))
                .andExpect(jsonPath("$.notifyComments").value(true))
                .andExpect(jsonPath("$.notifyRecommendations").value(true))
                .andExpect(jsonPath("$.notifyNewPublications").value(false));

        assertThat(userSettingsRepository.findByUserId(user.getId()))
                .as("a read of a missing settings row must not lazily persist one")
                .isEmpty();
    }

    @Test
    @DisplayName("settingsGet_WhenSyncNull_PreservesNull")
    void settingsGet_WhenSyncNull_PreservesNull() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO user_settings (user_id, sync_enabled, notify_comments, "
                        + "notify_recommendations, notify_new_publications) VALUES (?, NULL, true, true, false)",
                user.getId());

        mockMvc.perform(get("/api/user/settings").cookie(authCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncEnabled").value(nullValue()));
    }
}
