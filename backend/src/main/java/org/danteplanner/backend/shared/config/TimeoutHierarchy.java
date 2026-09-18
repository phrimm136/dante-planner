package org.danteplanner.backend.shared.config;

/**
 * Tomcat bounds no synchronous request, and {@code spring.mvc.async.request-timeout} reaches only
 * async dispatches; an SSE emitter's own timeout overrides that global value.
 */
public final class TimeoutHierarchy {

    /** InnoDB row-lock wait, in seconds — the unit {@code innodb_lock_wait_timeout} takes. */
    public static final int DB_LOCK_WAIT_SECONDS = 10;

    public static final long DB_LOCK_WAIT_MS = DB_LOCK_WAIT_SECONDS * 1000L;

    public static final long JDBC_SOCKET_TIMEOUT_MS = 15_000L;

    public static final long POOL_ACQUIRE_TIMEOUT_MS = 5_000L;

    public static final long BULKHEAD_ACQUIRE_TIMEOUT_MS = 2_000L;

    public static final long REQUEST_BUDGET_MS = 30_000L;

    /**
     * bucket4j's wait on the rate-limit Redis future — strictly outside
     * {@link BoundedRedisConnections#COMMAND_TIMEOUT} so Lettuce's timer always fires first and a
     * stall surfaces as a {@code RedisException}, never bucket4j's own timeout type.
     */
    public static final long RATE_LIMIT_FUTURE_TIMEOUT_MS = 4_000L;

    /**
     * Tomcat's connector timeout also bounds a blocking response write.
     */
    public static final long TOMCAT_CONNECTION_TIMEOUT_MS = 8_000L;

    /** Connector/J's own spelling of the socket-read bound. */
    public static final String SOCKET_TIMEOUT_PROPERTY = "socketTimeout";

    public static final String LOCK_WAIT_INIT_SQL =
            "SET SESSION innodb_lock_wait_timeout = " + DB_LOCK_WAIT_SECONDS;

    private TimeoutHierarchy() {
    }
}
