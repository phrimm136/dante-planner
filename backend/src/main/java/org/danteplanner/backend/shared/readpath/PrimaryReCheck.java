package org.danteplanner.backend.shared.readpath;

import java.util.UUID;
import java.util.function.Supplier;

import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.exception.EntityNotFoundException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class PrimaryReCheck {

    static final String PROMOTED_COUNTER = "replica_miss_promoted_total";

    private final Counter promotedCounter;
    private final ContentTombstoneStore tombstoneStore;

    public PrimaryReCheck(MeterRegistry meterRegistry, ContentTombstoneStore tombstoneStore) {
        this.promotedCounter = Counter.builder(PROMOTED_COUNTER).register(meterRegistry);
        this.tombstoneStore = tombstoneStore;
    }

    public <T> T readWithReCheck(String entityType, UUID id, Supplier<T> dereference) {
        T hit;
        try {
            hit = dereference.get();
        } catch (EntityNotFoundException miss) {
            ReadOnlyRoutingDataSource.pinTo(RoutingKey.BULKHEAD);
            try {
                T promoted = dereference.get();
                promotedCounter.increment();
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
