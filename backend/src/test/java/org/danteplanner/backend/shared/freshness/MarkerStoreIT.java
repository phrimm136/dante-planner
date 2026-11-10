package org.danteplanner.backend.shared.freshness;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.redis.testcontainers.RedisContainer;
import ch.qos.logback.classic.Level;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.config.BoundedRedisConnections;
import org.danteplanner.backend.shared.config.RedisConnectionConfig;
import org.danteplanner.backend.shared.redis.RedisRole;
import org.danteplanner.backend.shared.redis.RedisRoleHealth;
import org.danteplanner.backend.shared.redis.RedisRoleProbe;
import org.danteplanner.backend.support.Hang;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.backend.support.RedisEndpoints;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("containerized")
class MarkerStoreIT {

    private static final RedisContainer REDIS = new RedisContainer("redis:7-alpine");
    private static final String REFUSED_HOST = "127.0.0.1";
    private static final int REFUSED_PORT = 1;
    private static final Duration TTL = Duration.ofHours(1);
    // Wide enough that a loaded runner cannot turn a slow raise into a counted failure; no test here
    // asserts on the bound itself.
    private static final Duration WAIT_BOUND = Duration.ofSeconds(30);
    private static final int CONCURRENT_RAISES = 200;
    private static final int RAISING_THREADS = 8;

