package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Observability seam: {@code replica_miss_promoted_total} is registered eagerly at boot,
 * so a freshly booted pod that has served zero promotions still exposes the series at value 0
 * on its first Prometheus scrape. Current {@link org.danteplanner.backend.shared.readpath.PrimaryReCheck}
 * registers the counter lazily on the first promotion, so the boot scrape omits it.
 *
 * <p>Enables routing + replica (pointed at the same container) so the {@code PrimaryReCheck}
 * bean instantiates; no promotion is ever triggered.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Testcontainers
@Tag("containerized")
@Import(TestConfig.class)
class MetricsRegistrationIT {

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
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        registry.add("spring.flyway.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.flyway.user", mysqlContainer::getUsername);
        registry.add("spring.flyway.password", mysqlContainer::getPassword);
        registry.add("datasource.routing.enabled", () -> "true");
        registry.add("datasource.replica.enabled", () -> "true");
        registry.add("datasource.replica.url", mysqlContainer::getJdbcUrl);
        registry.add("datasource.replica.username", mysqlContainer::getUsername);
        registry.add("datasource.replica.password", mysqlContainer::getPassword);
        registry.add("management.endpoints.web.exposure.include", () -> "health,prometheus");
        registry.add("management.prometheus.metrics.export.enabled", () -> "true");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("promotionCounter_WhenBooted_PresentOnScrape")
    void promotionCounter_WhenBooted_PresentOnScrape() {
        ResponseEntity<String> scrape = restTemplate.getForEntity("/actuator/prometheus", String.class);

        assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Micrometer renders a counter named x_total as either x_total or x_total_total depending
        // on the naming convention; accept either and require a zero-valued sample line.
        Pattern series = Pattern.compile(
                "^replica_miss_promoted_total(_total)?(\\{[^}]*})?\\s+0(\\.0)?\\s*$",
                Pattern.MULTILINE);
        assertThat(series.matcher(scrape.getBody()).find())
                .as("boot scrape must expose replica_miss_promoted_total at 0; body was:%n%s", scrape.getBody())
                .isTrue();
    }
}
