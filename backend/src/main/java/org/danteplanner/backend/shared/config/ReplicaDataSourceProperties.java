package org.danteplanner.backend.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "datasource.replica")
@Getter
@Setter
public class ReplicaDataSourceProperties {

    private boolean enabled;
    private String url;
    private String username;
    private String password;
}
