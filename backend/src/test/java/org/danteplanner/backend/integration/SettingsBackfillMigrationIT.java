package org.danteplanner.backend.integration;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Settings backfill seam: the backfill migration gives every existing user a settings row, so a
 * legacy user that predates settings-at-creation is left with no missing settings row.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("it")
@Testcontainers
@Tag("containerized")
@Import(TestConfig.class)
class SettingsBackfillMigrationIT {

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
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        userRepository.findAll().stream()
                .filter(u -> u.getId() != 0L)
                .forEach(userRepository::delete);
    }

    private long usersWithoutSettings() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users u WHERE u.id <> 0 "
                        + "AND NOT EXISTS (SELECT 1 FROM user_settings s WHERE s.user_id = u.id)",
                Long.class);
        return count == null ? 0L : count;
    }

    @Test
    @DisplayName("backfill_WhenLegacyUsers_GainSettingsRows")
    void backfill_WhenLegacyUsers_GainSettingsRows() throws Exception {
        // A legacy user created directly (no settings row), as before settings-at-creation existed.
        TestDataFactory.createTestUser(userRepository, "legacy-no-settings@example.com");
        assertThat(usersWithoutSettings())
                .as("precondition: the legacy user has no settings row")
                .isGreaterThan(0L);

        Resource[] migrations = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*__backfill_user_settings.sql");
        assertThat(migrations)
                .as("the settings backfill migration must exist")
                .isNotEmpty();

        String sql = new String(migrations[0].getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String statement = Arrays.stream(sql.split("\n"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"))
                .trim();
        if (statement.endsWith(";")) {
            statement = statement.substring(0, statement.length() - 1);
        }
        jdbcTemplate.execute(statement);

        assertThat(usersWithoutSettings())
                .as("after the backfill migration, no user is left without a settings row")
                .isEqualTo(0L);
    }
}
