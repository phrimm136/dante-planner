package org.danteplanner.backend.integration;

import org.danteplanner.backend.config.TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase-1 acceptance test for the causal-consistency Testcontainers harness.
 *
 * <p>Pins the two external contracts the harness must satisfy before any downstream
 * INV suite can run:
 * <ol>
 *   <li>A primary/replica pair wired like a Seoul pod, whose replication can be paused
 *       and resumed through {@link ReplicationControl} (no timing constants — INV4);
 *       a write made while the replica is stopped is invisible on the replica-qualified
 *       datasource until {@code startReplica()} + {@code awaitCaughtUp()}.</li>
 *   <li>Two distinct {@link LettuceConnectionFactory} beans — auth Redis and the local
 *       ephemeral rate-limit Redis — injectable by distinct qualifiers.</li>
 * </ol>
 * Bootstrap (containers, GTID replication wiring, Toxiproxy profiles) lives in the
 * inherited harness base; this class asserts contract only.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Tag("containerized")
@Import(TestConfig.class)
class CausalHarnessIT extends CausalHarnessSupport {

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcTemplate primaryJdbcTemplate;

    @Autowired
    @Qualifier("replicaJdbcTemplate")
    private JdbcTemplate replicaJdbcTemplate;

    @Autowired
    @Qualifier("authRedisConnectionFactory")
    private LettuceConnectionFactory authRedisConnectionFactory;

    @Autowired
    @Qualifier("rateLimitRedisConnectionFactory")
    private LettuceConnectionFactory rateLimitRedisConnectionFactory;

    @Test
    @DisplayName("Replication round-trip: a write made while the replica is stopped is invisible until start+awaitCaughtUp")
    void replicationRoundTrip_writeWhileStopped_invisibleUntilResumedAndCaughtUp() {
        primaryJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS harness_probe (id INT PRIMARY KEY)");
        primaryJdbcTemplate.update("INSERT INTO harness_probe (id) VALUES (1)");
        replicationControl.awaitCaughtUp();
        assertThat(countOnReplica(1)).isEqualTo(1);

        replicationControl.stopReplica();
        primaryJdbcTemplate.update("INSERT INTO harness_probe (id) VALUES (2)");
        assertThat(countOnReplica(2)).isZero();

        replicationControl.startReplica();
        replicationControl.awaitCaughtUp();
        assertThat(countOnReplica(2)).isEqualTo(1);
    }

    @Test
    @DisplayName("Two distinct LettuceConnectionFactory beans are injectable by auth and rate-limit qualifiers")
    void redisFactories_authAndRateLimit_areDistinctInjectableBeans() {
        assertThat(authRedisConnectionFactory).isNotNull();
        assertThat(rateLimitRedisConnectionFactory).isNotNull();
        assertThat(authRedisConnectionFactory).isNotSameAs(rateLimitRedisConnectionFactory);
    }

    private int countOnReplica(int id) {
        Integer count = replicaJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM harness_probe WHERE id = ?", Integer.class, id);
        return count == null ? 0 : count;
    }
}
