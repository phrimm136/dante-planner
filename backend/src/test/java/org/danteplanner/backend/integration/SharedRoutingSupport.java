package org.danteplanner.backend.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

abstract class SharedRoutingSupport extends SharedMySqlContainerSupport {

    @DynamicPropertySource
    static void routingProperties(DynamicPropertyRegistry registry) {
        String url = registerSharedMysql(registry);
        registry.add("datasource.routing.enabled", () -> "true");
        registry.add("datasource.read-sources[0].name", () -> "seoul-replica");
        registry.add("datasource.read-sources[0].url", () -> url);
        registry.add("datasource.read-sources[0].username", MYSQL::getUsername);
        registry.add("datasource.read-sources[0].password", MYSQL::getPassword);
        registry.add("datasource.read-sources[1].name", () -> "primary");
        registry.add("management.endpoints.web.exposure.include", () -> "health,prometheus");
        registry.add("management.prometheus.metrics.export.enabled", () -> "true");
    }
}
