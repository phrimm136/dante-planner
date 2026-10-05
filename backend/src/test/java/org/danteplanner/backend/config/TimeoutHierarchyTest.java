package org.danteplanner.backend.config;

import org.danteplanner.backend.shared.config.TimeoutHierarchy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Invariant test for the timeout ledger — the waits must nest, so an inner wait always fires and
 * surfaces a typed failure before the wait containing it expires. Stated as relationships between
 * the constants rather than as literals, so retuning any single value still has to keep the
 * ordering.
 */
class TimeoutHierarchyTest {

    @Test
    void timeoutHierarchy_WhenNested_LockWaitExpiresBeforeSocketTimeout() {
        assertThat(TimeoutHierarchy.DB_LOCK_WAIT_MS)
                .isLessThan(TimeoutHierarchy.JDBC_SOCKET_TIMEOUT_MS);
    }

    @Test
    void timeoutHierarchy_WhenNested_SocketTimeoutExpiresBeforeRequestBudget() {
        assertThat(TimeoutHierarchy.JDBC_SOCKET_TIMEOUT_MS)
                .isLessThan(TimeoutHierarchy.REQUEST_BUDGET_MS);
    }

    @Test
    void timeoutHierarchy_WhenPoolsCompared_BulkheadAcquireIsShorterThanMainPoolAcquire() {
        assertThat(TimeoutHierarchy.BULKHEAD_ACQUIRE_TIMEOUT_MS)
                .isLessThan(TimeoutHierarchy.POOL_ACQUIRE_TIMEOUT_MS);
    }

    @Test
    void timeoutHierarchy_WhenNested_PoolAcquireExpiresBeforeRequestBudget() {
        assertThat(TimeoutHierarchy.POOL_ACQUIRE_TIMEOUT_MS)
                .isLessThan(TimeoutHierarchy.REQUEST_BUDGET_MS);
    }

    @Test
    void timeoutHierarchy_WhenNested_RedisCommandTimeoutExpiresBeforeRateLimitFutureWait() {
        assertThat(org.danteplanner.backend.shared.config.BoundedRedisConnections.COMMAND_TIMEOUT.toMillis())
                .isLessThan(TimeoutHierarchy.RATE_LIMIT_FUTURE_TIMEOUT_MS);
    }

    @Test
    void timeoutHierarchy_WhenNested_TomcatWriteBoundExpiresBeforeEveryHeartbeatSweep() {
        assertThat(TimeoutHierarchy.TOMCAT_CONNECTION_TIMEOUT_MS)
                .isLessThan(org.danteplanner.backend.shared.sse.SseConstants.USER_STREAM_HEARTBEAT_INTERVAL_MS)
                .isLessThan(org.danteplanner.backend.shared.sse.SseConstants.COMMENT_STREAM_HEARTBEAT_INTERVAL_MS);
    }

    @Test
    void timeoutHierarchy_WhenRead_MatchesRatifiedValues() {
        assertThat(TimeoutHierarchy.DB_LOCK_WAIT_SECONDS).isEqualTo(10);
        assertThat(TimeoutHierarchy.JDBC_SOCKET_TIMEOUT_MS).isEqualTo(15_000L);
        assertThat(TimeoutHierarchy.POOL_ACQUIRE_TIMEOUT_MS).isEqualTo(5_000L);
        assertThat(TimeoutHierarchy.BULKHEAD_ACQUIRE_TIMEOUT_MS).isEqualTo(2_000L);
        assertThat(TimeoutHierarchy.REQUEST_BUDGET_MS).isEqualTo(30_000L);
        assertThat(TimeoutHierarchy.RATE_LIMIT_FUTURE_TIMEOUT_MS).isEqualTo(4_000L);
    }

    @Test
    void lockWaitInitSql_WhenBuilt_CarriesTheLockWaitConstant() {
        assertThat(TimeoutHierarchy.LOCK_WAIT_INIT_SQL)
                .isEqualTo("SET SESSION innodb_lock_wait_timeout = "
                        + TimeoutHierarchy.DB_LOCK_WAIT_SECONDS);
    }

    @Test
    void lockWaitMillis_WhenRead_IsDerivedFromLockWaitSeconds() {
        assertThat(TimeoutHierarchy.DB_LOCK_WAIT_MS)
                .isEqualTo(TimeoutHierarchy.DB_LOCK_WAIT_SECONDS * 1000L);
    }
}
