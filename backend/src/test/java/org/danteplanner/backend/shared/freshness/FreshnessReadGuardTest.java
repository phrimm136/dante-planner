package org.danteplanner.backend.shared.freshness;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.config.ChosenReadSource;
import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.freshness.MarkerStore.MarkerView;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FreshnessReadGuardTest {

    private static final String SEOUL = "seoul-replica";
    private static final String UUID_A = "3e11fa47-71ca-11e1-9e33-c80aa9429562";
    private static final String APPLIED_TEN = UUID_A + ":1-10";
    private static final String APPLIED_FIVE = UUID_A + ":1-5";
    private static final Duration WAIT_BOUND = Duration.ofMillis(250);
    private static final FreshnessScope PLANNER = new FreshnessScope.Planner(UUID.randomUUID());
    private static final FreshnessScope USER = new FreshnessScope.User(7L);

    @Mock
    private DataSource seoulPool;

    @Mock
    private DataSource primaryPool;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final List<Collection<FreshnessScope>> markerReads = new ArrayList<>();
    private final List<String> waits = new ArrayList<>();
    private final Map<FreshnessScope, MarkerView> views = new LinkedHashMap<>();
    private boolean waitApplies;
    private ReadSourceSelector.Source seoul;
    private ReadSourceSelector.Source primary;
    private ReadSourceSelector selector;
    private FreshnessReadGuard guard;

    @BeforeEach
    void setUp() {
        ReadSourceSelector.clearSkips();
        ChosenReadSource.clear();
        seoul = new ReadSourceSelector.Source(SEOUL, ReadSourceSelector.Authority.REPLICA, seoulPool, breaker(SEOUL));
        primary = new ReadSourceSelector.Source(
                ReadSourceProperties.PRIMARY, ReadSourceSelector.Authority.PRIMARY, primaryPool, breaker(ReadSourceProperties.PRIMARY));
        selector = new ReadSourceSelector(List.of(seoul, primary));
        guard = guardWaiting((source, gtidSet, bound) -> {
            waits.add(source.name() + "|" + gtidSet + "|" + bound);
            return waitApplies;
        });
    }

    @AfterEach
    void clearSkips() {
        ReadSourceSelector.clearSkips();
        ChosenReadSource.clear();
    }

    private static CircuitBreaker breaker(String name) {
        return CircuitBreaker.of(name, ReadSourceProperties.Breaker.defaults().circuitBreakerConfig());
    }

    private FreshnessReadGuard guardWaiting(FreshnessReadGuard.GtidWait gtidWait) {
        return new FreshnessReadGuard(scopes -> {
            markerReads.add(List.copyOf(scopes));
            return views;
        }, gtidWait, new FreshnessReadSettings(WAIT_BOUND), meters, selector);
    }

    private String loadOnCurrentSource(List<String> loads) {
        ReadSourceSelector.Source source = selector.current().orElseThrow();
        ChosenReadSource.record(source);
        loads.add(source.name());
        return source.name();
    }

    private ScopedRead<String> scopedLoad(List<String> loads, Set<FreshnessScope> scopes) {
        return new ScopedRead<>(loadOnCurrentSource(loads), scopes);
    }

    private static final class PlannerMissFixture extends ScopedMiss {

        private PlannerMissFixture(Set<FreshnessScope> scopes) {
            super("PLANNER_NOT_FOUND", "Planner not found", scopes);
        }
    }

    private double waitSamples(String outcome) {
        Timer timer = meters.find(FreshnessReadGuard.READ_WAIT)
                .tag(FreshnessReadGuard.SOURCE_TAG, SEOUL)
                .tag(FreshnessReadGuard.OUTCOME_TAG, outcome)
                .timer();
        return timer == null ? 0 : timer.count();
    }

    @Test
    void settle_WhenEverySourceMarkerIsAbsent_ServesWithoutWaiting() {
        views.put(PLANNER, new MarkerView.Absent());
        views.put(USER, new MarkerView.Absent());

        assertThat(guard.settle(List.of(PLANNER, USER), seoul, Map.of(), Set.of()))
                .isEqualTo(new FreshnessReadGuard.SettleOutcome.Serve());
        assertThat(markerReads).containsExactly(List.of(PLANNER, USER));
        assertThat(waits).isEmpty();
        assertThat(selector.current()).contains(seoul);
    }

    @Test
    void settle_WhenTheMarkerWasAppliedBeforeTheLoadBegan_IssuesNoWait() {
        views.put(PLANNER, new MarkerView.At(APPLIED_TEN));
        waitApplies = true;
        guard.settle(List.of(PLANNER), seoul, Map.of(), Set.of());
        Map<String, String> appliedAtLoadStart = guard.applied();
        views.put(PLANNER, new MarkerView.At(APPLIED_FIVE));
        waitApplies = false;

        assertThat(guard.settle(List.of(PLANNER), seoul, appliedAtLoadStart, Set.of()))
                .isEqualTo(new FreshnessReadGuard.SettleOutcome.Serve());
        assertThat(waits).containsExactly(SEOUL + "|" + UUID_A + ":1-10|" + WAIT_BOUND);
    }

    @Test
    void settle_WhenWaitApplies_ReloadsAndRemembersTheSet() {
        views.put(PLANNER, new MarkerView.At(APPLIED_FIVE));
        views.put(USER, new MarkerView.At(APPLIED_TEN));
        waitApplies = true;

        assertThat(guard.settle(List.of(PLANNER, USER), seoul, Map.of(), Set.of()))
                .isEqualTo(new FreshnessReadGuard.SettleOutcome.Reload());
        waitApplies = false;
        assertThat(guard.settle(List.of(PLANNER, USER), seoul, guard.applied(), Set.of()))
                .isEqualTo(new FreshnessReadGuard.SettleOutcome.Serve());

        assertThat(waits).containsExactly(SEOUL + "|" + UUID_A + ":1-10|" + WAIT_BOUND);
        assertThat(waitSamples(FreshnessReadGuard.APPLIED)).isEqualTo(1);
        assertThat(waitSamples(FreshnessReadGuard.TIMEOUT)).isZero();
        assertThat(selector.current()).contains(seoul);
    }

    @Test
    void settle_WhenTheSetWasRememberedOnlyAfterTheLoadBegan_WaitsAndReloads() {
        views.put(PLANNER, new MarkerView.At(APPLIED_FIVE));
        waitApplies = true;
        Map<String, String> appliedAtLoadStart = guard.applied();
        guard.settle(List.of(PLANNER), seoul, Map.of(), Set.of());

        assertThat(guard.settle(List.of(PLANNER), seoul, appliedAtLoadStart, Set.of()))
                .isEqualTo(new FreshnessReadGuard.SettleOutcome.Reload());
        assertThat(waits).hasSize(2);
    }

    @Test
    void settle_WhenWaitTimesOut_SkipsTheSource() {
        views.put(PLANNER, new MarkerView.At(APPLIED_FIVE));
        waitApplies = false;

        assertThat(guard.settle(List.of(PLANNER), seoul, Map.of(), Set.of()))
                .isEqualTo(new FreshnessReadGuard.SettleOutcome.Skip(seoul));
        assertThat(waitSamples(FreshnessReadGuard.TIMEOUT)).isEqualTo(1);
        assertThat(waitSamples(FreshnessReadGuard.APPLIED)).isZero();
        assertThat(selector.current()).contains(primary);
    }

    @Test
    void settle_WhenTheWaitConnectionFails_SkipsTheSourceWithoutThrowing() throws SQLException {
        when(seoulPool.getConnection()).thenThrow(new SQLException("Communications link failure", "08S01"));
        FreshnessReadGuard realWait = guardWaiting(ReplicaGtidWait::isApplied);
        views.put(PLANNER, new MarkerView.At(APPLIED_FIVE));

        assertThat(realWait.settle(List.of(PLANNER), seoul, Map.of(), Set.of()))
                .isEqualTo(new FreshnessReadGuard.SettleOutcome.Skip(seoul));
        assertThat(waitSamples(FreshnessReadGuard.TIMEOUT)).isEqualTo(1);
        assertThat(seoul.breaker().getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(selector.current()).contains(primary);
    }

    @Test
    void settle_WhenAnyViewIsUnknown_SkipsTheSource() {
        views.put(PLANNER, new MarkerView.At(APPLIED_FIVE));
        views.put(USER, new MarkerView.Unknown());
        waitApplies = true;

        assertThat(guard.settle(List.of(PLANNER, USER), seoul, Map.of(), Set.of()))
                .isEqualTo(new FreshnessReadGuard.SettleOutcome.Skip(seoul));
        assertThat(waits).isEmpty();
        assertThat(selector.current()).contains(primary);
    }

    @Test
    void settle_WhenSourceIsAuthoritative_ServesWithoutReadingMarkers() {
        views.put(PLANNER, new MarkerView.Unknown());

        assertThat(guard.settle(List.of(PLANNER), primary, Map.of(), Set.of()))
                .isEqualTo(new FreshnessReadGuard.SettleOutcome.Serve());
        assertThat(markerReads).isEmpty();
        assertThat(waits).isEmpty();
    }

    @Test
    void read_WhenServedByAuthority_ReadsNoMarkers() throws Throwable {
        ReadSourceSelector.skip(seoul);
        List<String> loads = new ArrayList<>();
        views.put(PLANNER, new MarkerView.Unknown());

        ScopedRead<?> served = guard.read(() -> scopedLoad(loads, Set.of(PLANNER)));

        assertThat(served.value()).isEqualTo(ReadSourceProperties.PRIMARY);
        assertThat(loads).containsExactly(ReadSourceProperties.PRIMARY);
        assertThat(markerReads).isEmpty();
    }

    @Test
    void read_WhenMarkersWereAppliedBeforeTheLoadBegan_ReturnsTheFirstLoad() throws Throwable {
        views.put(PLANNER, new MarkerView.At(APPLIED_FIVE));
        waitApplies = true;
        guard.read(() -> scopedLoad(new ArrayList<>(), Set.of(PLANNER)));
        List<String> loads = new ArrayList<>();
        markerReads.clear();
        waits.clear();
        waitApplies = false;

        ScopedRead<?> served = guard.read(() -> scopedLoad(loads, Set.of(PLANNER)));

        assertThat(served.value()).isEqualTo(SEOUL);
        assertThat(loads).containsExactly(SEOUL);
        assertThat(markerReads).containsExactly(List.of(PLANNER));
        assertThat(waits).isEmpty();
    }

    @Test
    void read_WhenTheMarkerIsAppliedDuringTheWait_LoadsAgainAndServesTheSecondLoad() throws Throwable {
        AtomicInteger loads = new AtomicInteger();
        views.put(PLANNER, new MarkerView.At(APPLIED_FIVE));
        waitApplies = true;

        ScopedRead<?> served = guard.read(() -> {
            loadOnCurrentSource(new ArrayList<>());
            return new ScopedRead<>(loads.incrementAndGet(), Set.of(PLANNER));
        });

        assertThat(served.value()).isEqualTo(2);
        assertThat(waits).containsExactly(SEOUL + "|" + APPLIED_FIVE + "|" + WAIT_BOUND);
        assertThat(markerReads).containsExactly(List.of(PLANNER), List.of(PLANNER));
        assertThat(selector.current()).contains(seoul);
    }

    @Test
    void read_WhenAnotherWaitRecordsTheSetDuringTheLoad_LoadsAgainAndServesTheSecondLoad() throws Throwable {
        AtomicInteger loads = new AtomicInteger();
        views.put(PLANNER, new MarkerView.At(APPLIED_FIVE));
        waitApplies = true;

        ScopedRead<?> served = guard.read(() -> {
            loadOnCurrentSource(new ArrayList<>());
            int load = loads.incrementAndGet();
            if (load == 1) {
                guard.settle(List.of(PLANNER), seoul, Map.of(), Set.of());
            }
            return new ScopedRead<>(load, Set.of(PLANNER));
        });

        assertThat(served.value()).isEqualTo(2);
        assertThat(loads).hasValue(2);
    }

    @Test
    void read_WhenANewerMarkerIsUnappliedAfterAReload_SkipsTheSourceWithoutASecondWait() throws Throwable {
        List<String> loads = new ArrayList<>();
        views.put(PLANNER, new MarkerView.At(APPLIED_FIVE));
        waitApplies = true;

        ScopedRead<?> served = guard.read(() -> {
            ScopedRead<String> read = scopedLoad(loads, Set.of(PLANNER));
            if (loads.size() == 2) {
                views.put(PLANNER, new MarkerView.At(APPLIED_TEN));
            }
            return read;
        });

        assertThat(served.value()).isEqualTo(ReadSourceProperties.PRIMARY);
        assertThat(loads).containsExactly(SEOUL, SEOUL, ReadSourceProperties.PRIMARY);
        assertThat(waits).containsExactly(SEOUL + "|" + APPLIED_FIVE + "|" + WAIT_BOUND);
        assertThat(selector.current()).contains(primary);
    }

    @Test
    void settle_WhenTheSourceMayNotReloadAgain_SkipsWithoutWaiting() {
        views.put(PLANNER, new MarkerView.At(APPLIED_FIVE));
        waitApplies = true;

        assertThat(guard.settle(List.of(PLANNER), seoul, Map.of(), Set.of(seoul.name())))
                .isEqualTo(new FreshnessReadGuard.SettleOutcome.Skip(seoul));
        assertThat(waits).isEmpty();
        assertThat(selector.current()).contains(primary);
    }

    @Test
    void read_WhenAMissMarkerIsAppliedDuringTheWait_LoadsAgainAndServesTheSecondLoad() throws Throwable {
        List<String> loads = new ArrayList<>();
        views.put(PLANNER, new MarkerView.At(APPLIED_FIVE));
        waitApplies = true;

        ScopedRead<?> served = guard.read(() -> {
            ScopedRead<String> read = scopedLoad(loads, Set.of(PLANNER));
            if (loads.size() == 1) {
                throw new PlannerMissFixture(Set.of(PLANNER));
            }
            return read;
        });

        assertThat(served.value()).isEqualTo(SEOUL);
        assertThat(loads).containsExactly(SEOUL, SEOUL);
        assertThat(waits).hasSize(1);
    }

    @Test
    void read_WhenTheSourceIsSkipped_LoadsAgainOnTheNextSource() throws Throwable {
        List<String> loads = new ArrayList<>();
        views.put(PLANNER, new MarkerView.At(APPLIED_FIVE));
        waitApplies = false;

        ScopedRead<?> served = guard.read(() -> scopedLoad(loads, Set.of(PLANNER)));

        assertThat(served.value()).isEqualTo(ReadSourceProperties.PRIMARY);
        assertThat(loads).containsExactly(SEOUL, ReadSourceProperties.PRIMARY);
        assertThat(markerReads).containsExactly(List.of(PLANNER));
    }

    @Test
    void read_WhenAMissHasNoMarker_IsAFinal404() {
        List<String> loads = new ArrayList<>();
        views.put(PLANNER, new MarkerView.Absent());
        PlannerMissFixture miss = new PlannerMissFixture(Set.of(PLANNER));

        assertThatThrownBy(() -> guard.read(() -> {
            loadOnCurrentSource(loads);
            throw miss;
        })).isSameAs(miss);
        assertThat(loads).containsExactly(SEOUL);
        assertThat(markerReads).containsExactly(List.of(PLANNER));
        assertThat(waits).isEmpty();
        assertThat(selector.current()).contains(seoul);
    }

    @Test
    void read_WhenAMissHasAnUnappliedMarker_LoadsAgain() throws Throwable {
        List<String> loads = new ArrayList<>();
        views.put(PLANNER, new MarkerView.At(APPLIED_FIVE));
        waitApplies = false;

        ScopedRead<?> served = guard.read(() -> {
            ScopedRead<String> read = scopedLoad(loads, Set.of(PLANNER));
            if (read.value().equals(SEOUL)) {
                throw new PlannerMissFixture(Set.of(PLANNER));
            }
            return read;
        });

        assertThat(served.value()).isEqualTo(ReadSourceProperties.PRIMARY);
        assertThat(loads).containsExactly(SEOUL, ReadSourceProperties.PRIMARY);
    }

    @Test
    void read_WhenATransactionIsActive_Refuses() {
        AtomicInteger loads = new AtomicInteger();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> guard.read(() -> new ScopedRead<>(loads.incrementAndGet(), Set.of(PLANNER))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("a freshness read must run outside a transaction");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(loads).hasValue(0);
    }

    @Test
    void read_WhenTheLoadIssuedNoStatement_DecidesAgainstTheCurrentSource() throws Throwable {
        AtomicInteger loads = new AtomicInteger();
        views.put(PLANNER, new MarkerView.At(APPLIED_FIVE));
        waitApplies = false;

        ScopedRead<?> served = guard.read(() -> new ScopedRead<>(loads.incrementAndGet(), Set.of(PLANNER)));

        assertThat(served.value()).isEqualTo(2);
        assertThat(waits).containsExactly(SEOUL + "|" + APPLIED_FIVE + "|" + WAIT_BOUND);
        assertThat(selector.current()).contains(primary);
    }

    @Test
    void read_WhenTheLoadFailsWithAnythingButAScopedMiss_RethrowsWithoutReadingMarkers() {
        IllegalArgumentException forbidden = new IllegalArgumentException("not the owner");

        assertThatThrownBy(() -> guard.read(() -> {
            throw forbidden;
        })).isSameAs(forbidden);
        assertThat(markerReads).isEmpty();
    }
}
