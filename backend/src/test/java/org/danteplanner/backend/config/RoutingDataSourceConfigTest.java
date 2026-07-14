package org.danteplanner.backend.config;

import org.danteplanner.backend.shared.config.PoolLedger;
import org.danteplanner.backend.shared.config.ReplicaDataSourceProperties;
import org.danteplanner.backend.shared.config.RoutingDataSourceConfig;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

import com.zaxxer.hikari.HikariConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario test for requirements §6 / INV9 — the routing datasource config sizes its HikariCP pools
 * from the {@link PoolLedger} constants per region, reading the same ledger production reads rather
 * than copies. Exercises only the connection-free builder methods, never {@code dataSource()}.
 */
class RoutingDataSourceConfigTest {

    private static DataSourceProperties primaryProperties() {
        DataSourceProperties props = new DataSourceProperties();
        props.setUrl("jdbc:mysql://primary:3306/planner");
        props.setUsername("primaryUser");
        props.setPassword("primaryPw");
        return props;
    }

    private static ReplicaDataSourceProperties replicaProperties(boolean enabled) {
        ReplicaDataSourceProperties props = new ReplicaDataSourceProperties();
        props.setEnabled(enabled);
        props.setUrl("jdbc:mysql://replica:3306/planner");
        props.setUsername("replicaUser");
        props.setPassword("replicaPw");
        return props;
    }

    @Nested
    @DisplayName("Seoul pod (replica enabled)")
    class SeoulPod {

        private final RoutingDataSourceConfig config =
                new RoutingDataSourceConfig(primaryProperties(), replicaProperties(true));

        @Test
        void buildPrimaryHikariConfig_WhenReplicaEnabled_UsesSeoulPrimaryPool() {
            HikariConfig hikari = config.buildPrimaryHikariConfig();

            assertThat(hikari.getMaximumPoolSize()).isEqualTo(PoolLedger.SEOUL_PRIMARY_POOL);
        }

        @Test
        void buildReplicaHikariConfig_WhenReplicaEnabled_UsesSeoulReplicaPool() {
            HikariConfig hikari = config.buildReplicaHikariConfig();

            assertThat(hikari.getMaximumPoolSize()).isEqualTo(PoolLedger.SEOUL_REPLICA_POOL);
        }
    }

    @Nested
    @DisplayName("Oregon pod (replica disabled)")
    class OregonPod {

        private final RoutingDataSourceConfig config =
                new RoutingDataSourceConfig(primaryProperties(), replicaProperties(false));

        @Test
        void buildPrimaryHikariConfig_WhenReplicaDisabled_UsesOregonPrimaryPool() {
            HikariConfig hikari = config.buildPrimaryHikariConfig();

            assertThat(hikari.getMaximumPoolSize()).isEqualTo(PoolLedger.OREGON_PRIMARY_POOL);
        }
    }
}
