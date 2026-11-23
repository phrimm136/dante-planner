package org.danteplanner.fixture.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "unvalidated-fixture")
public record UnvalidatedPropertiesFixture(int capacity) {
}
