package org.danteplanner.backend.shared.freshness;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.NettyCustomizer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.config.BoundedRedisConnections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class MarkerStoreConnectBoundTest {

    private static final String SERVER = "3e11fa47-71ca-11e1-9e33-c80aa9429562";
    private static final Duration WAIT_BOUND = Duration.ofSeconds(1);
    private static final Duration SCHEDULING_SLACK = Duration.ofSeconds(5);
    private static final int BACKLOG = 1;
    private static final int MAX_FILLERS = 16;
    private static final int FILLER_CONNECT_MILLIS = 200;
    private static final int QUEUED_RAISES = 6;
    private static final Duration CONNECT_OUTLASTING_THE_TEST = Duration.ofHours(1);

    @Test
    void raise_WhenTheMarkerStoreDropsTheConnect_ReturnsWithinTheConnectBoundPlusTheWaitBoundAndCountsOneFailure()
            throws IOException {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        List<Socket> fillers = new ArrayList<>();
        try (ServerSocket blackHole = new ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress())) {
            LettuceConnectionFactory writeStore = BoundedRedisConnections.connectionFactory(
                    new RedisStandaloneConfiguration(blackHole.getInetAddress().getHostAddress(),
                            blackHole.getLocalPort()));
            writeStore.afterPropertiesSet();
            try (MarkerStore store = new MarkerStore(writeStore, writeStore.getRequiredNativeClient().getResources(), List.of(),
                    new MarkerStore.Settings(0, WAIT_BOUND, Duration.ofHours(1)), meters)) {
                assertThat(fillAcceptQueue(blackHole, fillers))
                        .as("the kernel drops a SYN once the accept queue of a never-accepting listener is full")
                        .isTrue();
                store.raise(Set.of(new FreshnessScope.PublicCatalog()), SERVER + ":1");
                await().atMost(MarkerStore.RAISE_CONNECT_TIMEOUT.plus(WAIT_BOUND).plus(MarkerStore.RAISE_MARGIN)
                        .plus(SCHEDULING_SLACK))
                        .untilAsserted(() -> assertThat(meters.counter(MarkerStore.RAISE_FAILURES).count())
                                .isEqualTo(1.0));
                double failuresBefore = meters.counter(MarkerStore.RAISE_FAILURES).count();

                long start = System.nanoTime();
                store.raise(Set.of(new FreshnessScope.PublicCatalog()), SERVER + ":2");
                Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

                assertThat(elapsed).isLessThan(MarkerStore.RAISE_CONNECT_TIMEOUT.plus(WAIT_BOUND).plus(SCHEDULING_SLACK));
                assertThat(meters.counter(MarkerStore.RAISE_FAILURES).count() - failuresBefore).isEqualTo(1.0);
            } finally {
                writeStore.destroy();
            }
        } finally {
            for (Socket filler : fillers) {
                filler.close();
            }
        }
    }

    @Test
    void close_WhenRaisesAreQueuedBehindAStuckConnect_ReleasesEveryRaiseAsAFailureAndStopsTheRaiseThread()
            throws IOException, InterruptedException {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        List<Socket> fillers = new ArrayList<>();
        try (ServerSocket blackHole = new ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress())) {
            LettuceConnectionFactory writeStore = BoundedRedisConnections.connectionFactory(
                    new RedisStandaloneConfiguration(blackHole.getInetAddress().getHostAddress(),
                            blackHole.getLocalPort()));
            writeStore.afterPropertiesSet();
            ClientResources connectOutlastingTheTest = DefaultClientResources.builder()
                    .nettyCustomizer(new NettyCustomizer() {
                        @Override
                        public void afterBootstrapInitialized(Bootstrap bootstrap) {
                            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                                    Math.toIntExact(CONNECT_OUTLASTING_THE_TEST.toMillis()));
                        }
                    })
                    .build();
            try {
                MarkerStore store = new MarkerStore(writeStore, connectOutlastingTheTest,
                        List.of(), new MarkerStore.Settings(0, WAIT_BOUND, Duration.ofHours(1)), meters);
                assertThat(fillAcceptQueue(blackHole, fillers)).isTrue();
                ThreadGroup group = new ThreadGroup("marker-raise-close-" + UUID.randomUUID());
                List<Thread> raises = IntStream.range(0, QUEUED_RAISES)
                        .mapToObj(i -> new Thread(group, () -> store.raise(
                                Set.of(new FreshnessScope.PublicCatalog()), SERVER + ":" + (i + 1)), group.getName()))
                        .toList();
                raises.forEach(Thread::start);
                await().atMost(SCHEDULING_SLACK).until(() -> raises.stream()
                        .allMatch(raise -> raise.getState() == Thread.State.TIMED_WAITING));

                store.close();
                for (Thread raise : raises) {
                    raise.join(WAIT_BOUND.plus(MarkerStore.RAISE_MARGIN).plus(SCHEDULING_SLACK).toMillis());
                }

                assertThat(raises).noneMatch(Thread::isAlive);
                await().atMost(SCHEDULING_SLACK).until(() -> raiseThreads(group).isEmpty());
                assertThat(meters.counter(MarkerStore.RAISE_FAILURES).count()).isEqualTo(QUEUED_RAISES);
            } finally {
                writeStore.destroy();
                connectOutlastingTheTest.shutdown().syncUninterruptibly();
            }
        } finally {
            for (Socket filler : fillers) {
                filler.close();
            }
        }
    }

    private static List<Thread> raiseThreads(ThreadGroup group) {
        Thread[] threads = new Thread[group.activeCount() * 2 + QUEUED_RAISES];
        int count = group.enumerate(threads, true);
        return Arrays.stream(threads, 0, count)
                .filter(Thread::isAlive)
                .filter(thread -> thread.getName().equals(RaiseBatcher.THREAD_NAME))
                .toList();
    }

    private static boolean fillAcceptQueue(ServerSocket listener, List<Socket> fillers) throws IOException {
        for (int i = 0; i < MAX_FILLERS; i++) {
            Socket filler = new Socket();
            try {
                filler.connect(new InetSocketAddress(listener.getInetAddress(), listener.getLocalPort()),
                        FILLER_CONNECT_MILLIS);
                fillers.add(filler);
            } catch (SocketTimeoutException e) {
                filler.close();
                return true;
            }
        }
        return false;
    }
}
