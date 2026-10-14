package org.danteplanner.backend.readpath;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.danteplanner.backend.shared.config.ReadOnlyRoutingDataSource;
import org.danteplanner.backend.shared.config.RoutingKey;
import org.danteplanner.backend.shared.config.UndeclaredPrimaryAccessGuard;
import org.danteplanner.backend.shared.exception.EntityNotFoundException;
import org.danteplanner.backend.shared.exception.ReadTemporarilyUnavailableException;
import org.danteplanner.backend.shared.readpath.PrimaryReCheck;
import org.danteplanner.backend.support.RecordingTombstoneStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.danteplanner.backend.support.Rejections.assertRejected;

/**
 * The replica-miss re-check: what it promotes, what it masks, and what it must leave behind.
 *
 * <p>The routing pin is a static thread-local read through
 * {@link ReadOnlyRoutingDataSource#determineCurrentLookupKey()}, so the supplier observes which
 * pool it would reach. That is the only seam through which the pin is visible, and leaking one
 * would silently route a later unrelated read on the same worker thread.</p>
 */
class PrimaryReCheckTest {

    private static final String ENTITY_TYPE = "planner";
    private static final String PROMOTED_COUNTER = "replica_miss_promoted_total";
    private static final String TOMBSTONE_HIT_COUNTER = "tombstone.hit";
    private static final String MARKER_SCOPE = "published-planner";

    /** Never armed: these probes read the routing decision, they do not acquire a connection. */
    private static final UndeclaredPrimaryAccessGuard PROBE_GUARD =
            new UndeclaredPrimaryAccessGuard(new SimpleMeterRegistry(), false);

    private final RecordingTombstoneStore tombstoneStore = new RecordingTombstoneStore();

