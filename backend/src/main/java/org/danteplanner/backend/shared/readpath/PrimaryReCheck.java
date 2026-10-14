package org.danteplanner.backend.shared.readpath;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.exception.EntityNotFoundException;
import org.danteplanner.backend.shared.exception.ReadTemporarilyUnavailableException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class PrimaryReCheck {

    static final String PROMOTED_COUNTER = "replica_miss_promoted_total";
    static final String BULKHEAD_QUERIES_COUNTER = "replica_bulkhead_queries_total";
    static final String TOMBSTONE_HIT_COUNTER = "tombstone.hit";
    static final String SCOPE_TAG = "scope";

    private final MeterRegistry meterRegistry;

    private final Counter promotedCounter;
    private final Counter bulkheadQueriesCounter;
    private final ContentTombstoneStore tombstoneStore;
    private final BooleanSupplier servedByAuthority;
    private final BooleanSupplier authorityHealthy;
    private final RoutingKey reCheckTarget;

    public PrimaryReCheck(MeterRegistry meterRegistry, ContentTombstoneStore tombstoneStore,
            BooleanSupplier servedByAuthority, BooleanSupplier authorityHealthy, RoutingKey reCheckTarget) {
        this.meterRegistry = meterRegistry;
        this.promotedCounter = Counter.builder(PROMOTED_COUNTER).register(meterRegistry);
        this.bulkheadQueriesCounter = Counter.builder(BULKHEAD_QUERIES_COUNTER).register(meterRegistry);
        this.tombstoneStore = tombstoneStore;
        this.servedByAuthority = servedByAuthority;
        this.authorityHealthy = authorityHealthy;
        this.reCheckTarget = reCheckTarget;
    }

    public <T> T readWithReCheck(String entityType, UUID id, Supplier<T> dereference) {
        T hit;
        try {
            hit = dereference.get();
        } catch (EntityNotFoundException miss) {
            if (servedByAuthority.getAsBoolean()) {
                throw miss;
            }
            T promoted = onBulkhead(dereference);
            promotedCounter.increment();
            return promoted;
        }
        if (servedByAuthority.getAsBoolean()) {
            return hit;
        }
        if (tombstoneStore.isTombstoned(entityType, id)) {
            countTombstoneHits(entityType, 1);
            throw new EntityNotFoundException(entityType, id);
        }
        return hit;
    }

    public <T> List<T> readAllWithReCheck(String entityType, Collection<UUID> ids,
            Function<Collection<UUID>, List<T>> dereference, Function<T, UUID> idOf) {
        List<T> hits = dereference.apply(ids);
        if (servedByAuthority.getAsBoolean()) {
            return hits;
        }
        Set<UUID> hitIds = hits.stream().map(idOf).collect(Collectors.toSet());
        Set<UUID> tombstoned = tombstoneStore.tombstonedAmong(entityType, hitIds);
        if (!tombstoned.isEmpty()) {
            countTombstoneHits(entityType, tombstoned.size());
        }
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
        if (servedByAuthority.getAsBoolean() || !tombstoneStore.isTombstoned(scope, id)) {
            return hit;
        }
        countTombstoneHits(scope, 1);
        return onBulkhead(dereference);
    }

    private void countTombstoneHits(String scope, int hits) {
        Counter.builder(TOMBSTONE_HIT_COUNTER).tag(SCOPE_TAG, scope).register(meterRegistry).increment(hits);
    }

    private <T> T onBulkhead(Supplier<T> dereference) {
        if (!authorityHealthy.getAsBoolean()) {
            throw new ReadTemporarilyUnavailableException();
        }
        bulkheadQueriesCounter.increment();
        ReadOnlyRoutingDataSource.pinTo(reCheckTarget);
        try {
            return dereference.get();
        } catch (DataAccessResourceFailureException | CannotCreateTransactionException failure) {
            throw new ReadTemporarilyUnavailableException(failure);
        } finally {
            ReadOnlyRoutingDataSource.clear();
        }
    }
}
