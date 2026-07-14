package org.danteplanner.backend.integration;

import javax.sql.DataSource;
import org.danteplanner.backend.config.TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase-2 scenario test: the app's {@code @Primary} routing datasource routes by the transaction
 * read-only flag against the real replicating primary/replica pair.
 *
 * <p>With replication paused, a read inside a read-only transaction must observe the STALE replica
 * value while a read inside a read-write transaction observes the FRESH primary value. Writes go
 * through the admin {@code primaryJdbcTemplate} straight to the primary container; the routing
 * datasource is exercised only by the two reads. Enables routing for this context alone via a
 * subclass {@link DynamicPropertySource}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class RoutingSeoulIT extends CausalHarnessSupport {

    private static final String REPLICATED_VALUE = "seoul-and-replica";
    private static final String PRIMARY_ONLY_VALUE = "primary-only";

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;

    @Autowired
    @Qualifier("replicaJdbcTemplate")
    private JdbcTemplate replicaJdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void routingProperties(DynamicPropertyRegistry registry) {
        registry.add("datasource.routing.enabled", () -> "true");
        registry.add("datasource.replica.enabled", () -> "true");
        registry.add("datasource.replica.url", REPLICA::getJdbcUrl);
        registry.add("datasource.replica.username", REPLICA::getUsername);
        registry.add("datasource.replica.password", REPLICA::getPassword);
    }

    @Test
    @DisplayName("readOnly tx routes to the stale replica; read-write tx routes to the fresh primary")
    void routing_readOnlyTxSeesStaleReplica_readWriteTxSeesFreshPrimary() {
        try {
            primaryJdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS routing_probe (id INT PRIMARY KEY, val VARCHAR(64))");
            primaryJdbcTemplate.update(
                    "INSERT INTO routing_probe (id, val) VALUES (1, ?)", REPLICATED_VALUE);

            replicationControl.awaitCaughtUp();
            assertThat(replicaJdbcTemplate.queryForObject(
                    "SELECT val FROM routing_probe WHERE id = 1", String.class))
                    .isEqualTo(REPLICATED_VALUE);

            replicationControl.stopReplica();

            primaryJdbcTemplate.update(
                    "UPDATE routing_probe SET val = ? WHERE id = 1", PRIMARY_ONLY_VALUE);

            assertThat(readValViaRouting(true)).isEqualTo(REPLICATED_VALUE);
            assertThat(readValViaRouting(false)).isEqualTo(PRIMARY_ONLY_VALUE);
        } finally {
            replicationControl.startReplica();
            replicationControl.awaitCaughtUp();
        }
    }

    private String readValViaRouting(boolean readOnly) {
        TransactionTemplate tt = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        tt.setReadOnly(readOnly);
        return tt.execute(status ->
                new JdbcTemplate(dataSource).queryForObject(
                        "SELECT val FROM routing_probe WHERE id = 1", String.class));
    }
}
