package org.danteplanner.backend.config;

import org.danteplanner.backend.shared.config.PoolLedger;

import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("INV9: Σ(primary-hitting pools) × max pods ≤ max_connections − reserve")
    void poolLedger_whenSummedAcrossMaxPods_staysWithinConnectionBudget() {
        int primaryHittingLoad =
                (PoolLedger.OREGON_PRIMARY_POOL + PoolLedger.SEOUL_PRIMARY_POOL)
                        * PoolLedger.MAX_PODS_PER_ASG;
        int usableConnections =
                PoolLedger.RDS_MICRO_MAX_CONNECTIONS - PoolLedger.CONNECTION_RESERVE;

        assertThat(primaryHittingLoad).isLessThanOrEqualTo(usableConnections);
    }

    @Test
    @DisplayName("ledger values match mechanics §6 BINDING pool sizes")
    void poolLedger_whenRead_matchesBindingPoolSizes() {
        assertThat(PoolLedger.OREGON_PRIMARY_POOL).isEqualTo(15);
        assertThat(PoolLedger.SEOUL_PRIMARY_POOL).isEqualTo(10);
        assertThat(PoolLedger.SEOUL_REPLICA_POOL).isEqualTo(15);
    }

    @Test
    @DisplayName("INV9: Σ(primary-hitting pools including Seoul bulkhead) × max pods ≤ budget")
    void poolLedger_whenBulkheadIncluded_staysWithinConnectionBudget() {
        int primaryHittingLoad =
                (PoolLedger.OREGON_PRIMARY_POOL
                                + PoolLedger.SEOUL_PRIMARY_POOL
                                + PoolLedger.BULKHEAD_POOL)
                        * PoolLedger.MAX_PODS_PER_ASG;
        int usableConnections =
                PoolLedger.RDS_MICRO_MAX_CONNECTIONS - PoolLedger.CONNECTION_RESERVE;

        assertThat(primaryHittingLoad).isLessThanOrEqualTo(usableConnections);
    }

    @Test
    @DisplayName("bulkhead pool size matches mechanics §6 (isolation, not throughput)")
    void poolLedger_whenReadBulkhead_matchesBindingPoolSize() {
        assertThat(PoolLedger.BULKHEAD_POOL).isEqualTo(3);
    }
}
