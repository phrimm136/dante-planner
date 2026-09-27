package org.danteplanner.backend.shared.readpath;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.exception.EntityNotFoundException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class PrimaryReCheck {

    static final String PROMOTED_COUNTER = "replica_miss_promoted_total";
    static final String BULKHEAD_QUERIES_COUNTER = "replica_bulkhead_queries_total";

    private final Counter promotedCounter;
    private final Counter bulkheadQueriesCounter;
    private final ContentTombstoneStore tombstoneStore;

    public PrimaryReCheck(MeterRegistry meterRegistry, ContentTombstoneStore tombstoneStore) {
        this.promotedCounter = Counter.builder(PROMOTED_COUNTER).register(meterRegistry);
        this.bulkheadQueriesCounter = Counter.builder(BULKHEAD_QUERIES_COUNTER).register(meterRegistry);
        this.tombstoneStore = tombstoneStore;
    }

    public <T> T readWithReCheck(String entityType, UUID id, Supplier<T> dereference) {
        T hit;
        try {
            hit = dereference.get();
        } catch (EntityNotFoundException miss) {
            T promoted = onBulkhead(dereference);
            promotedCounter.increment();
            return promoted;
        }
        if (tombstoneStore.isTombstoned(entityType, id)) {
            throw new EntityNotFoundException(entityType, id);
        }
        return hit;
    }

    public <T> List<T> readAllWithReCheck(String entityType, Collection<UUID> ids,
            Function<Collection<UUID>, List<T>> dereference, Function<T, UUID> idOf) {
        List<T> hits = dereference.apply(ids);
        Set<UUID> hitIds = hits.stream().map(idOf).collect(Collectors.toSet());
        Set<UUID> tombstoned = tombstoneStore.tombstonedAmong(entityType, hitIds);
        List<T> served = new ArrayList<>(hits.stream()
                .filter(hit -> !tombstoned.contains(idOf.apply(hit)))
                .toList());
        List<UUID> misses = ids.stream().filter(id -> !hitIds.contains(id)).distinct().toList();
        if (misses.isEmpty()) {
            return served;
        }
        List<T> promoted = onBulkhead(() -> dereference.apply(misses));
        promotedCounter.increment(promoted.size());
        served.addAll(promoted);
        return served;
    }

    public <T> T reReadWhenTombstoned(String scope, UUID id, T hit, Supplier<T> dereference) {
        if (!tombstoneStore.isTombstoned(scope, id)) {
            return hit;
        }
        return onBulkhead(dereference);
    }

    private <T> T onBulkhead(Supplier<T> dereference) {
        bulkheadQueriesCounter.increment();
        ReadOnlyRoutingDataSource.pinTo(RoutingKey.BULKHEAD);
        try {
            return dereference.get();
        } finally {
            ReadOnlyRoutingDataSource.clear();
        }
    }
}
