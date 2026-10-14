package org.danteplanner.backend.config;

import org.danteplanner.backend.shared.config.PoolLedger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pool-ledger invariant: the connection pools that hit the shared
 * Oregon primary (Oregon primary pool + Seoul write pool), summed across the max pods per ASG, must
 * stay within the db.t4g.micro connection budget minus the reserve. Fed by the production
 * {@link PoolLedger} constants the datasource config itself reads, so the invariant and production
 * share one source of truth.
 */
class PoolLedgerConfigTest {

    @Test
    void poolLedger_WhenSummedAcrossMaxPods_StaysWithinConnectionBudget() {
        int primaryHittingLoad =
                (PoolLedger.OREGON_PRIMARY_POOL + PoolLedger.SEOUL_PRIMARY_POOL)
                        * PoolLedger.MAX_PODS_PER_ASG;
        int usableConnections =
                PoolLedger.RDS_MICRO_MAX_CONNECTIONS - PoolLedger.CONNECTION_RESERVE;

        assertThat(primaryHittingLoad).isLessThanOrEqualTo(usableConnections);
    }

    @Test
    void poolLedger_WhenRead_MatchesBindingPoolSizes() {
        assertThat(PoolLedger.OREGON_PRIMARY_POOL).isEqualTo(15);
        assertThat(PoolLedger.SEOUL_PRIMARY_POOL).isEqualTo(10);
        assertThat(PoolLedger.SEOUL_REPLICA_POOL).isEqualTo(15);
        assertThat(PoolLedger.READ_FALLBACK_POOL).isEqualTo(3);
        assertThat(PoolLedger.SEOUL_PRIMARY_READ_POOL).isEqualTo(6);
    }

    @Test
    void poolLedger_WhenBulkheadAndSeoulPrimaryReadPoolIncluded_StaysWithinConnectionBudget() {
        int primaryHittingLoad =
                (PoolLedger.OREGON_PRIMARY_POOL
                                + PoolLedger.SEOUL_PRIMARY_POOL
                                + PoolLedger.BULKHEAD_POOL
                                + PoolLedger.SEOUL_PRIMARY_READ_POOL)
                        * PoolLedger.MAX_PODS_PER_ASG;
        int usableConnections =
                PoolLedger.RDS_MICRO_MAX_CONNECTIONS - PoolLedger.CONNECTION_RESERVE;

        assertThat(primaryHittingLoad).isLessThanOrEqualTo(usableConnections);
    }

    @Test
    void poolLedger_WhenReadBulkhead_MatchesBindingPoolSize() {
        assertThat(PoolLedger.BULKHEAD_POOL).isEqualTo(3);
    }
}
