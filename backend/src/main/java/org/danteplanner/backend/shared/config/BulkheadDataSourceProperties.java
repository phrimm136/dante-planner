package org.danteplanner.backend.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "datasource.bulkhead")
@Getter
@Setter
public class BulkheadDataSourceProperties {

    private String url;
    private String username;
    private String password;

    private long connectionTimeout = TimeoutHierarchy.BULKHEAD_ACQUIRE_TIMEOUT_MS;
}
