package org.danteplanner.backend.shared.freshness;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.springframework.data.redis.connection.RedisPassword;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.toxiproxy.ToxiproxyContainer;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.danteplanner.backend.shared.config.BoundedRedisConnections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("containerized")
class MarkerStoreReplicationIT {

    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final String TOXIPROXY_IMAGE = "ghcr.io/shopify/toxiproxy:2.5.0";
    private static final int REDIS_PORT = 6379;
    private static final int LINK_PROXY_PORT = 8666;
    private static final int CLIENT_PROXY_PORT = 8667;
    private static final String PRIMARY_ALIAS = "marker-primary";
    private static final String TOXIPROXY_ALIAS = "marker-toxiproxy";
    private static final String LINK_PROXY_NAME = "marker-replication";
    private static final String CLIENT_PROXY_NAME = "marker-client";
    private static final String ACK_HOLD = "marker-ack-hold";
    private static final String BLACK_HOLE = "marker-black-hole";
    private static final String PASSWORD = "marker-store-secret";
    private static final Duration WAIT_BOUND = Duration.ofSeconds(1);
    private static final Duration LINK_TIMEOUT = Duration.ofSeconds(30);
    private static final String LINK_STATUS = "master_link_status";
    private static final String LINK_UP = "up";
    private static final String BLOCKED_CLIENTS = "blocked_clients";
    private static final String ONE = "1";
    private static final String CONNECTIONS_RECEIVED = "total_connections_received";
    private static final String WAIT_STATS = "cmdstat_wait";
    private static final String CALLS_PREFIX = "calls=";
    private static final int BURST_RAISES = 50;
    private static final int STALLED_RAISES = 8;
    private static final Duration SCHEDULING_SLACK = Duration.ofMillis(250);

    private static final Network NETWORK = Network.newNetwork();

    @SuppressWarnings("resource")
    private static final GenericContainer<?> PRIMARY = new GenericContainer<>(REDIS_IMAGE)
            .withNetwork(NETWORK)
            .withNetworkAliases(PRIMARY_ALIAS)
            .withExposedPorts(REDIS_PORT)
            .withCommand("redis-server", "--requirepass", PASSWORD);

    @SuppressWarnings("resource")
    private static final ToxiproxyContainer TOXIPROXY = new ToxiproxyContainer(TOXIPROXY_IMAGE)
            .withNetwork(NETWORK)
            .withNetworkAliases(TOXIPROXY_ALIAS);

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REPLICA = new GenericContainer<>(REDIS_IMAGE)
            .withNetwork(NETWORK)
            .withExposedPorts(REDIS_PORT)
            .withCommand("redis-server", "--replicaof", TOXIPROXY_ALIAS, String.valueOf(LINK_PROXY_PORT),
                    "--masterauth", PASSWORD, "--requirepass", PASSWORD);

    private static Proxy replicationLink;
    private static Proxy clientLink;
    private static LettuceConnectionFactory proxiedFactory;
    private static LettuceConnectionFactory primaryFactory;
    private static StringRedisTemplate primary;
    private static LettuceConnectionFactory replicaFactory;
    private static StringRedisTemplate replica;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private MarkerStore store;
    private String server;

    @BeforeAll
    static void startRedis() throws IOException {
        PRIMARY.start();
        TOXIPROXY.start();
        ToxiproxyClient toxiproxy = new ToxiproxyClient(TOXIPROXY.getHost(), TOXIPROXY.getControlPort());
        replicationLink = toxiproxy
                .createProxy(LINK_PROXY_NAME, "0.0.0.0:" + LINK_PROXY_PORT, PRIMARY_ALIAS + ":" + REDIS_PORT);
        clientLink = toxiproxy
                .createProxy(CLIENT_PROXY_NAME, "0.0.0.0:" + CLIENT_PROXY_PORT, PRIMARY_ALIAS + ":" + REDIS_PORT);
        REPLICA.start();
        primaryFactory = factory(PRIMARY.getHost(), PRIMARY.getMappedPort(REDIS_PORT));
        proxiedFactory = factory(TOXIPROXY.getHost(), TOXIPROXY.getMappedPort(CLIENT_PROXY_PORT));
        primary = template(primaryFactory);
        replicaFactory = factory(REPLICA.getHost(), REPLICA.getMappedPort(REDIS_PORT));
        replica = template(replicaFactory);
    }

    @AfterAll
    static void stopRedis() {
        primaryFactory.destroy();
        proxiedFactory.destroy();
        replicaFactory.destroy();
        REPLICA.stop();
        TOXIPROXY.stop();
        PRIMARY.stop();
        NETWORK.close();
    }

