package org.danteplanner.backend.shared.config;

public final class PoolLedger {

    /** Hikari pool names, carried as the {@code pool} tag on every {@code hikaricp_*} meter. */
    public static final String PRIMARY_POOL_NAME = "primary";
    public static final String BULKHEAD_POOL_NAME = "bulkhead";
    public static final String PRIMARY_READ_POOL_NAME = "primary-read";

    public static final int OREGON_PRIMARY_POOL = 6;
    public static final int SEOUL_PRIMARY_POOL = 6;
    public static final int SEOUL_REPLICA_POOL = 6;
    public static final int READ_FALLBACK_POOL = 6;
    public static final int SEOUL_PRIMARY_READ_POOL = 3;

    public static final int BULKHEAD_POOL = 2;

    public static final int MAX_PODS_PER_ASG = 2;
    public static final int REGIONS = 2;
    public static final int PROBE_CONNECTIONS_PER_POD = 1;
    public static final int RDS_MICRO_MAX_CONNECTIONS = 60;
    public static final int CONNECTION_RESERVE = 10;

    private PoolLedger() {
    }
}
