package org.danteplanner.backend.config;

import org.danteplanner.backend.shared.config.PoolLedger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance test for INV9 — the pool-ledger invariant. The connection pools that hit the shared
 * Oregon primary (Oregon primary pool + Seoul write pool), summed across the max pods per ASG, must
 * stay within the db.t4g.micro connection budget minus the reserve. Fed by the production
 * {@link PoolLedger} constants the datasource config itself reads, so the invariant and production
 * share one source of truth.
 */
class PoolLedgerConfigTest {

    // INV9
    @Test
    void poolLedger_WhenSummedAcrossMaxPods_StaysWithinConnectionBudget() {
        int primaryHittingLoad =
                (PoolLedger.OREGON_PRIMARY_POOL + PoolLedger.SEOUL_PRIMARY_POOL)
                        * PoolLedger.MAX_PODS_PER_ASG;
        int usableConnections =
                PoolLedger.RDS_MICRO_MAX_CONNECTIONS - PoolLedger.CONNECTION_RESERVE;

        assertThat(primaryHittingLoad).isLessThanOrEqualTo(usableConnections);
    }

    // mechanics §6
    @Test
    void poolLedger_WhenRead_MatchesBindingPoolSizes() {
        assertThat(PoolLedger.OREGON_PRIMARY_POOL).isEqualTo(15);
        assertThat(PoolLedger.SEOUL_PRIMARY_POOL).isEqualTo(10);
        assertThat(PoolLedger.SEOUL_REPLICA_POOL).isEqualTo(15);
    }

    // INV9
    @Test
    void poolLedger_WhenBulkheadIncluded_StaysWithinConnectionBudget() {
        int primaryHittingLoad =
                (PoolLedger.OREGON_PRIMARY_POOL
                                + PoolLedger.SEOUL_PRIMARY_POOL
                                + PoolLedger.BULKHEAD_POOL)
                        * PoolLedger.MAX_PODS_PER_ASG;
        int usableConnections =
                PoolLedger.RDS_MICRO_MAX_CONNECTIONS - PoolLedger.CONNECTION_RESERVE;

        assertThat(primaryHittingLoad).isLessThanOrEqualTo(usableConnections);
    }

    // mechanics §6
    @Test
    void poolLedger_WhenReadBulkhead_MatchesBindingPoolSize() {
        assertThat(PoolLedger.BULKHEAD_POOL).isEqualTo(3);
    }
}
