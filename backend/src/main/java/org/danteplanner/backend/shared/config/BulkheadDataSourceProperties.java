package org.danteplanner.backend.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Holds the endpoint of the Seoul-only re-check bulkhead pool.
 *
 * <p>The bulkhead is primary-hitting: when {@code url} is blank it defaults to the primary
 * {@code DataSourceProperties} endpoint, so a Seoul pod re-checks its own primary. A distinct
 * endpoint (e.g. a proxied primary for fault-injection tests) can be pointed at without touching
 * the write path.</p>
 */
@ConfigurationProperties(prefix = "datasource.bulkhead")
@Getter
@Setter
public class BulkheadDataSourceProperties {

    private String url;
    private String username;
    private String password;
}
