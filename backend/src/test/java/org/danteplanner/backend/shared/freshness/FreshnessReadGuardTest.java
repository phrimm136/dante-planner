package org.danteplanner.backend.shared.freshness;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.exception.EntityNotFoundException;
import org.danteplanner.backend.shared.freshness.MarkerStore.MarkerView;
import org.danteplanner.backend.shared.gtid.GtidReadGate;
import org.danteplanner.backend.shared.readpath.ChosenReadSource;

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
    private static final FreshnessScope ENTITY = new FreshnessScope.Entity(UUID.randomUUID());
    private static final FreshnessScope OWNER_LIST = new FreshnessScope.OwnerList(7L);

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
        seoul = new ReadSourceSelector.Source(SEOUL, false, seoulPool, breaker(SEOUL));
        primary = new ReadSourceSelector.Source(
                ReadSourceProperties.PRIMARY, true, primaryPool, breaker(ReadSourceProperties.PRIMARY));
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
        return CircuitBreaker.of(name, new ReadSourceProperties.Breaker().circuitBreakerConfig());
    }

    private FreshnessReadGuard guardWaiting(FreshnessReadGuard.GtidWait gtidWait) {
        return new FreshnessReadGuard(scopes -> {
            markerReads.add(List.copyOf(scopes));
            return views;
        }, gtidWait, new FreshnessReadSettings(WAIT_BOUND), meters, selector);
    }

    private String loadOnCurrentSource(List<String> loads) {
        ReadSourceSelector.Source source = selector.current().orElseThrow();
        ChosenReadSource.record(source.name(), source.authoritative());
        loads.add(source.name());
        return source.name();
    }

    private double waitSamples(String outcome) {
        Timer timer = meters.find(FreshnessReadGuard.READ_WAIT)
                .tag(FreshnessReadGuard.SOURCE_TAG, SEOUL)
                .tag(FreshnessReadGuard.OUTCOME_TAG, outcome)
                .timer();
        return timer == null ? 0 : timer.count();
    }

    @Test
    void decide_WhenEverySourceMarkerIsAbsent_ServesWithoutWaiting() {
        views.put(ENTITY, new MarkerView.Absent());
        views.put(OWNER_LIST, new MarkerView.Absent());

        assertThat(guard.decide(List.of(ENTITY, OWNER_LIST), seoul)).isEqualTo(new FreshnessReadGuard.Decision.Serve());
        assertThat(markerReads).containsExactly(List.of(ENTITY, OWNER_LIST));
        assertThat(waits).isEmpty();
        assertThat(selector.current()).contains(seoul);
    }

    @Test
    void decide_WhenMarkerAppliedPerCache_IssuesNoWait() {
        views.put(ENTITY, new MarkerView.At(APPLIED_TEN));
        waitApplies = true;
        guard.decide(List.of(ENTITY), seoul);
        views.put(ENTITY, new MarkerView.At(APPLIED_FIVE));
        waitApplies = false;

        assertThat(guard.decide(List.of(ENTITY), seoul)).isEqualTo(new FreshnessReadGuard.Decision.Serve());
        assertThat(waits).containsExactly(SEOUL + "|" + UUID_A + ":1-10|" + WAIT_BOUND);
    }

    @Test
    void decide_WhenWaitApplies_ServesAndCaches() {
        views.put(ENTITY, new MarkerView.At(APPLIED_FIVE));
        views.put(OWNER_LIST, new MarkerView.At(APPLIED_TEN));
        waitApplies = true;

        assertThat(guard.decide(List.of(ENTITY, OWNER_LIST), seoul))
                .isEqualTo(new FreshnessReadGuard.Decision.Serve());
        waitApplies = false;
        assertThat(guard.decide(List.of(ENTITY, OWNER_LIST), seoul))
                .isEqualTo(new FreshnessReadGuard.Decision.Serve());

        assertThat(waits).containsExactly(SEOUL + "|" + UUID_A + ":1-10|" + WAIT_BOUND);
        assertThat(waitSamples(FreshnessReadGuard.APPLIED)).isEqualTo(1);
        assertThat(waitSamples(FreshnessReadGuard.TIMEOUT)).isZero();
        assertThat(selector.current()).contains(seoul);
    }

    @Test
    void decide_WhenWaitTimesOut_SkipsTheSource() {
        views.put(ENTITY, new MarkerView.At(APPLIED_FIVE));
        waitApplies = false;

        assertThat(guard.decide(List.of(ENTITY), seoul)).isEqualTo(new FreshnessReadGuard.Decision.Skip(seoul));
        assertThat(waitSamples(FreshnessReadGuard.TIMEOUT)).isEqualTo(1);
        assertThat(waitSamples(FreshnessReadGuard.APPLIED)).isZero();
        assertThat(selector.current()).contains(primary);
    }

    @Test
    void decide_WhenTheWaitConnectionFails_SkipsTheSourceWithoutThrowing() throws SQLException {
        when(seoulPool.getConnection()).thenThrow(new SQLException("Communications link failure", "08S01"));
        FreshnessReadGuard realWait = guardWaiting(GtidReadGate::isApplied);
        views.put(ENTITY, new MarkerView.At(APPLIED_FIVE));

        assertThat(realWait.decide(List.of(ENTITY), seoul)).isEqualTo(new FreshnessReadGuard.Decision.Skip(seoul));
        assertThat(waitSamples(FreshnessReadGuard.TIMEOUT)).isEqualTo(1);
        assertThat(seoul.breaker().getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(selector.current()).contains(primary);
    }

    @Test
    void decide_WhenAnyViewIsUnknown_SkipsTheSource() {
        views.put(ENTITY, new MarkerView.At(APPLIED_FIVE));
        views.put(OWNER_LIST, new MarkerView.Unknown());
        waitApplies = true;

        assertThat(guard.decide(List.of(ENTITY, OWNER_LIST), seoul))
                .isEqualTo(new FreshnessReadGuard.Decision.Skip(seoul));
        assertThat(waits).isEmpty();
        assertThat(selector.current()).contains(primary);
    }

    @Test
    void decide_WhenSourceIsAuthoritative_ServesWithoutReadingMarkers() {
        views.put(ENTITY, new MarkerView.Unknown());

        assertThat(guard.decide(List.of(ENTITY), primary)).isEqualTo(new FreshnessReadGuard.Decision.Serve());
        assertThat(markerReads).isEmpty();
        assertThat(waits).isEmpty();
    }

    @Test
    void read_WhenServedByAuthority_ReadsNoMarkers() {
        ReadSourceSelector.skip(seoul);
        List<String> loads = new ArrayList<>();
        views.put(ENTITY, new MarkerView.Unknown());

        String served = guard.read(List.of(ENTITY), () -> loadOnCurrentSource(loads), value -> List.of(ENTITY));

        assertThat(served).isEqualTo(ReadSourceProperties.PRIMARY);
        assertThat(loads).containsExactly(ReadSourceProperties.PRIMARY);
        assertThat(markerReads).isEmpty();
    }

    @Test
    void read_WhenMarkersApplied_ReturnsTheFirstLoad() {
        List<String> loads = new ArrayList<>();
        views.put(ENTITY, new MarkerView.At(APPLIED_FIVE));
        waitApplies = true;

        String served = guard.read(List.of(), () -> loadOnCurrentSource(loads), value -> List.of(ENTITY));

        assertThat(served).isEqualTo(SEOUL);
        assertThat(loads).containsExactly(SEOUL);
        assertThat(markerReads).containsExactly(List.of(ENTITY));
    }

    @Test
    void read_WhenTheSourceIsSkipped_LoadsAgainOnTheNextSource() {
        List<String> loads = new ArrayList<>();
        views.put(ENTITY, new MarkerView.At(APPLIED_FIVE));
        waitApplies = false;

        String served = guard.read(List.of(), () -> loadOnCurrentSource(loads), value -> List.of(ENTITY));

        assertThat(served).isEqualTo(ReadSourceProperties.PRIMARY);
        assertThat(loads).containsExactly(SEOUL, ReadSourceProperties.PRIMARY);
        assertThat(markerReads).containsExactly(List.of(ENTITY));
    }

    @Test
    void read_WhenAMissHasNoMarker_IsAFinal404() {
        List<String> loads = new ArrayList<>();
        views.put(ENTITY, new MarkerView.Absent());
        EntityNotFoundException miss = new EntityNotFoundException("planner", UUID.randomUUID());

        assertThatThrownBy(() -> guard.read(List.of(ENTITY), () -> {
            loadOnCurrentSource(loads);
            throw miss;
        }, value -> List.of())).isSameAs(miss);
        assertThat(loads).containsExactly(SEOUL);
        assertThat(markerReads).containsExactly(List.of(ENTITY));
        assertThat(waits).isEmpty();
        assertThat(selector.current()).contains(seoul);
    }

    @Test
    void read_WhenAMissHasAnUnappliedMarker_LoadsAgain() {
        List<String> loads = new ArrayList<>();
        views.put(ENTITY, new MarkerView.At(APPLIED_FIVE));
        waitApplies = false;

        String served = guard.read(List.of(ENTITY), () -> {
            String source = loadOnCurrentSource(loads);
            if (source.equals(SEOUL)) {
                throw new EntityNotFoundException("planner", UUID.randomUUID());
            }
            return source;
        }, value -> List.of(ENTITY));

        assertThat(served).isEqualTo(ReadSourceProperties.PRIMARY);
        assertThat(loads).containsExactly(SEOUL, ReadSourceProperties.PRIMARY);
    }

    @Test
    void read_WhenATransactionIsActive_Refuses() {
        AtomicInteger loads = new AtomicInteger();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> guard.read(List.of(ENTITY), loads::incrementAndGet, value -> List.of(ENTITY)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("a freshness read must run outside a transaction");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(loads).hasValue(0);
    }

    @Test
    void read_WhenTheLoadIssuedNoStatement_DecidesAgainstTheCurrentSource() {
        AtomicInteger loads = new AtomicInteger();
        views.put(ENTITY, new MarkerView.At(APPLIED_FIVE));
        waitApplies = false;

        int served = guard.read(List.of(), loads::incrementAndGet, value -> List.of(ENTITY));

        assertThat(served).isEqualTo(2);
        assertThat(waits).containsExactly(SEOUL + "|" + APPLIED_FIVE + "|" + WAIT_BOUND);
        assertThat(selector.current()).contains(primary);
    }

    @Test
    void read_WhenTheLoadFailsWithAnythingButANotFound_RethrowsWithoutReadingMarkers() {
        IllegalArgumentException forbidden = new IllegalArgumentException("not the owner");

        assertThatThrownBy(() -> guard.read(List.of(ENTITY), () -> {
            throw forbidden;
        }, value -> List.of(ENTITY))).isSameAs(forbidden);
        assertThat(markerReads).isEmpty();
    }
}
