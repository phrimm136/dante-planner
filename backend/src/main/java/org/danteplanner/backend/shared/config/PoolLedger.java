package org.danteplanner.backend.shared.config;

/**
 * The connection pool ledger from mechanics §6.
 * Single source of truth for both the INV9 config assertion and {@code RoutingDataSourceConfig} —
 * never copied. These sizes bound the load on the shared Oregon primary within the db.t4g.micro
 * connection budget minus the reserve.
 */
public final class PoolLedger {

    public static final int OREGON_PRIMARY_POOL = 15;
    public static final int SEOUL_PRIMARY_POOL = 10;
    public static final int SEOUL_REPLICA_POOL = 15;

    /** Seoul-only re-check bulkhead pool (primary-hitting); isolation not throughput. */
    public static final int BULKHEAD_POOL = 3;

    public static final int MAX_PODS_PER_ASG = 2;
    public static final int RDS_MICRO_MAX_CONNECTIONS = 85;
    public static final int CONNECTION_RESERVE = 10;

    private PoolLedger() {
        // Utility class - prevent instantiation
    }
}