    @BeforeEach
    void setUp() {
        server = UUID.randomUUID().toString();
        store = storeOver(primaryFactory);
        await().atMost(LINK_TIMEOUT).until(() -> LINK_UP.equals(info(replica, "replication").getProperty(LINK_STATUS)));
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private MarkerStore storeOver(LettuceConnectionFactory writeStore) {
        return new MarkerStore(writeStore, writeStore.getRequiredNativeClient().getResources(), List.of(),
                new MarkerStore.Settings(1, WAIT_BOUND, Duration.ofHours(1)), meters);
    }

    private static LettuceConnectionFactory factory(String host, int port) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(host, port);
        configuration.setPassword(RedisPassword.of(PASSWORD));
        LettuceConnectionFactory factory = BoundedRedisConnections.connectionFactory(configuration);
        factory.afterPropertiesSet();
        return factory;
    }

    private static StringRedisTemplate template(LettuceConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }

    private static Properties info(StringRedisTemplate template, String section) {
        return template.execute((RedisCallback<Properties>) connection -> connection.serverCommands().info(section));
    }

    private static long connectionsReceived() {
        return Long.parseLong(info(primary, "stats").getProperty(CONNECTIONS_RECEIVED));
    }

    private static long waitCalls() {
        String stats = info(primary, "commandstats").getProperty(WAIT_STATS);
        if (stats == null) {
            return 0;
        }
        String calls = stats.substring(stats.indexOf(CALLS_PREFIX) + CALLS_PREFIX.length());
        return Long.parseLong(calls.substring(0, calls.indexOf(',')));
    }

    private static List<Long> raiseConcurrently(MarkerStore raising, List<FreshnessScope> scopes, String gtidSet)
            throws Exception {
        ExecutorService requests = Executors.newFixedThreadPool(scopes.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Long>> raises = scopes.stream()
                    .map(scope -> requests.submit(() -> {
                        start.await();
                        long begin = System.nanoTime();
                        raising.raise(Set.of(scope), gtidSet);
                        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
                    }))
                    .toList();
            start.countDown();
            List<Long> elapsedMillis = new ArrayList<>();
            for (Future<Long> raise : raises) {
                elapsedMillis.add(raise.get(BoundedRedisConnections.COMMAND_TIMEOUT.toMillis() * 2,
                        TimeUnit.MILLISECONDS));
            }
            return elapsedMillis;
        } finally {
            requests.shutdownNow();
        }
    }

    private static List<FreshnessScope> distinctScopes(int count) {
        return IntStream.range(0, count)
                .<FreshnessScope>mapToObj(i -> new FreshnessScope.Entity(UUID.randomUUID()))
                .toList();
    }

    private static void holdReplicaAcks() throws IOException {
        replicationLink.toxics().timeout(ACK_HOLD, ToxicDirection.UPSTREAM, 0);
    }

    private static void releaseReplicaAcks() throws IOException {
        replicationLink.toxics().get(ACK_HOLD).remove();
    }

    @Test
    void raise_WhenTheReplicaAcknowledges_ReturnsWithTheMarkerAlreadyOnTheReplicaAndCountsNoTimeout() {
        FreshnessScope scope = new FreshnessScope.Entity(UUID.randomUUID());

        store.raise(Set.of(scope), server + ":1-11");

        assertThat(replica.opsForHash().entries(scope.key())).containsOnly(Map.entry(server, "11"));
        assertThat(meters.counter(MarkerStore.WAIT_TIMEOUTS).count()).isZero();
        assertThat(meters.counter(MarkerStore.RAISE_FAILURES).count()).isZero();
    }

    @Test
    void raise_WhenTheReplicaStopsAcknowledging_ReturnsAfterTheBoundAndCountsOneTimeout() throws IOException {
        FreshnessScope scope = new FreshnessScope.Entity(UUID.randomUUID());
        holdReplicaAcks();
        long elapsedMillis;
        try {
            long start = System.nanoTime();
            store.raise(Set.of(scope), server + ":1-12");
            elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        } finally {
            releaseReplicaAcks();
        }

        assertThat(elapsedMillis)
                .as("a WAIT on a connection that wrote nothing answers at once while the replica stays "
                        + "connected; only the raise's own connection waits out the bound")
                .isGreaterThanOrEqualTo(WAIT_BOUND.toMillis())
                .isLessThan(BoundedRedisConnections.COMMAND_TIMEOUT.toMillis());
        assertThat(meters.counter(MarkerStore.WAIT_TIMEOUTS).count()).isEqualTo(1.0);
        assertThat(meters.counter(MarkerStore.RAISE_FAILURES).count()).isZero();
        assertThat(primary.opsForHash().entries(scope.key())).containsOnly(Map.entry(server, "12"));
    }

