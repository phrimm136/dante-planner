package org.danteplanner.backend.shared.freshness;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import jakarta.persistence.EntityManagerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.danteplanner.backend.shared.config.ReadSourceProperties;
import org.danteplanner.backend.shared.config.ReadSourceSelector;
import org.danteplanner.backend.shared.config.RedisConnectionConfig;
import org.danteplanner.backend.shared.exception.ProblemWriter;
import org.danteplanner.backend.shared.gtid.GtidGateConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("containerized")
@ExtendWith(MockitoExtension.class)
class MarkerStoreContextIT {

    private static final RedisContainer REDIS = new RedisContainer("redis:7-alpine");
    private static final String LETTUCE_THREAD_PREFIX = "lettuce-";
    private static final String NAME_COUNTER_SEPARATOR = "-";
    private static final Duration CONTEXT_BOUND = Duration.ofSeconds(60);
    private static final Duration SHUTDOWN_BOUND = Duration.ofSeconds(10);
    private static final int RAISES_AT_CLOSE = 32;
    private static final List<String> REDIS_ENDPOINTS = List.of("auth", "rate-limit", "sse-local", "auth-local");

    @Mock
    private DataSource primaryReadSource;

    @Mock
    private EntityManagerFactory entityManagerFactory;

    @Mock
    private DataSource dataSource;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    private ApplicationContextRunner routingContext() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(RedisConnectionConfig.class, GtidGateConfig.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(CircuitBreakerRegistry.class, CircuitBreakerRegistry::ofDefaults)
                .withBean(ReadSourceProperties.class)
                .withBean(ReadSourceSelector.class, () -> new ReadSourceSelector(List.of(new ReadSourceSelector.Source(
                        ReadSourceProperties.PRIMARY, true, primaryReadSource,
                        CircuitBreaker.ofDefaults(ReadSourceProperties.PRIMARY + UUID.randomUUID())))))
                .withBean(EntityManagerFactory.class, () -> entityManagerFactory)
                .withBean(DataSource.class, () -> dataSource)
                .withBean(ProblemWriter.class, () -> new ProblemWriter(new ObjectMapper()))
                .withPropertyValues(
                        "datasource.routing.enabled=true",
                        "datasource.read-sources[0].name=seoul-replica",
                        "datasource.read-sources[0].url=jdbc:mysql://127.0.0.1:1/planner",
                        "datasource.read-sources[1].name=" + ReadSourceProperties.PRIMARY,
                        "freshness.marker.wait-replicas=0",
                        "freshness.marker.wait-bound=PT1S",
                        "freshness.marker.ttl=PT1H");
        for (String endpoint : REDIS_ENDPOINTS) {
            runner = runner.withPropertyValues(
                    "redis." + endpoint + ".host=" + REDIS.getRedisHost(),
                    "redis." + endpoint + ".port=" + REDIS.getRedisPort());
        }
        return runner;
    }

    private static void raiseProbeAndConnectEveryFactory(ApplicationContext context) {
        MarkerStore store = context.getBean(MarkerStore.class);
        store.raise(Set.of(new FreshnessScope.PublicCatalog()), UUID.randomUUID() + ":1");
        store.probe();
        for (LettuceConnectionFactory factory : context.getBeansOfType(LettuceConnectionFactory.class).values()) {
            try (RedisConnection connection = factory.getConnection()) {
                assertThat(connection.ping()).isNotNull();
            }
        }
    }

    private void runInGroup(ThreadGroup group, Consumer<ApplicationContext> whileOpen) throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread contextThread = new Thread(group, () -> {
            try {
                routingContext().run(context -> {
                    assertThat(context).hasNotFailed();
                    whileOpen.accept(context);
                });
            } catch (Throwable e) {
                failure.set(e);
            }
        }, group.getName());
        contextThread.start();
        contextThread.join(CONTEXT_BOUND);
        assertThat(contextThread.isAlive()).as("context thread finished within the bound").isFalse();
        assertThat(failure.get()).isNull();
    }

    private static List<Thread> lettuceThreads(ThreadGroup group) {
        return liveThreads(group).stream()
                .filter(thread -> thread.getName().startsWith(LETTUCE_THREAD_PREFIX))
                .toList();
    }

    private static List<Thread> lettuceAndRaiseThreads(ThreadGroup group) {
        return liveThreads(group).stream()
                .filter(thread -> thread.getName().startsWith(LETTUCE_THREAD_PREFIX)
                        || thread.getName().equals(RaiseBatcher.THREAD_NAME))
                .toList();
    }

    private static List<Thread> liveThreads(ThreadGroup group) {
        Thread[] threads = new Thread[group.activeCount() * 2 + 16];
        int count = group.enumerate(threads, true);
        return Arrays.stream(threads, 0, count)
                .filter(Thread::isAlive)
                .toList();
    }

    private static String withoutLastCounter(String name) {
        return name.substring(0, name.lastIndexOf(NAME_COUNTER_SEPARATOR));
    }

    @Test
    void context_WhenMarkerStoreHasRaisedAndProbed_RunsEveryRedisClientOnOneSetOfLettucePools() throws InterruptedException {
        ThreadGroup group = new ThreadGroup("marker-store-context-" + UUID.randomUUID());
        AtomicReference<Set<String>> pools = new AtomicReference<>();
        runInGroup(group, context -> {
            raiseProbeAndConnectEveryFactory(context);
            pools.set(lettuceThreads(group).stream()
                    .map(thread -> withoutLastCounter(thread.getName()))
                    .collect(Collectors.toSet()));
        });

        Set<String> kinds = pools.get().stream()
                .map(MarkerStoreContextIT::withoutLastCounter)
                .collect(Collectors.toSet());
        assertThat(pools.get()).as("the context started Lettuce threads, so the axis is not vacuous").isNotEmpty();
        assertThat(pools.get()).as("one pool per kind of Lettuce pool %s", kinds).hasSameSizeAs(kinds);
    }

    @Test
    void context_WhenClosedWhileRaisesAreInFlight_ReleasesEveryRaiseAndLeavesNoLettuceOrRaiseThreadAlive()
            throws InterruptedException {
        ThreadGroup group = new ThreadGroup("marker-store-context-" + UUID.randomUUID());
        AtomicReference<Integer> startedWhileOpen = new AtomicReference<>();
        List<Thread> raises = new ArrayList<>();
        runInGroup(group, context -> {
            raiseProbeAndConnectEveryFactory(context);
            startedWhileOpen.set(lettuceAndRaiseThreads(group).size());
            MarkerStore store = context.getBean(MarkerStore.class);
            for (int i = 0; i < RAISES_AT_CLOSE; i++) {
                Thread raise = new Thread(() -> store.raise(
                        Set.of(new FreshnessScope.PublicCatalog()), UUID.randomUUID() + ":1"));
                raises.add(raise);
                raise.start();
            }
        });
        for (Thread raise : raises) {
            raise.join(SHUTDOWN_BOUND.toMillis());
        }

        assertThat(startedWhileOpen.get()).as("the context started Lettuce threads, so the axis is not vacuous")
                .isPositive();
        assertThat(raises).as("every raise in flight at close returned").noneMatch(Thread::isAlive);
        await().atMost(SHUTDOWN_BOUND).untilAsserted(() -> assertThat(lettuceAndRaiseThreads(group)).isEmpty());
    }
}
