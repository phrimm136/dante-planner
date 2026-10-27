package org.danteplanner.backend.shared.config;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import org.assertj.core.api.InstanceOfAssertFactories;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import io.lettuce.core.RedisConnectionException;

import org.danteplanner.backend.support.InMemoryProxyManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LazyConnectingProxyManagerTest {

    private static final int CALLERS = 12;
    private static final Duration FAIL_FAST_BUDGET = Duration.ofMillis(100);
    private static final Duration OUTCOME_WAIT = Duration.ofSeconds(10);
    // The lower bound is the contract (never fail before the wait); the slack only absorbs the
    // scheduler on a loaded runner.
    private static final long SCHEDULING_SLACK_MS = 5_000;
    private static final long LATCH_WAIT_SECONDS = 5;
    private static final long RETRY_AFTER_NANOS =
            TimeUnit.MILLISECONDS.toNanos(TimeoutHierarchy.RATE_LIMIT_CONNECT_RETRY_AFTER_MS);

    private final AtomicLong nanoTime = new AtomicLong();

    @Test
    void builder_WhenCallersArriveDuringTheFirstConnect_AllShareItsBucket() throws Exception {
        InMemoryProxyManager connected = new InMemoryProxyManager();
        CountDownLatch connecting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger connectorCalls = new AtomicInteger();
        RedisConnectionConfig.LazyConnectingProxyManager manager = manager(() -> {
            connectorCalls.incrementAndGet();
            connecting.countDown();
            awaitQuietly(release);
            return connected;
        });
        List<CompletableFuture<Object>> outcomes = new ArrayList<>();
        outcomes.add(call(manager).outcome());
        assertThat(connecting.await(LATCH_WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        List<Thread> waiters = new ArrayList<>();
        for (int i = 1; i < CALLERS; i++) {
            Caller caller = call(manager);
            waiters.add(caller.thread());
            outcomes.add(caller.outcome());
        }

        try {
            awaitParkedOrDone(waiters);
        } finally {
            release.countDown();
        }

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome).succeedsWithin(OUTCOME_WAIT)
                .isSameAs(connected.builder()));
        assertThat(manager.builder()).isSameAs(connected.builder());
        assertThat(connectorCalls).hasValue(1);
    }

    @Test
    void builder_WhenTheStoreNeverAnswersTheHandshake_EveryCallerFailsWithinTheBoundOnOneAttempt() throws Exception {
        try (ServerSocket silentStore = new ServerSocket(0)) {
            int port = silentStore.getLocalPort();
            AtomicInteger connectorCalls = new AtomicInteger();
            AtomicReference<Thread> owner = new AtomicReference<>();
            RedisConnectionConfig.LazyConnectingProxyManager manager = manager(() -> {
                connectorCalls.incrementAndGet();
                owner.set(Thread.currentThread());
                return RedisConnectionConfig.buildRateLimitProxyManager("127.0.0.1", port, Duration.ofHours(1));
            });
            long start = System.nanoTime();

            List<Caller> callers = new ArrayList<>();
            for (int i = 0; i < CALLERS; i++) {
                callers.add(call(manager));
            }

            assertThat(callers).allSatisfy(caller -> assertThat(caller.outcome())
                    .succeedsWithin(OUTCOME_WAIT)
                    .isInstanceOf(RedisConnectionException.class));
            assertThat(callers).filteredOn(caller -> caller.thread() == owner.get()).singleElement()
                    .satisfies(caller -> assertThat(caller.outcome().join())
                            .asInstanceOf(InstanceOfAssertFactories.THROWABLE)
                            .hasMessageEndingWith(":" + port));
            assertThat(callers).filteredOn(caller -> caller.thread() != owner.get()).hasSize(CALLERS - 1)
                    .allSatisfy(waiter -> assertThat(TimeUnit.NANOSECONDS.toMillis(waiter.finishedAt().get() - start))
                            .isLessThanOrEqualTo(TimeoutHierarchy.RATE_LIMIT_CONNECT_WAIT_MS + SCHEDULING_SLACK_MS));
            assertThat(connectorCalls).hasValue(1);
        }
    }

    @Test
    void builder_WhenTheConnectOutlastsTheBound_FailsTheWaiterAtTheBound() throws Exception {
        CountDownLatch connecting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger connectorCalls = new AtomicInteger();
        RedisConnectionConfig.LazyConnectingProxyManager manager = manager(() -> {
            connectorCalls.incrementAndGet();
            connecting.countDown();
            awaitQuietly(release);
            throw new RedisConnectionException("connect refused");
        });
        Caller owner = call(manager);
        assertThat(connecting.await(LATCH_WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

        try {
            long start = System.nanoTime();
            CompletableFuture<Object> waiter = call(manager).outcome();

            assertThat(waiter).succeedsWithin(OUTCOME_WAIT).asInstanceOf(InstanceOfAssertFactories.THROWABLE)
                    .isInstanceOf(RedisConnectionException.class)
                    .hasMessage("Rate-limit store connect still in progress");
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
                    .isBetween(TimeoutHierarchy.RATE_LIMIT_CONNECT_WAIT_MS,
                            TimeoutHierarchy.RATE_LIMIT_CONNECT_WAIT_MS + SCHEDULING_SLACK_MS);
            assertThat(connectorCalls).hasValue(1);
        } finally {
            release.countDown();
            owner.thread().join(TimeUnit.SECONDS.toMillis(LATCH_WAIT_SECONDS));
        }
    }

    @Test
    void builder_WhenInsideTheRetryWindowAfterAFailure_FailsFastWithoutANewAttempt() {
        AtomicInteger connectorCalls = new AtomicInteger();
        RedisConnectionConfig.LazyConnectingProxyManager manager = manager(() -> {
            connectorCalls.incrementAndGet();
            throw new RedisConnectionException("connect refused");
        });
        assertThatThrownBy(manager::builder).isInstanceOf(RedisConnectionException.class)
                .hasMessage("connect refused");
        nanoTime.addAndGet(RETRY_AFTER_NANOS - 1);

        assertThat(call(manager).outcome())
                .succeedsWithin(FAIL_FAST_BUDGET).asInstanceOf(InstanceOfAssertFactories.THROWABLE)
                .isInstanceOf(RedisConnectionException.class)
                .hasMessage("connect refused");
        assertThat(connectorCalls).hasValue(1);
    }

    @Test
    void builder_WhenTheRetryWindowHasPassed_ConnectsAgain() {
        InMemoryProxyManager connected = new InMemoryProxyManager();
        RemoteBucketBuilder<byte[]> bucketBuilder = connected.builder();
        AtomicInteger connectorCalls = new AtomicInteger();
        RedisConnectionConfig.LazyConnectingProxyManager manager = manager(() -> {
            if (connectorCalls.incrementAndGet() == 1) {
                throw new RedisConnectionException("connect refused");
            }
            return connected;
        });
        assertThatThrownBy(manager::builder).isInstanceOf(RedisConnectionException.class)
                .hasMessage("connect refused");
        nanoTime.addAndGet(RETRY_AFTER_NANOS);

        assertThat(manager.builder()).isSameAs(bucketBuilder);
        assertThat(connectorCalls).hasValue(2);
    }

    private RedisConnectionConfig.LazyConnectingProxyManager manager(Supplier<ProxyManager<byte[]>> connector) {
        return new RedisConnectionConfig.LazyConnectingProxyManager(connector, nanoTime::get);
    }

    private record Caller(Thread thread, CompletableFuture<Object> outcome, AtomicLong finishedAt) {
    }

    private static Caller call(RedisConnectionConfig.LazyConnectingProxyManager manager) {
        CompletableFuture<Object> outcome = new CompletableFuture<>();
        AtomicLong finishedAt = new AtomicLong();
        Thread thread = Thread.ofPlatform().start(() -> {
            Object result = builderOrFailure(manager);
            finishedAt.set(System.nanoTime());
            outcome.complete(result);
        });
        return new Caller(thread, outcome, finishedAt);
    }

    private static Object builderOrFailure(RedisConnectionConfig.LazyConnectingProxyManager manager) {
        try {
            return manager.builder();
        } catch (RuntimeException failure) {
            return failure;
        }
    }

    private static void awaitParkedOrDone(List<Thread> threads) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(LATCH_WAIT_SECONDS);
        while (!threads.stream().allMatch(LazyConnectingProxyManagerTest::parkedOrDone)) {
            assertThat(System.nanoTime()).isLessThan(deadline);
            Thread.onSpinWait();
        }
    }

    private static boolean parkedOrDone(Thread thread) {
        Thread.State state = thread.getState();
        return state == Thread.State.TIMED_WAITING || state == Thread.State.TERMINATED;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(LATCH_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