    private static LettuceConnectionFactory liveFactory;
    private static StringRedisTemplate live;
    private static LettuceConnectionFactory refusedFactory;
    private static StringRedisTemplate refused;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final List<MarkerStore> stores = new ArrayList<>();
    private String serverA;
    private String serverB;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        liveFactory = factory(REDIS.getRedisHost(), REDIS.getRedisPort());
        live = template(liveFactory);
        refusedFactory = factory(REFUSED_HOST, REFUSED_PORT);
        refused = template(refusedFactory);
    }

    @AfterAll
    static void stopRedis() {
        liveFactory.destroy();
        refusedFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        serverA = UUID.randomUUID().toString();
        serverB = UUID.randomUUID().toString();
    }

    @AfterEach
    void tearDown() {
        stores.forEach(MarkerStore::close);
    }

    private static LettuceConnectionFactory factory(String host, int port) {
        LettuceConnectionFactory factory =
                BoundedRedisConnections.connectionFactory(new RedisStandaloneConfiguration(host, port));
        factory.afterPropertiesSet();
        return factory;
    }

    private static StringRedisTemplate template(LettuceConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    private MarkerStore storeOver(LettuceConnectionFactory writeStore, List<MarkerStore.Source> readSources) {
        return storeOver(writeStore, readSources, new RedisRoleHealth());
    }

    private MarkerStore storeOver(
            LettuceConnectionFactory writeStore, List<MarkerStore.Source> readSources, RedisRoleHealth roleHealth) {
        MarkerStore store = new MarkerStore(writeStore, writeStore.getRequiredNativeClient().getResources(), readSources,
                roleHealth, new MarkerStore.Settings(0, WAIT_BOUND, TTL), meters);
        stores.add(store);
        return store;
    }

    private Map<Object, Object> marker(FreshnessScope scope) {
        return live.opsForHash().entries(scope.key());
    }

    @Test
    void raise_WhenWritersRaceOnOneScope_EndsAtTheHighestSequencePerServer() throws Exception {
        MarkerStore store = storeOver(liveFactory, List.of());
        FreshnessScope scope = new FreshnessScope.Entity(UUID.randomUUID());
        List<Integer> sequences = new ArrayList<>(IntStream.rangeClosed(1, CONCURRENT_RAISES).boxed().toList());
        Collections.shuffle(sequences);
        ExecutorService writers = Executors.newFixedThreadPool(RAISING_THREADS);
        try {
            List<Future<?>> raises = sequences.stream()
                    .<Future<?>>map(sequence -> writers.submit(() -> store.raise(
                            Set.of(scope), serverA + ":1-" + sequence + "," + serverB + ":" + (sequence * 2))))
                    .toList();
            for (Future<?> raise : raises) {
                Hang.get(raise, "a racing raise");
            }
        } finally {
            writers.shutdownNow();
        }

        assertThat(marker(scope)).containsOnly(
                Map.entry(serverA, String.valueOf(CONCURRENT_RAISES)),
                Map.entry(serverB, String.valueOf(CONCURRENT_RAISES * 2)));
        assertThat(meters.counter(MarkerStore.RAISE_FAILURES).count()).isZero();
    }

    @Test
    void raise_WhenALowerSequenceFollows_KeepsTheHigherOne() {
        MarkerStore store = storeOver(liveFactory, List.of());
        FreshnessScope scope = new FreshnessScope.Entity(UUID.randomUUID());

        store.raise(Set.of(scope), serverA + ":1-50");
        store.raise(Set.of(scope), serverA + ":1-49");

        assertThat(marker(scope)).containsOnly(Map.entry(serverA, "50"));
    }

    @Test
    void raise_WhenANewServerUuidArrives_AddsItsFieldBesideTheOthers() {
        MarkerStore store = storeOver(liveFactory, List.of());
        FreshnessScope scope = new FreshnessScope.OwnerList(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));

        store.raise(Set.of(scope), serverA + ":1-50");
        store.raise(Set.of(scope), serverB + ":1-3");

        assertThat(marker(scope)).containsOnly(Map.entry(serverA, "50"), Map.entry(serverB, "3"));
    }

    @Test
    void raise_WhenItRaisesSeveralScopes_RaisesEachAndExpiresEachWithinTheTtl() {
        MarkerStore store = storeOver(liveFactory, List.of());
        FreshnessScope entity = new FreshnessScope.Entity(UUID.randomUUID());
        FreshnessScope catalog = new FreshnessScope.PublicCatalog();

        store.raise(Set.of(entity, catalog), serverA + ":1-7");

        assertThat(marker(entity)).containsOnly(Map.entry(serverA, "7"));
        assertThat(marker(catalog)).contains(Map.entry(serverA, "7"));
        assertThat(live.getExpire(entity.key(), TimeUnit.MILLISECONDS)).isPositive().isLessThanOrEqualTo(TTL.toMillis());
        assertThat(live.getExpire(catalog.key(), TimeUnit.MILLISECONDS)).isPositive().isLessThanOrEqualTo(TTL.toMillis());
    }

    @Test
    void raise_WhenTheScriptCacheWasFlushedSinceTheConnectionOpened_ReloadsTheScriptAndRaises() {
        MarkerStore store = storeOver(liveFactory, List.of());
        FreshnessScope scope = new FreshnessScope.Entity(UUID.randomUUID());
        store.raise(Set.of(scope), serverA + ":1-3");
        live.execute((RedisCallback<Void>) connection -> {
            connection.scriptingCommands().scriptFlush();
            return null;
        });

        store.raise(Set.of(scope), serverA + ":1-4");

        assertThat(marker(scope)).containsOnly(Map.entry(serverA, "4"));
        assertThat(meters.counter(MarkerStore.RAISE_FAILURES).count()).isZero();
    }

    @Test
    void read_WhenAScopeHasAMarkerAndAnotherHasNone_IsAtForOneAndAbsentForTheOther() {
        MarkerStore store = storeOver(liveFactory, List.of(new MarkerStore.Source(RedisRole.AUTH, live)));
        FreshnessScope raised = new FreshnessScope.Entity(UUID.randomUUID());
        FreshnessScope untouched = new FreshnessScope.Entity(UUID.randomUUID());
        store.raise(Set.of(raised), serverA + ":1-4:6-9");

        assertThat(store.read(List.of(raised, untouched))).containsExactly(
                Map.entry(raised, new MarkerStore.MarkerView.At(serverA + ":1-9")),
                Map.entry(untouched, new MarkerStore.MarkerView.Absent()));
    }

    @Test
    void read_WhenTheLocalStoreIsDown_ServesFromTheAuthStore() {
        MarkerStore store = storeOver(liveFactory, MarkerStore.sourceOrder(false,
                new MarkerStore.Source(RedisRole.AUTH_LOCAL, refused), new MarkerStore.Source(RedisRole.AUTH, live)));
        FreshnessScope scope = new FreshnessScope.Entity(UUID.randomUUID());
        store.raise(Set.of(scope), serverA + ":1-5");

        try (LogCapture capture = LogCapture.onThisThread(MarkerStore.class)) {
            assertThat(store.read(List.of(scope)))
                    .containsExactly(Map.entry(scope, new MarkerStore.MarkerView.At(serverA + ":1-5")));
            assertThat(capture.at(Level.WARN)).as("the local store was tried and failed").hasSize(1);
        }
    }

    @Test
    void read_WhenTheLocalRoleIsUnavailable_ServesFromTheAuthStoreWithoutTryingTheLocalStore() {
        RedisConnectionConfig connections = RedisEndpoints.everyRoleAt(REDIS.getRedisHost(), REDIS.getRedisPort());
        RedisEndpoints.moveRole(connections, RedisRole.AUTH_LOCAL, REFUSED_HOST, REFUSED_PORT);
        RedisRoleHealth roleHealth = new RedisRoleHealth();
        try (RedisRoleProbe probe =
                new RedisRoleProbe(liveFactory.getRequiredNativeClient().getResources(), connections, roleHealth)) {
            probe.probeAtStartup();
            Hang.until(() -> {
                probe.probe();
                return roleHealth.isAvailable(RedisRole.AUTH);
            }, "the live auth role answering a probe");
        }
        assertThat(roleHealth.isAvailable(RedisRole.AUTH_LOCAL)).as("the refused local role").isFalse();
        MarkerStore store = storeOver(liveFactory, MarkerStore.sourceOrder(false,
                new MarkerStore.Source(RedisRole.AUTH_LOCAL, refused), new MarkerStore.Source(RedisRole.AUTH, live)),
                roleHealth);
        FreshnessScope scope = new FreshnessScope.Entity(UUID.randomUUID());
        store.raise(Set.of(scope), serverA + ":1-5");

        try (LogCapture capture = LogCapture.onThisThread(MarkerStore.class)) {
            assertThat(store.read(List.of(scope)))
                    .containsExactly(Map.entry(scope, new MarkerStore.MarkerView.At(serverA + ":1-5")));
            assertThat(capture.at(Level.WARN)).as("the local store was not tried").isEmpty();
        }
    }
}
