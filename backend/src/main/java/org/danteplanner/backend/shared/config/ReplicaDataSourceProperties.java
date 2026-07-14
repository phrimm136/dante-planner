package org.danteplanner.backend.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Holds the Seoul-local read replica datasource endpoint.
 *
 * <p>{@code enabled} gates whether a replica pool exists at all: {@code true} on Seoul pods
 * (which read from the local replica), {@code false} on Oregon pods (no replica pool — reads
 * fall through to the shared primary).</p>
 */
@ConfigurationProperties(prefix = "datasource.replica")
@Getter
@Setter
public class ReplicaDataSourceProperties {

    private boolean enabled;
    private String url;
    private String username;
    private String password;
}
