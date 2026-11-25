package org.danteplanner.backend.shared.freshness;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.danteplanner.backend.shared.failure.OnUndeclared;
import org.danteplanner.backend.shared.gtid.GtidWriteCapture;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkerStampTest {

    private static final String ON_UNDECLARED = "freshness.marker.on-undeclared";
    private static final String SOURCE = "3e11fa47-71ca-11e1-9e33-c80aa9429562";
    private static final String TRACKED_GTID = SOURCE + ":7";
    private static final String FALLBACK_GTID = SOURCE + ":1-6";

    private record Raise(Set<FreshnessScope> scopes, String gtidSet) {
    }

    private static final class RecordingMarkerStore extends MarkerStore {

        private final List<Raise> raises = new ArrayList<>();

        RecordingMarkerStore() {
            super(null, null, List.of(), new RedisRoleHealth(), new Settings(0, Duration.ofSeconds(1), Duration.ofHours(1), OnUndeclared.COUNT),
                    new SimpleMeterRegistry());
        }

        @Override
        public void raise(Collection<FreshnessScope> scopes, String gtidSet) {
            raises.add(new Raise(Set.copyOf(scopes), gtidSet));
        }
    }

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final GtidWriteCapture writeCapture = new GtidWriteCapture(meters);
    private final RecordingMarkerStore markerStore = new RecordingMarkerStore();

    @BeforeEach
    void openWindow() {
        writeCapture.begin();
        FreshnessDeclarations.begin();
    }

    @AfterEach
    void closeWindow() {
        writeCapture.clear();
        FreshnessDeclarations.clear();
    }

    private MarkerStamp stamp(OnUndeclared onUndeclared) {
        return new MarkerStamp(writeCapture, markerStore, meters, onUndeclared);
    }

    private double undeclared() {
        return meters.counter(MarkerStamp.UNDECLARED_COUNTER).count();
    }

    @Test
    void stamp_WhenFailFastAndATrackedCommitDeclaredNothing_ThrowsAndRaisesNothing() {
        MarkerStamp stamp = stamp(OnUndeclared.FAIL);
        writeCapture.recordCommit(TRACKED_GTID, true);

        assertThatThrownBy(stamp::stamp)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("a window committed a write and declared no freshness scope: call "
                        + "FreshnessDeclarations.declare(...) or declareNone(reason) in the service that wrote");
        assertThat(markerStore.raises).isEmpty();
        assertThat(undeclared()).isZero();
    }

    @Test
    void stamp_WhenNotFailFastAndATrackedCommitDeclaredNothing_CountsIt() {
        MarkerStamp stamp = stamp(OnUndeclared.COUNT);
        writeCapture.recordCommit(TRACKED_GTID, true);

        stamp.stamp();

        assertThat(markerStore.raises).isEmpty();
        assertThat(undeclared()).isEqualTo(1.0);
    }

    @Test
    void stamp_WhenNothingWasCaptured_RaisesAndCountsNothing() {
        MarkerStamp stamp = stamp(OnUndeclared.FAIL);

        stamp.stamp();

        assertThat(markerStore.raises).isEmpty();
        assertThat(undeclared()).isZero();
    }

    @Test
    void stamp_WhenScopesAreDeclared_RaisesThemToTheGtidOnceAndNothingOnTheSecondStamp() {
        MarkerStamp stamp = stamp(OnUndeclared.FAIL);
        FreshnessScope user = new FreshnessScope.User(7);
        writeCapture.recordCommit(TRACKED_GTID, true);
        FreshnessDeclarations.declare(user);

        stamp.stamp();
        stamp.stamp();

        assertThat(markerStore.raises).containsExactly(new Raise(Set.of(user), SOURCE + ":7-7"));
    }

    @Test
    void stamp_WhenNoneIsDeclaredInFailFast_RaisesNothing() {
        MarkerStamp stamp = stamp(OnUndeclared.FAIL);
        writeCapture.recordCommit(TRACKED_GTID, true);
        FreshnessDeclarations.declareNone("no read renders it");

        stamp.stamp();

        assertThat(markerStore.raises).isEmpty();
        assertThat(undeclared()).isZero();
    }

    @Test
    void stamp_WhenOnlyTheFallbackCapturedAndNothingIsDeclaredInFailFast_RaisesAndCountsNothing() {
        MarkerStamp stamp = stamp(OnUndeclared.FAIL);
        writeCapture.recordCommit(FALLBACK_GTID, false);

        stamp.stamp();

        assertThat(markerStore.raises).isEmpty();
        assertThat(undeclared()).isZero();
    }

    @Test
    void stamp_WhenOnlyTheFallbackCapturedAndNothingIsDeclaredOutsideFailFast_RaisesAndCountsNothing() {
        MarkerStamp stamp = stamp(OnUndeclared.COUNT);
        writeCapture.recordCommit(FALLBACK_GTID, false);

        stamp.stamp();

        assertThat(markerStore.raises).isEmpty();
        assertThat(undeclared()).isZero();
    }

    @Test
    void stamp_WhenAFallbackAndATrackedCommitDeclaredNothingInFailFast_Throws() {
        MarkerStamp stamp = stamp(OnUndeclared.FAIL);
        writeCapture.recordCommit(FALLBACK_GTID, false);
        writeCapture.recordCommit(TRACKED_GTID, true);

        assertThatThrownBy(stamp::stamp).isInstanceOf(IllegalStateException.class)
                .hasMessage(MarkerStamp.UNDECLARED_MESSAGE);
        assertThat(markerStore.raises).isEmpty();
    }

    private static ApplicationContextRunner settings() {
        return new ApplicationContextRunner()
                .withUserConfiguration(SettingsConfiguration.class)
                .withPropertyValues("freshness.marker.wait-bound=PT1S", "freshness.marker.ttl=PT1H");
    }

    private MarkerStamp configuredStamp(MarkerStore.Settings settings) {
        return new FreshnessConfig().markerStamp(writeCapture, markerStore, settings, meters);
    }

    @Test
    void onUndeclared_WhenThePropertyIsFail_Throws() {
        settings().withPropertyValues(ON_UNDECLARED + "=fail").run(context -> {
            writeCapture.recordCommit(TRACKED_GTID, true);

            assertThatThrownBy(configuredStamp(context.getBean(MarkerStore.Settings.class))::stamp)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(MarkerStamp.UNDECLARED_MESSAGE);
        });
    }

    @Test
    void onUndeclared_WhenThePropertyIsCount_Counts() {
        settings().withPropertyValues(ON_UNDECLARED + "=count").run(context -> {
            writeCapture.recordCommit(TRACKED_GTID, true);

            configuredStamp(context.getBean(MarkerStore.Settings.class)).stamp();

            assertThat(undeclared()).isEqualTo(1.0);
        });
    }

    @Test
    void onUndeclared_WhenThePropertyIsMissing_IsCount() {
        settings().run(context -> assertThat(context.getBean(MarkerStore.Settings.class).onUndeclared())
                .isEqualTo(OnUndeclared.COUNT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "yes", "refuse"})
    void onUndeclared_WhenThePropertyNamesNoPolicy_FailsStartup(String value) {
        settings().withPropertyValues(ON_UNDECLARED + "=" + value)
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties(MarkerStore.Settings.class)
    static class SettingsConfiguration {
    }
}
