package org.danteplanner.backend.shared.config;

/**
 * The datasource routing targets. {@link #PRIMARY} and {@link #REPLICA} are selected by the
 * transaction read-only flag; {@link #BULKHEAD} is a primary-hitting isolation pool reached only
 * via an explicit routing override during a replica-miss re-check.
 */
public enum RoutingKey {
    PRIMARY,
    REPLICA,
    BULKHEAD
}
