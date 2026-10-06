package org.danteplanner.backend.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

abstract class SharedRoutingSupport extends SharedMySqlContainerSupport {

    @DynamicPropertySource
    static void routingProperties(DynamicPropertyRegistry registry) {
        String url = registerSharedMysql(registry);
        registry.add("datasource.routing.enabled", () -> "true");
        registry.add("datasource.replica.enabled", () -> "true");
        registry.add("datasource.replica.url", () -> url);
        registry.add("datasource.replica.username", MYSQL::getUsername);
        registry.add("datasource.replica.password", MYSQL::getPassword);
        registry.add("management.endpoints.web.exposure.include", () -> "health,prometheus");
        registry.add("management.prometheus.metrics.export.enabled", () -> "true");
    }
}
