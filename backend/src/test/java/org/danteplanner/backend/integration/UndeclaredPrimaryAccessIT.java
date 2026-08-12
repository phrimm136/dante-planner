package org.danteplanner.backend.integration;

import javax.sql.DataSource;

import org.danteplanner.backend.config.TestConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A primary connection acquired with no transaction active is undeclared, and the pod boots strict
 * here: the acquisition is rejected rather than counted. Boot-time acquisitions are exempt by the
 * context having started at all — the guard arms at {@code ApplicationReadyEvent}, after Flyway and
 * schema validation have run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class UndeclaredPrimaryAccessIT extends SharedMySqlContainerSupport {

    private static final String PROBE_QUERY = "SELECT 1";

    @DynamicPropertySource
    static void routingProperties(DynamicPropertyRegistry registry) {
        String url = registerSharedMysql(registry);
        registry.add("datasource.routing.enabled", () -> "true");
        registry.add("datasource.replica.enabled", () -> "true");
        registry.add("datasource.replica.url", () -> url);
        registry.add("datasource.replica.username", MYSQL::getUsername);
        registry.add("datasource.replica.password", MYSQL::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Test
    void primaryAcquisition_WhenNoTransactionIsActive_IsRejected() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThatThrownBy(() -> jdbcTemplate.queryForObject(PROBE_QUERY, Integer.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside a transaction");
    }

    @Test
    void primaryAcquisition_WhenInsideAWriteTransaction_IsServed() {
        assertThat(probeInTransaction(false)).isEqualTo(1);
    }

    @Test
    void replicaAcquisition_WhenInsideAReadOnlyTransaction_IsServed() {
        assertThat(probeInTransaction(true)).isEqualTo(1);
    }

    private Integer probeInTransaction(boolean readOnly) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transactionTemplate.setReadOnly(readOnly);
        return transactionTemplate.execute(status ->
                new JdbcTemplate(dataSource).queryForObject(PROBE_QUERY, Integer.class));
    }
}
