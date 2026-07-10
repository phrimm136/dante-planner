package org.danteplanner.backend.shared.config;

/**
 * The two datasource routing targets keyed by the transaction read-only flag.
 */
public enum RoutingKey {
    PRIMARY,
    REPLICA
}