    private SimpleMeterRegistry meterRegistry;
    private PrimaryReCheck reCheck;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        reCheck = reCheck(false, true, RoutingKey.BULKHEAD);
        ReadOnlyRoutingDataSource.clear();
    }

    @AfterEach
    void clearPin() {
        ReadOnlyRoutingDataSource.clear();
    }

    private PrimaryReCheck reCheck(boolean servedByAuthority, boolean authorityHealthy, RoutingKey target) {
        return new PrimaryReCheck(meterRegistry, tombstoneStore,
                () -> servedByAuthority, () -> authorityHealthy, target);
    }

    /** What the current thread would route to, as the routing datasource itself decides it. */
    private static Object currentRoutingKey() {
        return new ReadOnlyRoutingDataSource(PROBE_GUARD).determineCurrentLookupKey();
    }

    private double promoted() {
        var counter = meterRegistry.find(PROMOTED_COUNTER).counter();
        return counter == null ? 0d : counter.count();
    }

    private double tombstoneHits(String scope) {
        var counter = meterRegistry.find(TOMBSTONE_HIT_COUNTER).tag("scope", scope).counter();
        return counter == null ? 0d : counter.count();
    }

    @Nested
    class ReplicaHit {

        @Test
        void liveEntity_WhenReadFromReplica_IsServed() {
            String result = reCheck.readWithReCheck(ENTITY_TYPE, id, () -> "planner");

            assertThat(result).isEqualTo("planner");
            assertThat(promoted()).as("a hit must not spend a bulkhead re-check").isEqualTo(0d);
            assertThat(currentRoutingKeyOverride()).as("the thread must be left unpinned").isNull();
        }

        @Test
        void deletedEntity_WhenReplicaHits_IsMasked() {
            tombstoneStore.writeTombstone(ENTITY_TYPE, id);

            assertRejected(() -> reCheck.readWithReCheck(ENTITY_TYPE, id, () -> "planner")).withCode("NOT_FOUND");
        }

        @Test
        void tombstoneHit_WhenReplicaHitIsMasked_CountsOnceUnderItsEntityType() {
            tombstoneStore.writeTombstone(ENTITY_TYPE, id);

            assertRejected(() -> reCheck.readWithReCheck(ENTITY_TYPE, id, () -> "planner")).withCode("NOT_FOUND");
            assertThat(tombstoneHits(ENTITY_TYPE)).isEqualTo(1d);
            assertThat(tombstoneHits(MARKER_SCOPE)).isEqualTo(0d);
        }

        @Test
        void tombstoneHit_WhenNoTombstoneAndNoMarker_CountsNothing() {
            reCheck.readWithReCheck(ENTITY_TYPE, id, () -> "planner");
            reCheck.reReadWhenTombstoned(MARKER_SCOPE, id, "planner", () -> "primary");

            assertThat(tombstoneHits(ENTITY_TYPE)).isEqualTo(0d);
            assertThat(tombstoneHits(MARKER_SCOPE)).isEqualTo(0d);
        }

        /**
         * The tombstone store fails open, so an unreachable Redis reports absent and the row is
         * served. On this branch nothing else guards it: the primary re-check runs on a miss and
         * never re-examines a positive.
         */
        @Test
        void tombstoneStore_WhenFailingOpen_ServesTheRow() {
            assertThat(reCheck.readWithReCheck(ENTITY_TYPE, id, () -> "planner")).isEqualTo("planner");
        }
    }

    @Nested
    class ReplicaMiss {

        @Test
        void miss_WhenReplicaMisses_IsRecheckedThroughBulkhead() {
            AtomicInteger attempts = new AtomicInteger();
            Supplier<String> dereference = () -> {
                if (attempts.getAndIncrement() == 0) {
                    throw new EntityNotFoundException(ENTITY_TYPE, id);
                }
                assertThat(currentRoutingKey())
                        .as("the re-check must reach the primary through the isolated bulkhead pool")
                        .isEqualTo(RoutingKey.BULKHEAD);
                return "planner";
            };

            assertThat(reCheck.readWithReCheck(ENTITY_TYPE, id, dereference)).isEqualTo("planner");
            assertThat(attempts.get()).isEqualTo(2);
            assertThat(promoted()).isEqualTo(1d);
        }

        @Test
        void pin_WhenRecheckSucceeds_IsReleased() {
            AtomicInteger attempts = new AtomicInteger();
            reCheck.readWithReCheck(ENTITY_TYPE, id, () -> {
                if (attempts.getAndIncrement() == 0) {
                    throw new EntityNotFoundException(ENTITY_TYPE, id);
                }
                return "planner";
            });

            assertThat(currentRoutingKeyOverride())
                    .as("a leaked pin would route a later unrelated read on this worker thread")
                    .isNull();
        }

        @Test
        void pin_WhenRecheckAlsoMisses_IsReleased() {
            assertRejected(() -> reCheck.readWithReCheck(
                    ENTITY_TYPE, id, () -> {
                        throw new EntityNotFoundException(ENTITY_TYPE, id);
                    })).withCode("NOT_FOUND");

            assertThat(currentRoutingKeyOverride()).isNull();
        }

        @Test
        void promotedRead_WhenPromoted_SkipsTombstoneGate() {
            tombstoneStore.writeTombstone(ENTITY_TYPE, id);
            AtomicInteger attempts = new AtomicInteger();
            String served = reCheck.readWithReCheck(ENTITY_TYPE, id, () -> {
                if (attempts.getAndIncrement() == 0) {
                    throw new EntityNotFoundException(ENTITY_TYPE, id);
                }
                return "planner";
            });

            assertThat(served).isEqualTo("planner");
        }

        @Test
        void tombstoneHit_WhenMissIsPromoted_IsNotCounted() {
            tombstoneStore.writeTombstone(ENTITY_TYPE, id);
            AtomicInteger attempts = new AtomicInteger();

            reCheck.readWithReCheck(ENTITY_TYPE, id, () -> {
                if (attempts.getAndIncrement() == 0) {
                    throw new EntityNotFoundException(ENTITY_TYPE, id);
                }
                return "planner";
            });

            assertThat(tombstoneHits(ENTITY_TYPE)).isEqualTo(0d);
            assertThat(promoted()).isEqualTo(1d);
        }
    }

    @Nested
    class BatchRead {

        private final UUID replicated = UUID.randomUUID();
        private final UUID primaryOnly = UUID.randomUUID();
        private final UUID nowhere = UUID.randomUUID();
        private final Set<UUID> onPrimary = Set.of(replicated, primaryOnly);

        private final List<List<UUID>> calls = new ArrayList<>();
        private final List<Object> routedTo = new ArrayList<>();

        /** The replica holds only {@code replicated}; the primary holds it and {@code primaryOnly}. */
        private List<UUID> dereference(Collection<UUID> requested) {
            calls.add(List.copyOf(requested));
            Object key = currentRoutingKeyOverride();
            routedTo.add(key);
            Set<UUID> visible = key == RoutingKey.BULKHEAD ? onPrimary : Set.of(replicated);
            return requested.stream().filter(visible::contains).toList();
        }

        @Test
        void misses_WhenReplicaLacksThem_AreReReadOnceThroughBulkhead() {
            List<UUID> served = reCheck.readAllWithReCheck(
                    ENTITY_TYPE, List.of(replicated, primaryOnly, nowhere), this::dereference, Function.identity());

            assertThat(served).containsExactlyInAnyOrder(replicated, primaryOnly);
            assertThat(calls).containsExactly(
                    List.of(replicated, primaryOnly, nowhere), List.of(primaryOnly, nowhere));
            assertThat(routedTo).containsExactly(null, RoutingKey.BULKHEAD);
            assertThat(promoted()).isEqualTo(1d);
            assertThat(currentRoutingKeyOverride()).as("a leaked pin would route a later unrelated read").isNull();
        }

        @Test
        void tombstonedHit_WhenReplicaServesIt_IsDropped() {
            tombstoneStore.writeTombstone(ENTITY_TYPE, replicated);

            List<UUID> served = reCheck.readAllWithReCheck(
                    ENTITY_TYPE, List.of(replicated), this::dereference, Function.identity());

            assertThat(served).isEmpty();
            assertThat(calls).hasSize(1);
            assertThat(promoted()).isEqualTo(0d);
        }

        @Test
        void allHits_WhenReplicaHasEveryId_SkipTheReCheck() {
            List<UUID> served = reCheck.readAllWithReCheck(
                    ENTITY_TYPE, List.of(replicated), this::dereference, Function.identity());

            assertThat(served).containsExactly(replicated);
            assertThat(calls).hasSize(1);
            assertThat(promoted()).isEqualTo(0d);
        }

        @Test
        void promotedRows_WhenReCheckedOnPrimary_SkipTombstoneGate() {
            tombstoneStore.writeTombstone(ENTITY_TYPE, primaryOnly);

            List<UUID> served = reCheck.readAllWithReCheck(
                    ENTITY_TYPE, List.of(primaryOnly), this::dereference, Function.identity());

            assertThat(served).containsExactly(primaryOnly);
        }

        @Test
        void tombstoneHit_WhenOneOfTwoHitsIsTombstoned_CountsOne() {
            UUID live = UUID.randomUUID();
            UUID deleted = UUID.randomUUID();
            tombstoneStore.writeTombstone(ENTITY_TYPE, deleted);

            List<UUID> served = reCheck.readAllWithReCheck(
                    ENTITY_TYPE, List.of(live, deleted), List::copyOf, Function.identity());

            assertThat(served).containsExactly(live);
            assertThat(tombstoneHits(ENTITY_TYPE)).isEqualTo(1d);
        }

        @Test
        void tombstoneHit_WhenEveryHitIsTombstoned_CountsEachOne() {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            tombstoneStore.writeTombstone(ENTITY_TYPE, first);
            tombstoneStore.writeTombstone(ENTITY_TYPE, second);

            reCheck.readAllWithReCheck(ENTITY_TYPE, List.of(first, second), List::copyOf, Function.identity());

            assertThat(tombstoneHits(ENTITY_TYPE)).isEqualTo(2d);
        }

        @Test
        void tombstoneHit_WhenNoHitIsTombstoned_RegistersNoSeries() {
            reCheck.readAllWithReCheck(ENTITY_TYPE, List.of(replicated), this::dereference, Function.identity());

            assertThat(meterRegistry.find(TOMBSTONE_HIT_COUNTER).counters()).isEmpty();
        }
    }

    private double bulkheadQueries() {
        var counter = meterRegistry.find("replica_bulkhead_queries_total").counter();
        return counter == null ? 0d : counter.count();
    }

    @Nested
    class BulkheadQueries {

        private final UUID first = UUID.randomUUID();
        private final UUID second = UUID.randomUUID();

        @Test
        void singleMiss_WhenReCheckedOnPrimary_CountsOneQuery() {
            AtomicInteger calls = new AtomicInteger();
            Supplier<String> dereference = () -> {
                if (calls.incrementAndGet() == 1) {
                    throw new EntityNotFoundException(ENTITY_TYPE, id);
                }
                return "row";
            };

            reCheck.readWithReCheck(ENTITY_TYPE, id, dereference);

            assertThat(bulkheadQueries()).isEqualTo(1d);
        }

        @Test
        void batchMisses_WhenReReadOnPrimary_CountOneQueryAndEveryPromotedRow() {
            Function<Collection<UUID>, List<UUID>> dereference = requested ->
                    currentRoutingKeyOverride() == RoutingKey.BULKHEAD ? List.copyOf(requested) : List.of();

            reCheck.readAllWithReCheck(ENTITY_TYPE, List.of(first, second), dereference, Function.identity());

            assertThat(bulkheadQueries()).isEqualTo(1d);
            assertThat(promoted()).isEqualTo(2d);
        }

        @Test
        void batchHits_WhenReplicaServesEveryId_CountNoQuery() {
            reCheck.readAllWithReCheck(ENTITY_TYPE, List.of(first, second), List::copyOf, Function.identity());

            assertThat(bulkheadQueries()).isEqualTo(0d);
        }
    }

    @Nested
    class PublicViewReRead {

        private static final String SCOPE = "published-planner";

        @Test
        void hit_WhenNoMarkerExists_IsServedWithoutReRead() {
            AtomicInteger calls = new AtomicInteger();

            String served = reCheck.reReadWhenTombstoned(SCOPE, id, "replica",
                    () -> "primary-" + calls.incrementAndGet());

            assertThat(served).isEqualTo("replica");
            assertThat(calls.get()).isEqualTo(0);
            assertThat(bulkheadQueries()).isEqualTo(0d);
        }

        @Test
        void hit_WhenMarkerExistsAndPrimaryHoldsTheRow_ServesThePrimaryRow() {
            tombstoneStore.writeTombstone(SCOPE, id);
            List<Object> routedTo = new ArrayList<>();

            String served = reCheck.reReadWhenTombstoned(SCOPE, id, "replica", () -> {
                routedTo.add(currentRoutingKeyOverride());
                return "primary";
            });

            assertThat(served).isEqualTo("primary");
            assertThat(routedTo).containsExactly(RoutingKey.BULKHEAD);
            assertThat(bulkheadQueries()).isEqualTo(1d);
            assertThat(currentRoutingKeyOverride()).as("a leaked pin would route a later unrelated read").isNull();
        }

        @Test
        void markerHit_WhenMarkerExists_CountsOnceUnderTheMarkerScope() {
            tombstoneStore.writeTombstone(SCOPE, id);

            String served = reCheck.reReadWhenTombstoned(SCOPE, id, "replica", () -> "primary");

            assertThat(served).isEqualTo("primary");
            assertThat(tombstoneHits(SCOPE)).isEqualTo(1d);
            assertThat(tombstoneHits(ENTITY_TYPE)).isEqualTo(0d);
        }

        @Test
        void hit_WhenMarkerExistsAndPrimaryLacksTheRow_PropagatesThePrimaryMiss() {
            tombstoneStore.writeTombstone(SCOPE, id);
            EntityNotFoundException primaryMiss = new EntityNotFoundException(ENTITY_TYPE, id);

            assertThatThrownBy(() -> reCheck.reReadWhenTombstoned(SCOPE, id, "replica", () -> {
                throw primaryMiss;
            })).isSameAs(primaryMiss);
            assertThat(currentRoutingKeyOverride()).as("a leaked pin would route a later unrelated read").isNull();
        }
    }

    @Nested
    class ServedByTheAuthority {

        private PrimaryReCheck authoritative;

        @BeforeEach
        void servedByThePrimary() {
            authoritative = reCheck(true, true, RoutingKey.BULKHEAD);
        }

        @Test
        void hit_WhenTheAuthorityServedIt_IsServedWithoutATombstoneLookup() {
            tombstoneStore.writeTombstone(ENTITY_TYPE, id);

            assertThat(authoritative.readWithReCheck(ENTITY_TYPE, id, () -> "planner")).isEqualTo("planner");
            assertThat(tombstoneHits(ENTITY_TYPE)).isEqualTo(0d);
        }

        @Test
        void miss_WhenTheAuthorityMissed_PropagatesWithoutAReCheck() {
            AtomicInteger attempts = new AtomicInteger();
            EntityNotFoundException miss = new EntityNotFoundException(ENTITY_TYPE, id);

            assertThatThrownBy(() -> authoritative.readWithReCheck(ENTITY_TYPE, id, () -> {
                attempts.incrementAndGet();
                throw miss;
            })).isSameAs(miss);
            assertThat(attempts.get()).isEqualTo(1);
            assertThat(bulkheadQueries()).isEqualTo(0d);
        }

        @Test
        void batch_WhenTheAuthorityServedIt_ReturnsTheHitsWithoutGuards() {
            UUID other = UUID.randomUUID();
            tombstoneStore.writeTombstone(ENTITY_TYPE, id);

            List<UUID> served = authoritative.readAllWithReCheck(
                    ENTITY_TYPE, List.of(id, other), requested -> List.of(id), Function.identity());

            assertThat(served).containsExactly(id);
            assertThat(promoted()).isEqualTo(0d);
        }

        @Test
        void publicView_WhenTheAuthorityServedAMarkedRow_ServesItWithoutReRead() {
            tombstoneStore.writeTombstone(MARKER_SCOPE, id);

            assertThat(authoritative.reReadWhenTombstoned(MARKER_SCOPE, id, "primary", () -> "re-read"))
                    .isEqualTo("primary");
            assertThat(bulkheadQueries()).isEqualTo(0d);
        }
    }

    @Nested
    class AuthorityUnreachable {

        private PrimaryReCheck primaryDown;

        @BeforeEach
        void primaryBreakerOpen() {
            primaryDown = reCheck(false, false, RoutingKey.BULKHEAD);
        }

        @Test
        void miss_WhenThePrimaryBreakerIsOpen_AnswersReadUnavailableWithoutReReading() {
            AtomicInteger attempts = new AtomicInteger();

            assertRejected(() -> primaryDown.readWithReCheck(ENTITY_TYPE, id, () -> {
                attempts.incrementAndGet();
                throw new EntityNotFoundException(ENTITY_TYPE, id);
            })).withCode("READ_TEMPORARILY_UNAVAILABLE");
            assertThat(attempts.get()).isEqualTo(1);
            assertThat(currentRoutingKeyOverride()).isNull();
        }

        @Test
        void tombstoneHit_WhenThePrimaryBreakerIsOpen_StaysNotFound() {
            tombstoneStore.writeTombstone(ENTITY_TYPE, id);

            assertRejected(() -> primaryDown.readWithReCheck(ENTITY_TYPE, id, () -> "planner")).withCode("NOT_FOUND");
        }

        @Test
        void markerHit_WhenThePrimaryBreakerIsOpen_AnswersReadUnavailable() {
            tombstoneStore.writeTombstone(MARKER_SCOPE, id);

            assertRejected(() -> primaryDown.reReadWhenTombstoned(MARKER_SCOPE, id, "replica", () -> "primary"))
                    .withCode("READ_TEMPORARILY_UNAVAILABLE");
        }

        @Test
        void reCheck_WhenTheAcquireFailsToConnect_AnswersReadUnavailableAndReleasesThePin() {
            AtomicInteger attempts = new AtomicInteger();
            DataAccessResourceFailureException unreachable = new DataAccessResourceFailureException(
                    "Unable to acquire JDBC Connection",
                    new SQLTransientConnectionException("bulkhead - Connection is not available", "08S01"));

            assertThatThrownBy(() -> reCheck.readWithReCheck(ENTITY_TYPE, id, () -> {
                if (attempts.getAndIncrement() == 0) {
                    throw new EntityNotFoundException(ENTITY_TYPE, id);
                }
                throw unreachable;
            })).isInstanceOf(ReadTemporarilyUnavailableException.class).hasCause(unreachable);
            assertThat(currentRoutingKeyOverride()).isNull();
        }

        @Test
        void reCheck_WhenTheAcquireTimesOutWithoutAConnectionFailure_AnswersReadUnavailableAndReleasesThePin() {
            AtomicInteger attempts = new AtomicInteger();
            DataAccessResourceFailureException saturated = new DataAccessResourceFailureException(
                    "Unable to acquire JDBC Connection", new SQLException("bulkhead - request timed out"));

            assertThatThrownBy(() -> reCheck.readWithReCheck(ENTITY_TYPE, id, () -> {
                if (attempts.getAndIncrement() == 0) {
                    throw new EntityNotFoundException(ENTITY_TYPE, id);
                }
                throw saturated;
            })).isInstanceOf(ReadTemporarilyUnavailableException.class).hasCause(saturated);
            assertThat(currentRoutingKeyOverride()).isNull();
        }

        @Test
        void reCheck_WhenTheReReadFailsMidStatementWithANonConnectionError_PropagatesIt() {
            AtomicInteger attempts = new AtomicInteger();
            BadSqlGrammarException midStatement = new BadSqlGrammarException(
                    "re-read", "SELECT 1", new SQLException("syntax", "42000"));

            assertThatThrownBy(() -> reCheck.readWithReCheck(ENTITY_TYPE, id, () -> {
                if (attempts.getAndIncrement() == 0) {
                    throw new EntityNotFoundException(ENTITY_TYPE, id);
                }
                throw midStatement;
            })).isSameAs(midStatement);
        }
    }

    @Nested
    class ReCheckWithoutABulkhead {

        @Test
        void miss_WhenNoBulkheadPoolExists_ReReadsThroughThePrimaryTarget() {
            PrimaryReCheck oregon = reCheck(false, true, RoutingKey.PRIMARY);
            AtomicInteger attempts = new AtomicInteger();
            List<Object> routedTo = new ArrayList<>();

            TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
            try {
                oregon.readWithReCheck(ENTITY_TYPE, id, () -> {
                    routedTo.add(currentRoutingKey());
                    if (attempts.getAndIncrement() == 0) {
                        throw new EntityNotFoundException(ENTITY_TYPE, id);
                    }
                    return "planner";
                });
            } finally {
                TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
            }

            assertThat(routedTo).containsExactly(RoutingKey.REPLICA, RoutingKey.PRIMARY);
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
