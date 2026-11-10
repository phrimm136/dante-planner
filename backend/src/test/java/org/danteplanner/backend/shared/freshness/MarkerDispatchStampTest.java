package org.danteplanner.backend.shared.freshness;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;
import org.danteplanner.backend.shared.readpath.ChosenReadSource;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkerDispatchStampTest {

    private static final String SEOUL = "seoul-replica";
    private static final String DISPATCH_GTID = "3e11fa47-71ca-11e1-9e33-c80aa9429562:7";

    private record Raise(Set<FreshnessScope> scopes, String gtidSet) {
    }

    private static final class RecordingMarkerStore extends MarkerStore {

        private final List<Raise> raises = new ArrayList<>();

        RecordingMarkerStore() {
            super(null, null, List.of(), new RedisRoleHealth(), new Settings(0, Duration.ofSeconds(1), Duration.ofHours(1), false),
                    new SimpleMeterRegistry());
        }

        @Override
        public void raise(Collection<FreshnessScope> scopes, String gtidSet) {
            if (scopes.isEmpty()) {
                return;
            }
            raises.add(new Raise(Set.copyOf(scopes), gtidSet));
        }
    }

    private GtidWriteCapture writeCapture;
    private RecordingMarkerStore markerStore;
    private MarkerDispatchStamp stamp;
    private ReadSourceSelector.Source seoul;
    private ReadSourceSelector selector;

    @BeforeEach
    void setUp() {
        clearThreadState();
        writeCapture = new GtidWriteCapture(new SimpleMeterRegistry());
        markerStore = new RecordingMarkerStore();
        stamp = new MarkerDispatchStamp(writeCapture,
                new MarkerStamp(writeCapture, markerStore, new SimpleMeterRegistry(), false));
        seoul = new ReadSourceSelector.Source(SEOUL, false, null, breaker(SEOUL));
        selector = new ReadSourceSelector(List.of(seoul, new ReadSourceSelector.Source(
                ReadSourceProperties.PRIMARY, true, null, breaker(ReadSourceProperties.PRIMARY))));
    }

    @AfterEach
    void tearDown() {
        clearThreadState();
    }

    @Test
    void around_WhenTheDispatchRuns_OpensTheWindowInsideAndClosesItAfter() {
        AtomicBoolean openInside = new AtomicBoolean();

        stamp.around(() -> openInside.set(FreshnessDeclarations.isWindowOpen() && writeCapture.isWindowOpen()));

        assertThat(openInside).isTrue();
        assertThat(FreshnessDeclarations.isWindowOpen()).isFalse();
        assertThat(writeCapture.isWindowOpen()).isFalse();
    }

    @Test
    void around_WhenTheDispatchThrows_StillClosesTheWindow() {
        assertThatThrownBy(() -> stamp.around(() -> {
            throw new IllegalArgumentException("dispatch failed");
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dispatch failed");

        assertThat(FreshnessDeclarations.isWindowOpen()).isFalse();
        assertThat(writeCapture.isWindowOpen()).isFalse();
    }

    @Test
    void around_WhenSkipsAreRecordedBeforeAndInside_SeesNoEarlierSkipAndLeavesNoInnerSkip() {
        ReadSourceSelector.skip(seoul);
        List<Optional<String>> currentInside = new ArrayList<>();

        stamp.around(() -> {
            currentInside.add(selector.current().map(ReadSourceSelector.Source::name));
            ReadSourceSelector.skip(seoul);
        });

        assertThat(currentInside).containsExactly(Optional.of(SEOUL));
        assertThat(selector.current()).map(ReadSourceSelector.Source::name).contains(SEOUL);
    }

    @Test
    void raise_WhenAGtidIsCapturedAndAUserScopeDeclared_RaisesItOnceAndNothingOnTheSecondCall() {
        stamp.around(() -> {
            writeCapture.recordCommit(DISPATCH_GTID, true);
            FreshnessDeclarations.declare(new FreshnessScope.User(7));
            stamp.raise();
            stamp.raise();
        });

        assertThat(markerStore.raises).containsExactly(
                new Raise(Set.of(new FreshnessScope.User(7)), DISPATCH_GTID + "-7"));
    }

    @Test
    void raise_WhenTheDeclarationIsNone_RaisesNothing() {
        stamp.around(() -> {
            writeCapture.recordCommit(DISPATCH_GTID, true);
            FreshnessDeclarations.declareNone("outbox bookkeeping");
            stamp.raise();
        });

        assertThat(markerStore.raises).isEmpty();
    }

    @Test
    void raise_WhenNoWindowIsOpen_RaisesNothing() {
        writeCapture.recordCommit(DISPATCH_GTID, true);
        FreshnessDeclarations.declare(new FreshnessScope.User(7));

        stamp.raise();

        assertThat(markerStore.raises).isEmpty();
    }

    @Test
    void around_WhenAStampWindowIsAlreadyOpen_ThrowsAndRunsNothing() {
        FreshnessDeclarations.begin();
        AtomicBoolean ran = new AtomicBoolean();

        assertThatThrownBy(() -> stamp.around(() -> ran.set(true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("a dispatch window cannot open inside another stamp window");
        assertThat(ran).isFalse();
    }

    @Test
    void around_WhenAStampWindowIsAlreadyOpen_LeavesTheOuterWindowAndItsDeclarationIntact() {
        FreshnessDeclarations.begin();
        FreshnessDeclarations.declare(new FreshnessScope.User(7));

        assertThatThrownBy(() -> stamp.around(() -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("a dispatch window cannot open inside another stamp window");

        assertThat(FreshnessDeclarations.isWindowOpen()).isTrue();
        assertThat(FreshnessDeclarations.declaration())
                .isEqualTo(new FreshnessDeclaration.Scopes(Set.of(new FreshnessScope.User(7))));
    }

    @Test
    void around_WhenAReadSourceWasChosenBeforeAndInside_SeesNoEarlierChoiceAndLeavesNoInnerChoice() {
        ChosenReadSource.record(SEOUL, false);
        List<Optional<String>> chosenInside = new ArrayList<>();

        stamp.around(() -> {
            chosenInside.add(ChosenReadSource.name());
            ChosenReadSource.record(SEOUL, false);
        });

        assertThat(chosenInside).containsExactly(Optional.empty());
        assertThat(ChosenReadSource.name()).isEmpty();
    }

    private static void clearThreadState() {
        FreshnessDeclarations.clear();
        ChosenReadSource.clear();
        ReadSourceSelector.clearSkips();
    }

    private static CircuitBreaker breaker(String name) {
        return CircuitBreaker.of(name, new ReadSourceProperties.Breaker().circuitBreakerConfig());
    }
}
