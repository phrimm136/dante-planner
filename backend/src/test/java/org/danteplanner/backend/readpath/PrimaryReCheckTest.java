package org.danteplanner.backend.readpath;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.config.UndeclaredPrimaryAccessGuard;
import org.danteplanner.backend.shared.exception.EntityNotFoundException;
import org.danteplanner.backend.shared.readpath.ContentTombstoneStore;
import org.danteplanner.backend.shared.readpath.PrimaryReCheck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The replica-miss re-check: what it promotes, what it masks, and what it must leave behind.
 *
 * <p>The routing pin is a static thread-local read through
 * {@link ReadOnlyRoutingDataSource#determineCurrentLookupKey()}, so the supplier observes which
 * pool it would reach. That is the only seam through which the pin is visible, and leaking one
 * would silently route a later unrelated read on the same worker thread.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrimaryReCheckTest {

    private static final String ENTITY_TYPE = "planner";
    private static final String PROMOTED_COUNTER = "replica_miss_promoted_total";

    /** Never armed: these probes read the routing decision, they do not acquire a connection. */
    private static final UndeclaredPrimaryAccessGuard PROBE_GUARD =
            new UndeclaredPrimaryAccessGuard(new SimpleMeterRegistry(), false);

    @Mock ContentTombstoneStore tombstoneStore;

    private SimpleMeterRegistry meterRegistry;
    private PrimaryReCheck reCheck;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        reCheck = new PrimaryReCheck(meterRegistry, tombstoneStore);
        ReadOnlyRoutingDataSource.clear();
    }

    @AfterEach
    void clearPin() {
        ReadOnlyRoutingDataSource.clear();
    }

    /** What the current thread would route to, as the routing datasource itself decides it. */
    private static Object currentRoutingKey() {
        return new ReadOnlyRoutingDataSource(PROBE_GUARD).determineCurrentLookupKey();
    }

    private double promoted() {
        var counter = meterRegistry.find(PROMOTED_COUNTER).counter();
        return counter == null ? 0d : counter.count();
    }

    @Nested
    @DisplayName("a replica hit")
    class ReplicaHit {

        @Test
        @DisplayName("is returned unpinned when no tombstone covers it")
        void liveEntity_WhenReadFromReplica_IsServed() {
            when(tombstoneStore.isTombstoned(ENTITY_TYPE, id)).thenReturn(false);

            String result = reCheck.readWithReCheck(ENTITY_TYPE, id, () -> "planner");

            assertEquals("planner", result);
            assertEquals(0d, promoted(), "a hit must not spend a bulkhead re-check");
            assertNull(currentRoutingKeyOverride(), "the thread must be left unpinned");
        }

        @Test
        @DisplayName("is masked as absent when a tombstone covers it")
        void deletedEntity_WhenReplicaHits_IsMasked() {
            when(tombstoneStore.isTombstoned(ENTITY_TYPE, id)).thenReturn(true);

            assertThrows(EntityNotFoundException.class,
                    () -> reCheck.readWithReCheck(ENTITY_TYPE, id, () -> "planner"));
        }

        /**
         * The tombstone store fails open, so an unreachable Redis reports absent and the row is
         * served. On this branch nothing else guards it: the primary re-check runs on a miss and
         * never re-examines a positive.
         */
        @Test
        @DisplayName("is served when the tombstone store cannot answer")
        void tombstoneStore_WhenFailingOpen_ServesTheRow() {
            when(tombstoneStore.isTombstoned(ENTITY_TYPE, id)).thenReturn(false);

            assertEquals("planner", reCheck.readWithReCheck(ENTITY_TYPE, id, () -> "planner"));
        }
    }

    @Nested
    @DisplayName("a replica miss")
    class ReplicaMiss {

        @Test
        @DisplayName("re-runs the dereference pinned to the bulkhead pool")
        void miss_WhenReplicaMisses_IsRecheckedThroughBulkhead() {
            AtomicInteger attempts = new AtomicInteger();
            Supplier<String> dereference = () -> {
                if (attempts.getAndIncrement() == 0) {
                    throw new EntityNotFoundException(ENTITY_TYPE, id);
                }
                assertEquals(RoutingKey.BULKHEAD, currentRoutingKey(),
                        "the re-check must reach the primary through the isolated bulkhead pool");
                return "planner";
            };

            assertEquals("planner", reCheck.readWithReCheck(ENTITY_TYPE, id, dereference));
            assertEquals(2, attempts.get());
            assertEquals(1d, promoted());
        }

        @Test
        @DisplayName("clears the pin once the re-check succeeds")
        void pin_WhenRecheckSucceeds_IsReleased() {
            AtomicInteger attempts = new AtomicInteger();
            reCheck.readWithReCheck(ENTITY_TYPE, id, () -> {
                if (attempts.getAndIncrement() == 0) {
                    throw new EntityNotFoundException(ENTITY_TYPE, id);
                }
                return "planner";
            });

            assertNull(currentRoutingKeyOverride(),
                    "a leaked pin would route a later unrelated read on this worker thread");
        }

        @Test
        @DisplayName("clears the pin when the primary misses too")
        void pin_WhenRecheckAlsoMisses_IsReleased() {
            assertThrows(EntityNotFoundException.class, () -> reCheck.readWithReCheck(
                    ENTITY_TYPE, id, () -> {
                        throw new EntityNotFoundException(ENTITY_TYPE, id);
                    }));

            assertNull(currentRoutingKeyOverride());
        }

        @Test
        @DisplayName("does not consult the tombstone gate on the promoted path")
        void promotedRead_WhenPromoted_SkipsTombstoneGate() {
            AtomicInteger attempts = new AtomicInteger();
            reCheck.readWithReCheck(ENTITY_TYPE, id, () -> {
                if (attempts.getAndIncrement() == 0) {
                    throw new EntityNotFoundException(ENTITY_TYPE, id);
                }
                return "planner";
            });

            verify(tombstoneStore, never()).isTombstoned(ENTITY_TYPE, id);
        }
    }

    @Nested
    @DisplayName("a batch read")
    class BatchRead {

        private final UUID replicated = UUID.randomUUID();
        private final UUID primaryOnly = UUID.randomUUID();
        private final UUID nowhere = UUID.randomUUID();
        private final Set<UUID> onPrimary = Set.of(replicated, primaryOnly);

        private final List<List<UUID>> calls = new ArrayList<>();
        private final List<Object> routedTo = new ArrayList<>();

        /** The replica holds only {@code replicated}; the primary holds it and {@code primaryOnly}. */
        private final Function<Collection<UUID>, List<UUID>> dereference = requested -> {
            calls.add(List.copyOf(requested));
            Object key = currentRoutingKeyOverride();
            routedTo.add(key);
            Set<UUID> visible = key == RoutingKey.BULKHEAD ? onPrimary : Set.of(replicated);
            return requested.stream().filter(visible::contains).toList();
        };

        @Test
        @DisplayName("re-reads every replica miss in one bulkhead dereference and omits ids missing there too")
        void misses_WhenReplicaLacksThem_AreReReadOnceThroughBulkhead() {
            when(tombstoneStore.tombstonedAmong(ENTITY_TYPE, Set.of(replicated))).thenReturn(Set.of());

            List<UUID> served = reCheck.readAllWithReCheck(
                    ENTITY_TYPE, List.of(replicated, primaryOnly, nowhere), dereference, Function.identity());

            assertThat(served).containsExactlyInAnyOrder(replicated, primaryOnly);
            assertThat(calls).containsExactly(
                    List.of(replicated, primaryOnly, nowhere), List.of(primaryOnly, nowhere));
            assertThat(routedTo).containsExactly(null, RoutingKey.BULKHEAD);
            assertEquals(1d, promoted());
            assertNull(currentRoutingKeyOverride(), "a leaked pin would route a later unrelated read");
        }

        @Test
        @DisplayName("drops a tombstoned replica hit and leaves it out of the re-check")
        void tombstonedHit_WhenReplicaServesIt_IsDropped() {
            when(tombstoneStore.tombstonedAmong(ENTITY_TYPE, Set.of(replicated))).thenReturn(Set.of(replicated));

            List<UUID> served = reCheck.readAllWithReCheck(
                    ENTITY_TYPE, List.of(replicated), dereference, Function.identity());

            assertThat(served).isEmpty();
            assertThat(calls).hasSize(1);
            assertEquals(0d, promoted());
        }

        @Test
        @DisplayName("spends no bulkhead dereference when the replica returns every id")
        void allHits_WhenReplicaHasEveryId_SkipTheReCheck() {
            when(tombstoneStore.tombstonedAmong(ENTITY_TYPE, Set.of(replicated))).thenReturn(Set.of());

            List<UUID> served = reCheck.readAllWithReCheck(
                    ENTITY_TYPE, List.of(replicated), dereference, Function.identity());

            assertThat(served).containsExactly(replicated);
            assertThat(calls).hasSize(1);
            assertEquals(0d, promoted());
        }

        @Test
        @DisplayName("does not consult the tombstone gate for promoted rows")
        void promotedRows_WhenReCheckedOnPrimary_SkipTombstoneGate() {
            when(tombstoneStore.tombstonedAmong(ENTITY_TYPE, Set.of())).thenReturn(Set.of());

            reCheck.readAllWithReCheck(ENTITY_TYPE, List.of(primaryOnly), dereference, Function.identity());

            verify(tombstoneStore).tombstonedAmong(ENTITY_TYPE, Set.of());
            verify(tombstoneStore, never()).tombstonedAmong(ENTITY_TYPE, Set.of(primaryOnly));
        }
    }

    private double bulkheadQueries() {
        var counter = meterRegistry.find("replica_bulkhead_queries_total").counter();
        return counter == null ? 0d : counter.count();
    }

    @Nested
    @DisplayName("bulkhead re-check accounting")
    class BulkheadQueries {

        private final UUID first = UUID.randomUUID();
        private final UUID second = UUID.randomUUID();

        @Test
        @DisplayName("counts one query for a single-read miss")
        void singleMiss_WhenReCheckedOnPrimary_CountsOneQuery() {
            AtomicInteger calls = new AtomicInteger();
            Supplier<String> dereference = () -> {
                if (calls.incrementAndGet() == 1) {
                    throw new EntityNotFoundException(ENTITY_TYPE, id);
                }
                return "row";
            };

            reCheck.readWithReCheck(ENTITY_TYPE, id, dereference);

            assertEquals(1d, bulkheadQueries());
        }

        @Test
        @DisplayName("counts one query for a batch however many rows it promotes")
        void batchMisses_WhenReReadOnPrimary_CountOneQueryAndEveryPromotedRow() {
            when(tombstoneStore.tombstonedAmong(ENTITY_TYPE, Set.of())).thenReturn(Set.of());
            Function<Collection<UUID>, List<UUID>> dereference = requested ->
                    currentRoutingKeyOverride() == RoutingKey.BULKHEAD ? List.copyOf(requested) : List.of();

            reCheck.readAllWithReCheck(ENTITY_TYPE, List.of(first, second), dereference, Function.identity());

            assertEquals(1d, bulkheadQueries());
            assertEquals(2d, promoted());
        }

        @Test
        @DisplayName("counts nothing when the replica serves every id")
        void batchHits_WhenReplicaServesEveryId_CountNoQuery() {
            when(tombstoneStore.tombstonedAmong(ENTITY_TYPE, Set.of(first, second))).thenReturn(Set.of());

            reCheck.readAllWithReCheck(ENTITY_TYPE, List.of(first, second), List::copyOf, Function.identity());

            assertEquals(0d, bulkheadQueries());
        }
    }

    @Nested
    @DisplayName("a public-view read under a withdrawal marker")
    class PublicViewReRead {

        private static final String SCOPE = "published-planner";

        @Test
        @DisplayName("serves the replica hit untouched when no marker exists")
        void hit_WhenNoMarkerExists_IsServedWithoutReRead() {
            when(tombstoneStore.isTombstoned(SCOPE, id)).thenReturn(false);
            AtomicInteger calls = new AtomicInteger();

            String served = reCheck.reReadWhenTombstoned(SCOPE, id, "replica",
                    () -> "primary-" + calls.incrementAndGet());

            assertEquals("replica", served);
            assertEquals(0, calls.get());
            assertEquals(0d, bulkheadQueries());
        }

        @Test
        @DisplayName("re-reads through the bulkhead and serves what the primary holds when a marker exists")
        void hit_WhenMarkerExistsAndPrimaryHoldsTheRow_ServesThePrimaryRow() {
            when(tombstoneStore.isTombstoned(SCOPE, id)).thenReturn(true);
            List<Object> routedTo = new ArrayList<>();

            String served = reCheck.reReadWhenTombstoned(SCOPE, id, "replica", () -> {
                routedTo.add(currentRoutingKeyOverride());
                return "primary";
            });

            assertEquals("primary", served);
            assertThat(routedTo).containsExactly(RoutingKey.BULKHEAD);
            assertEquals(1d, bulkheadQueries());
            assertNull(currentRoutingKeyOverride(), "a leaked pin would route a later unrelated read");
        }

        @Test
        @DisplayName("answers with the primary's own miss when a marker exists and the primary lacks the row")
        void hit_WhenMarkerExistsAndPrimaryLacksTheRow_PropagatesThePrimaryMiss() {
            when(tombstoneStore.isTombstoned(SCOPE, id)).thenReturn(true);
            EntityNotFoundException primaryMiss = new EntityNotFoundException(ENTITY_TYPE, id);

            EntityNotFoundException thrown = assertThrows(EntityNotFoundException.class,
                    () -> reCheck.reReadWhenTombstoned(SCOPE, id, "replica", () -> {
                        throw primaryMiss;
                    }));

            assertThat(thrown).isSameAs(primaryMiss);
            assertNull(currentRoutingKeyOverride(), "a leaked pin would route a later unrelated read");
        }
    }

    /**
     * Reads the override alone rather than the resolved key: outside a transaction the resolved key
     * falls back to PRIMARY, which is indistinguishable from a leaked PRIMARY pin.
     */
    private static Object currentRoutingKeyOverride() {
        ReadOnlyRoutingDataSource probe = new ReadOnlyRoutingDataSource(PROBE_GUARD);
        Object key = probe.determineCurrentLookupKey();
        return key == RoutingKey.PRIMARY ? null : key;
    }
}
