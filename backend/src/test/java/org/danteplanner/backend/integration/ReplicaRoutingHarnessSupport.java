package org.danteplanner.backend.integration;

import java.sql.SQLException;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.zaxxer.hikari.HikariDataSource;

abstract class ReplicaRoutingHarnessSupport extends CausalHarnessSupport {

    @DynamicPropertySource
    static void routingProperties(DynamicPropertyRegistry registry) {
        registry.add("datasource.routing.enabled", () -> "true");
        registry.add("datasource.read-sources[0].name", () -> "seoul-replica");
        registry.add("datasource.read-sources[0].url", REPLICA::getJdbcUrl);
        registry.add("datasource.read-sources[0].username", REPLICA::getUsername);
        registry.add("datasource.read-sources[0].password", REPLICA::getPassword);
        registry.add("datasource.read-sources[1].name", () -> "primary");
    }

    static boolean lendsAConnection(HikariDataSource pool) {
        try {
            pool.getConnection().close();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }
}
