package org.danteplanner.backend.shared.readpath;

import java.util.function.Supplier;

import org.danteplanner.backend.planner.exception.PlannerNotFoundException;
import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.RoutingKey;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Re-checks the primary when a read-only by-id dereference misses on the stale local replica.
 *
 * <p>Exists only on Seoul pods (routing + replica enabled). The re-check pins the current thread to
 * the {@link RoutingKey#BULKHEAD} pool and re-runs the same dereference: the fresh read-only
 * transaction routes to the primary through the isolated bulkhead. A hit promotes the miss and
 * increments {@code replica_miss_promoted_total}; a still-missing primary re-throws the original
 * {@link PlannerNotFoundException} (404).</p>
 */
public class PrimaryReCheck {

    static final String PROMOTED_COUNTER = "replica_miss_promoted_total";

    private final MeterRegistry meterRegistry;

    public PrimaryReCheck(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Runs the dereference against the replica; on a miss, re-checks the primary via the bulkhead.
     *
     * @param dereference the side-effect-free by-id dereference (safe to invoke twice)
     * @param <T>         the dereferenced entity type
     * @return the entity, from the replica or the promoted primary re-check
     * @throws PlannerNotFoundException if the entity is absent on both the replica and the primary
     */
    public <T> T readWithReCheck(Supplier<T> dereference) {
        try {
            return dereference.get();
        } catch (PlannerNotFoundException miss) {
            ReadOnlyRoutingDataSource.pinTo(RoutingKey.BULKHEAD);
            try {
                T promoted = dereference.get();
                Counter.builder(PROMOTED_COUNTER).register(meterRegistry).increment();
                return promoted;
            } finally {
                ReadOnlyRoutingDataSource.clear();
            }
        }
    }
}
