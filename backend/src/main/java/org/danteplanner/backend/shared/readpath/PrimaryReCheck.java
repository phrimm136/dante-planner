package org.danteplanner.backend.shared.readpath;

import java.util.UUID;
import java.util.function.Supplier;

import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.exception.EntityNotFoundException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Re-checks the primary when a read-only by-id dereference misses on the stale local replica.
 *
 * <p>Exists only on Seoul pods (routing + replica enabled). The re-check pins the current thread to
 * the {@link RoutingKey#BULKHEAD} pool and re-runs the same dereference: the fresh read-only
 * transaction routes to the primary through the isolated bulkhead. A hit promotes the miss and
 * increments {@code replica_miss_promoted_total}; a still-missing primary re-throws the original
 * {@link EntityNotFoundException} (404).</p>
 *
 * <p>A replica-hit positive is additionally gated by the tombstone store: a just-deleted entity
 * still present on the stale replica surfaces its {@code del:<type>:<id>} marker and is masked as a
 * 404. The tombstone throw does not trigger a primary re-check — a promoted primary read already
 * reflects the delete, so only the replica-hit branch is checked.</p>
 */
public class PrimaryReCheck {

    static final String PROMOTED_COUNTER = "replica_miss_promoted_total";

    private final MeterRegistry meterRegistry;
    private final ContentTombstoneStore tombstoneStore;

    public PrimaryReCheck(MeterRegistry meterRegistry, ContentTombstoneStore tombstoneStore) {
        this.meterRegistry = meterRegistry;
        this.tombstoneStore = tombstoneStore;
    }

    /**
     * Runs the dereference against the replica; on a miss, re-checks the primary via the bulkhead;
     * on a replica-hit positive, masks a tombstoned entity as a 404.
     *
     * @param entityType  the entity type prefix (e.g. "planner")
     * @param id          the entity id
     * @param dereference the side-effect-free by-id dereference (safe to invoke twice)
     * @param <T>         the dereferenced entity type
     * @return the entity, from the replica or the promoted primary re-check
     * @throws EntityNotFoundException if the entity is absent on both the replica and the primary,
     *                                 or if the replica-hit positive carries a tombstone
     */
    public <T> T readWithReCheck(String entityType, UUID id, Supplier<T> dereference) {
        T hit;
        try {
            hit = dereference.get();
        } catch (EntityNotFoundException miss) {
            ReadOnlyRoutingDataSource.pinTo(RoutingKey.BULKHEAD);
            try {
                T promoted = dereference.get();
                Counter.builder(PROMOTED_COUNTER).register(meterRegistry).increment();
                return promoted;
            } finally {
                ReadOnlyRoutingDataSource.clear();
            }
        }
        if (tombstoneStore.isTombstoned(entityType, id)) {
            throw new EntityNotFoundException(entityType, id);
        }
        return hit;
    }
}
