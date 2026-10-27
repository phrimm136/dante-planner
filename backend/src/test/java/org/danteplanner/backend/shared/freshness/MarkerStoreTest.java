package org.danteplanner.backend.shared.freshness;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.config.BoundedRedisConnections;
import org.danteplanner.backend.shared.config.RedisConnectionConfig;

import static org.assertj.core.api.Assertions.assertThat;

class MarkerStoreTest {

    private static final String REFUSED_HOST = "127.0.0.1";
    private static final int REFUSED_PORT = 1;
    private static final String SERVER_A = "3e11fa47-71ca-11e1-9e33-c80aa9429562";
    private static final String SERVER_B = "8f9e0d1c-2b3a-4c5d-6e7f-8a9b0c1d2e3f";
    private static final MarkerStore.Settings SETTINGS =
            new MarkerStore.Settings(1, Duration.ofSeconds(1), Duration.ofHours(1));
    private static final MarkerStore.Settings ROOMY_SETTINGS =
            new MarkerStore.Settings(1, Duration.ofSeconds(30), Duration.ofHours(1));

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private LettuceConnectionFactory refused;
    private List<MarkerStore.Source> sources;
    private MarkerStore store;

    @BeforeEach
    void setUp() {
        refused = BoundedRedisConnections.connectionFactory(new RedisStandaloneConfiguration(REFUSED_HOST, REFUSED_PORT));
        refused.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(refused);
        template.afterPropertiesSet();
        sources = List.of(source(MarkerStore.LOCAL_SOURCE, template), source(MarkerStore.AUTH_SOURCE, template));
        store = new MarkerStore(refused, refused.getRequiredNativeClient().getResources(), sources, SETTINGS, meters);
    }

    @AfterEach
    void tearDown() {
        store.close();
        refused.destroy();
    }

    private static MarkerStore.Source source(String name, StringRedisTemplate template) {
        RedisConnectionConfig.Endpoint endpoint = new RedisConnectionConfig.Endpoint();
        endpoint.setHost(REFUSED_HOST);
        endpoint.setPort(REFUSED_PORT);
        return new MarkerStore.Source(name, template, endpoint, CircuitBreaker.ofDefaults(name + UUID.randomUUID()));
    }

    @Test
    void highestSequences_WhenAUuidHasSeveralIntervals_KeepsItsHighestEnd() {
        assertThat(MarkerStore.highestSequences(SERVER_A + ":1-5:7-9:12," + SERVER_B + ":3"))
                .containsExactly(Map.entry(SERVER_A, 12L), Map.entry(SERVER_B, 3L));
    }

    @Test
    void highestSequences_WhenTheUuidIsUpperCase_KeysItLowerCase() {
        assertThat(MarkerStore.highestSequences(SERVER_A.toUpperCase(Locale.ROOT) + ":1-5"))
                .containsExactly(Map.entry(SERVER_A, 5L));
    }

    @Test
    void raiseArguments_WhenGivenATtlAndASet_LeadsWithTheTtlInMillisThenUuidSequencePairs() {
        assertThat(MarkerStore.raiseArguments(Duration.ofHours(1), SERVER_B + ":1-4," + SERVER_A + ":1-9"))
                .containsExactly("3600000", SERVER_A, "9", SERVER_B, "4");
    }

    @Test
    void viewOf_WhenTheHashIsEmpty_IsAbsent() {
        assertThat(MarkerStore.viewOf(Map.of())).isEqualTo(new MarkerStore.MarkerView.Absent());
    }

    @Test
    void viewOf_WhenTheHashHoldsSeveralServers_IsTheirGtidSetFromOneInUuidOrder() {
        assertThat(MarkerStore.viewOf(Map.of(SERVER_B, "4", SERVER_A, "9")))
                .isEqualTo(new MarkerStore.MarkerView.At(SERVER_A + ":1-9," + SERVER_B + ":1-4"));
    }

    @Test
    void sourceOrder_WhenTheRegionReadsAReplicaFirst_ReadsTheLocalStoreThenTheAuthStore() {
        MarkerStore.Source local = source(MarkerStore.LOCAL_SOURCE, null);
        MarkerStore.Source auth = source(MarkerStore.AUTH_SOURCE, null);

        assertThat(MarkerStore.sourceOrder(false, local, auth)).containsExactly(local, auth);
    }

    @Test
    void sourceOrder_WhenTheRegionReadsThePrimaryFirst_ReadsOnlyTheAuthStore() {
        MarkerStore.Source local = source(MarkerStore.LOCAL_SOURCE, null);
        MarkerStore.Source auth = source(MarkerStore.AUTH_SOURCE, null);

        assertThat(MarkerStore.sourceOrder(true, local, auth)).containsExactly(auth);
    }

    @Test
    void raise_WhenNoScopeIsDeclared_TouchesNoStore() {
        store.raise(Set.of(), SERVER_A + ":1-3");

        assertThat(meters.counter(MarkerStore.RAISE_FAILURES).count()).isZero();
    }

    @Test
    void raise_WhenTheWriteStoreIsDown_CountsAFailureWithoutThrowing() {
        store.raise(Set.of(new FreshnessScope.PublicCatalog()), SERVER_A + ":1-3");

        assertThat(meters.counter(MarkerStore.RAISE_FAILURES).count()).isEqualTo(1.0);
        assertThat(meters.counter(MarkerStore.WAIT_TIMEOUTS).count()).isZero();
    }

    @Test
    void raise_WhenTheStoreIsClosed_CountsAFailureWithoutWaitingForTheBound() {
        MarkerStore roomy =
                new MarkerStore(refused, refused.getRequiredNativeClient().getResources(), sources, ROOMY_SETTINGS, meters);
        roomy.close();

        long start = System.nanoTime();
        roomy.raise(Set.of(new FreshnessScope.PublicCatalog()), SERVER_A + ":1-3");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isLessThan(ROOMY_SETTINGS.waitBound().dividedBy(2));
        assertThat(meters.counter(MarkerStore.RAISE_FAILURES).count()).isEqualTo(1.0);
    }

    @Test
    void read_WhenEverySourceErrors_IsUnknownForEveryScope() {
        FreshnessScope entity = new FreshnessScope.Entity(UUID.randomUUID());
        FreshnessScope catalog = new FreshnessScope.PublicCatalog();

        assertThat(store.read(List.of(entity, catalog))).containsExactly(
                Map.entry(entity, new MarkerStore.MarkerView.Unknown()),
                Map.entry(catalog, new MarkerStore.MarkerView.Unknown()));
    }

    @Test
    void probe_WhenASourceFailsOnce_KeepsItsBreakerClosed() {
        store.probe();

        assertThat(breakerStates()).containsOnly(CircuitBreaker.State.CLOSED);
    }

    @Test
    void probe_WhenASourceFailsTwiceInARow_OpensItsBreaker() {
        store.probe();
        store.probe();

        assertThat(breakerStates()).containsOnly(CircuitBreaker.State.OPEN);
    }

    @Test
    void settings_WhenTheWaitBoundReachesTheCommandTimeout_AreInvalid() {
        assertThat(new MarkerStore.Settings(1, BoundedRedisConnections.COMMAND_TIMEOUT, Duration.ofHours(1))
                .isWaitBoundUnderTheCommandTimeout()).isFalse();
        assertThat(SETTINGS.isWaitBoundUnderTheCommandTimeout()).isTrue();
    }

    private List<CircuitBreaker.State> breakerStates() {
        return sources.stream().map(source -> source.breaker().getState()).toList();
    }
}