    @Test
    void raise_WhenItWaitsOnAStalledReplica_LeavesTheSharedConnectionAnswering() throws Exception {
        FreshnessScope scope = new FreshnessScope.Entity(UUID.randomUUID());
        holdReplicaAcks();
        try {
            CompletableFuture<Void> raise =
                    CompletableFuture.runAsync(() -> store.raise(Set.of(scope), server + ":1-13"));
            await().atMost(WAIT_BOUND).until(() -> ONE.equals(info(primary, "clients").getProperty(BLOCKED_CLIENTS)));

            long start = System.nanoTime();
            Map<Object, Object> duringTheWait = primary.opsForHash().entries(scope.key());
            long sharedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            raise.get(BoundedRedisConnections.COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(sharedMillis).isLessThan(WAIT_BOUND.toMillis() / 2);
            assertThat(duringTheWait).containsOnly(Map.entry(server, "13"));
        } finally {
            releaseReplicaAcks();
        }
    }

    @Test
    void raise_WhenFiftyRequestsRaiseAtOnce_ShareOneConnectionAndFewerWaitsThanRaises() throws Exception {
        List<FreshnessScope> scopes = distinctScopes(BURST_RAISES);
        long connectionsBefore = connectionsReceived();
        long waitsBefore = waitCalls();

        raiseConcurrently(store, scopes, server + ":1-21");

        long connections = connectionsReceived() - connectionsBefore;
        long waits = waitCalls() - waitsBefore;
        assertThat(connections).as("connections opened for %d raises", BURST_RAISES).isEqualTo(1);
        assertThat(waits).as("WAITs sent for %d raises", BURST_RAISES).isPositive().isLessThan(BURST_RAISES);
        assertThat(meters.counter(MarkerStore.RAISE_FAILURES).count()).isZero();
        assertThat(meters.counter(MarkerStore.WAIT_TIMEOUTS).count()).isZero();
        assertThat(scopes).allSatisfy(scope ->
                assertThat(replica.opsForHash().entries(scope.key())).containsOnly(Map.entry(server, "21")));
    }

    @Test
    void raise_WhenConcurrentRaisesMeetAStalledReplica_EachReturnsWithinTheBoundAndTimeoutsCountPerBatch()
            throws Exception {
        List<FreshnessScope> scopes = distinctScopes(STALLED_RAISES);
        List<Long> elapsedMillis;
        holdReplicaAcks();
        try {
            elapsedMillis = raiseConcurrently(store, scopes, server + ":1-22");
        } finally {
            releaseReplicaAcks();
        }

        assertThat(elapsedMillis).allSatisfy(millis -> assertThat(millis)
                .isLessThan(WAIT_BOUND.plus(MarkerStore.RAISE_MARGIN).plus(SCHEDULING_SLACK).toMillis()));
        assertThat(meters.counter(MarkerStore.WAIT_TIMEOUTS).count())
                .as("one timeout per batch, so fewer than one per raise")
                .isPositive()
                .isLessThan(STALLED_RAISES);
        assertThat(meters.counter(MarkerStore.RAISE_FAILURES).count())
                .as("a raise whose batch lands after its caller stopped waiting is not a raise failure")
                .isZero();
        assertThat(scopes).allSatisfy(scope ->
                assertThat(primary.opsForHash().entries(scope.key())).containsOnly(Map.entry(server, "22")));
    }

    @Test
    void raise_WhenThePrimaryIsBlackHoledThenReturns_FailsWithinTheBoundThenReconnectsAndRaises() throws Exception {
        MarkerStore proxied = storeOver(proxiedFactory);
        try {
            FreshnessScope beforeTheOutage = new FreshnessScope.Entity(UUID.randomUUID());
            FreshnessScope duringTheOutage = new FreshnessScope.Entity(UUID.randomUUID());
            FreshnessScope afterTheOutage = new FreshnessScope.Entity(UUID.randomUUID());
            proxied.raise(Set.of(beforeTheOutage), server + ":1-23");
            long elapsedMillis;
            clientLink.toxics().timeout(BLACK_HOLE, ToxicDirection.UPSTREAM, 0);
            try {
                long start = System.nanoTime();
                proxied.raise(Set.of(duringTheOutage), server + ":1-24");
                elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            } finally {
                clientLink.toxics().get(BLACK_HOLE).remove();
            }
            assertThat(elapsedMillis).isLessThan(MarkerStore.RAISE_CONNECT_TIMEOUT.plus(WAIT_BOUND).toMillis());
            await().atMost(SCHEDULING_SLACK).until(() -> meters.counter(MarkerStore.RAISE_FAILURES).count() >= 1.0);
            assertThat(meters.counter(MarkerStore.RAISE_FAILURES).count()).isEqualTo(1.0);

            proxied.raise(Set.of(afterTheOutage), server + ":1-25");

            assertThat(meters.counter(MarkerStore.RAISE_FAILURES).count()).isEqualTo(1.0);
            assertThat(replica.opsForHash().entries(afterTheOutage.key())).containsOnly(Map.entry(server, "25"));
        } finally {
            proxied.close();
        }
    }
}
