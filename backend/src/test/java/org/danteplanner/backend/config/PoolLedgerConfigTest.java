package org.danteplanner.backend.config;

import org.danteplanner.backend.shared.config.PoolLedger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every connection that reaches the shared primary, summed across both regions at the maximum pod
 * count, stays within the primary's connection limit minus the reserve.
 */
class PoolLedgerConfigTest {

    @Test
    void poolLedger_WhenEveryPrimaryConnectionSummedAcrossMaxPods_StaysWithinConnectionBudget() {
        int oregonPerPod = PoolLedger.OREGON_PRIMARY_POOL;
        int seoulPerPod = PoolLedger.SEOUL_PRIMARY_POOL
                + PoolLedger.SEOUL_PRIMARY_READ_POOL
                + PoolLedger.BULKHEAD_POOL;
        int probes = PoolLedger.PROBE_CONNECTIONS_PER_POD * PoolLedger.MAX_PODS_PER_ASG * PoolLedger.REGIONS;
        int primaryHittingLoad = (oregonPerPod + seoulPerPod) * PoolLedger.MAX_PODS_PER_ASG + probes;
        int usableConnections =
                PoolLedger.RDS_MICRO_MAX_CONNECTIONS - PoolLedger.CONNECTION_RESERVE;

        assertThat(primaryHittingLoad).isLessThanOrEqualTo(usableConnections);
    }

    @Test
    void poolLedger_WhenRead_MatchesBindingPoolSizes() {
        assertThat(PoolLedger.OREGON_PRIMARY_POOL).isEqualTo(6);
        assertThat(PoolLedger.SEOUL_PRIMARY_POOL).isEqualTo(6);
        assertThat(PoolLedger.SEOUL_REPLICA_POOL).isEqualTo(6);
        assertThat(PoolLedger.READ_FALLBACK_POOL).isEqualTo(6);
        assertThat(PoolLedger.SEOUL_PRIMARY_READ_POOL).isEqualTo(3);
    }

    @Test
    void poolLedger_WhenReadBulkhead_MatchesBindingPoolSize() {
        assertThat(PoolLedger.BULKHEAD_POOL).isEqualTo(2);
    }
}
