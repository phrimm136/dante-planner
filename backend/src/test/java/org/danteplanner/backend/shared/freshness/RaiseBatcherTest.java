package org.danteplanner.backend.shared.freshness;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.shared.config.BoundedRedisConnections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RaiseBatcherTest {

    private static final Duration WAIT_BOUND = Duration.ofSeconds(1);
    private static final Duration BOUND = WAIT_BOUND.plus(MarkerStore.RAISE_MARGIN);
    private static final String[] KEYS = {new FreshnessScope.PublicCatalog().key()};
    private static final String[] ARGUMENTS = {"3600000", "3e11fa47-71ca-11e1-9e33-c80aa9429562", "7"};
    private static final String WAIT = "WAIT";
    private static final String UNBOUNDED_WAIT = "0";

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final Counter raiseFailures = meters.counter(MarkerStore.RAISE_FAILURES);
    private final Counter waitTimeouts = meters.counter(MarkerStore.WAIT_TIMEOUTS);

    @Test
    void raise_WhenABatchThrowsAnUncheckedException_FailsThatBatchAndRaisesTheNextOnTheSameWorker()
            throws IOException {
        List<Thread> workers = new CopyOnWriteArrayList<>();
        AtomicInteger endpoints = new AtomicInteger();
        try (FakeRedis redis = new FakeRedis(WaitReply.ACKNOWLEDGES, false);
                RaiseBatcher batcher = new RaiseBatcher(() -> {
                    workers.add(Thread.currentThread());
                    if (endpoints.getAndIncrement() == 0) {
                        throw new IllegalStateException("injected");
                    }
                    return redis.endpoint();
                }, 1, WAIT_BOUND, MarkerStore.RAISE_MARGIN, raiseFailures, waitTimeouts, System::nanoTime)) {
            long start = System.nanoTime();
            boolean first = batcher.raise(KEYS, ARGUMENTS);
            Duration firstElapsed = Duration.ofNanos(System.nanoTime() - start);
            double failuresAfterFirst = raiseFailures.count();

            boolean second = batcher.raise(KEYS, ARGUMENTS);

            assertThat(first).isFalse();
            assertThat(firstElapsed).as("released by the failed batch, not by the bound").isLessThan(WAIT_BOUND);
            assertThat(failuresAfterFirst).isEqualTo(1.0);
            assertThat(second).isTrue();
            assertThat(raiseFailures.count()).isEqualTo(1.0);
            assertThat(workers).hasSize(2).containsOnly(workers.get(0));
        }
    }

    @Test
    void raise_WhenTheWorkerDied_StartsANewWorkerAndRaises() throws Exception {
        List<Thread> workers = new CopyOnWriteArrayList<>();
        try (FakeRedis redis = new FakeRedis(WaitReply.ACKNOWLEDGES, false);
                RaiseBatcher batcher = new RaiseBatcher(() -> {
                    workers.add(Thread.currentThread());
                    if (workers.size() == 1) {
                        throw new StackOverflowError("injected");
                    }
                    return redis.endpoint();
                }, 1, WAIT_BOUND, MarkerStore.RAISE_MARGIN, raiseFailures, waitTimeouts, System::nanoTime)) {
            boolean first = batcher.raise(KEYS, ARGUMENTS);
            double failuresAfterFirst = raiseFailures.count();
            await().atMost(BOUND).until(() -> !workers.get(0).isAlive());

            boolean second = batcher.raise(KEYS, ARGUMENTS);

            assertThat(first).isFalse();
            assertThat(failuresAfterFirst).isEqualTo(1.0);
            assertThat(second).isTrue();
            assertThat(workers).hasSize(2).doesNotHaveDuplicates();
        }
    }

    @Test
    void raise_WhenTheBatcherIsClosed_ReturnsFalseAndStartsNoWorker() throws Exception {
        AtomicInteger endpoints = new AtomicInteger();
        RaiseBatcher batcher = new RaiseBatcher(() -> {
            endpoints.incrementAndGet();
            throw new IllegalStateException("no endpoint after close");
        }, 1, WAIT_BOUND, MarkerStore.RAISE_MARGIN, raiseFailures, waitTimeouts, System::nanoTime);
        batcher.close();
        ThreadGroup callers = new ThreadGroup("raise-after-close");
        AtomicReference<Boolean> raised = new AtomicReference<>();
        Thread caller = new Thread(callers, () -> raised.set(batcher.raise(KEYS, ARGUMENTS)), "raise-caller");

        caller.start();
        caller.join(BOUND.toMillis());

        assertThat(raised.get()).isFalse();
        assertThat(callers.activeCount()).as("threads started by the raise").isZero();
        assertThat(endpoints.get()).isZero();
        assertThat(raiseFailures.count()).isEqualTo(1.0);
    }

    @Test
    void raise_WhenTheDeadlinePassesBeforeTheWaitIsAwaited_CountsAWaitTimeoutAndReleasesTheBatchAtOnce()
            throws Exception {
        AtomicLong skew = new AtomicLong();
        AtomicReference<Thread> worker = new AtomicReference<>();
        try (FakeRedis redis = new FakeRedis(WaitReply.NEVER, true);
                RaiseBatcher batcher = new RaiseBatcher(() -> {
                    worker.set(Thread.currentThread());
                    return redis.endpoint();
                }, 1, WAIT_BOUND, MarkerStore.RAISE_MARGIN, raiseFailures, waitTimeouts,
                        () -> System.nanoTime() + skew.get())) {
            CompletableFuture<Boolean> raised = CompletableFuture.supplyAsync(() -> batcher.raise(KEYS, ARGUMENTS));
            assertThat(redis.evalshaReceived.await(BOUND.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
            await().atMost(WAIT_BOUND).until(() -> worker.get().getState() == Thread.State.TIMED_WAITING);

            skew.set(BOUND.multipliedBy(2).toNanos());
            long released = System.nanoTime();
            redis.releaseEvalsha.countDown();
            boolean result = raised.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - released);

            assertThat(result).isTrue();
            assertThat(elapsed).isLessThan(WAIT_BOUND.dividedBy(2));
            assertThat(waitTimeouts.count()).isEqualTo(1.0);
            assertThat(raiseFailures.count()).isZero();
            assertThat(redis.commands)
                    .filteredOn(command -> command.get(0).equalsIgnoreCase(WAIT))
                    .noneMatch(command -> command.get(2).equals(UNBOUNDED_WAIT));
        }
    }

    @Test
    void raise_WhenConnectingSpentPartOfTheBound_SendsTheWaitWithTheTimeLeftAndCountsAWaitTimeout()
            throws Exception {
        Duration connectCost = Duration.ofMillis(700);
        AtomicLong skew = new AtomicLong();
        try (FakeRedis redis = new FakeRedis(WaitReply.TIMES_OUT, false,
                () -> skew.set(connectCost.toNanos()));
                RaiseBatcher batcher = new RaiseBatcher(redis::endpoint, 1, WAIT_BOUND, MarkerStore.RAISE_MARGIN,
                        raiseFailures, waitTimeouts, () -> System.nanoTime() + skew.get())) {
            boolean raised = batcher.raise(KEYS, ARGUMENTS);

            assertThat(redis.waitTimeouts())
                    .singleElement()
                    .satisfies(timeout -> assertThat(timeout).isBetween(1L,
                            BOUND.minus(connectCost).minus(MarkerStore.RAISE_MARGIN).toMillis()));
            assertThat(raised).isTrue();
            assertThat(waitTimeouts.count()).isEqualTo(1.0);
            assertThat(raiseFailures.count()).isZero();
        }
    }

    @Test
    void raise_WhenConnectingLeftNoMoreThanTheMargin_SendsNoWaitAndCountsAWaitTimeout() throws Exception {
        Duration connectCost = WAIT_BOUND.plusMillis(100);
        AtomicLong skew = new AtomicLong();
        try (FakeRedis redis = new FakeRedis(WaitReply.TIMES_OUT, false,
                () -> skew.set(connectCost.toNanos()));
                RaiseBatcher batcher = new RaiseBatcher(redis::endpoint, 1, WAIT_BOUND, MarkerStore.RAISE_MARGIN,
                        raiseFailures, waitTimeouts, () -> System.nanoTime() + skew.get())) {
            boolean raised = batcher.raise(KEYS, ARGUMENTS);

            assertThat(raised).isTrue();
            assertThat(redis.waitTimeouts()).isEmpty();
            assertThat(waitTimeouts.count()).isEqualTo(1.0);
            assertThat(raiseFailures.count()).isZero();
        }
    }

    private enum WaitReply {
        ACKNOWLEDGES, NEVER, TIMES_OUT
    }

    private static final class FakeRedis implements AutoCloseable {

        private static final String SCRIPT_SHA = "0123456789abcdef0123456789abcdef01234567";

        private final WaitReply waitReply;
        private final boolean holdsEvalsha;
        private final Runnable onScriptLoad;
        private final ServerSocket listener;
        private final List<Socket> clients = new CopyOnWriteArrayList<>();
        private final List<List<String>> commands = new CopyOnWriteArrayList<>();
        private final CountDownLatch evalshaReceived = new CountDownLatch(1);
        private final CountDownLatch releaseEvalsha = new CountDownLatch(1);

        FakeRedis(WaitReply waitReply, boolean holdsEvalsha) throws IOException {
            this(waitReply, holdsEvalsha, () -> { });
        }

        FakeRedis(WaitReply waitReply, boolean holdsEvalsha, Runnable onScriptLoad) throws IOException {
            this.waitReply = waitReply;
            this.holdsEvalsha = holdsEvalsha;
            this.onScriptLoad = onScriptLoad;
            this.listener = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
            Thread acceptor = new Thread(this::accept, "fake-redis-accept");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        RaiseBatcher.Endpoint endpoint() {
            String host = listener.getInetAddress().getHostAddress();
            return new RaiseBatcher.Endpoint(BoundedRedisConnections.redisClient(host, listener.getLocalPort()),
                    BoundedRedisConnections.redisUri(host, listener.getLocalPort()));
        }

        List<Long> waitTimeouts() {
            return commands.stream()
                    .filter(command -> command.get(0).equalsIgnoreCase(WAIT))
                    .map(command -> Long.parseLong(command.get(2)))
                    .toList();
        }

        @Override
        public void close() throws IOException {
            releaseEvalsha.countDown();
            listener.close();
            for (Socket client : clients) {
                client.close();
            }
        }

        private void accept() {
            try {
                while (!listener.isClosed()) {
                    Socket client = listener.accept();
                    clients.add(client);
                    Thread handler = new Thread(() -> serve(client), "fake-redis-client");
                    handler.setDaemon(true);
                    handler.start();
                }
            } catch (IOException closed) {
                return;
            }
        }

        private void serve(Socket client) {
            try (InputStream in = new BufferedInputStream(client.getInputStream());
                    OutputStream out = client.getOutputStream()) {
                List<String> command = readCommand(in);
                while (command != null) {
                    commands.add(command);
                    String reply = replyTo(command);
                    if (reply != null) {
                        out.write(reply.getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    }
                    command = readCommand(in);
                }
            } catch (IOException | InterruptedException closed) {
                return;
            }
        }

        private String replyTo(List<String> command) throws InterruptedException {
            return switch (command.get(0).toUpperCase(Locale.ROOT)) {
                case "HELLO" -> "-ERR unknown command 'HELLO'\r\n";
                case "PING" -> "+PONG\r\n";
                case "SCRIPT" -> {
                    onScriptLoad.run();
                    yield "$" + SCRIPT_SHA.length() + "\r\n" + SCRIPT_SHA + "\r\n";
                }
                case "EVALSHA" -> {
                    evalshaReceived.countDown();
                    if (holdsEvalsha) {
                        releaseEvalsha.await(BOUND.toMillis() * 4, TimeUnit.MILLISECONDS);
                    }
                    yield ":1\r\n";
                }
                case WAIT -> switch (waitReply) {
                    case ACKNOWLEDGES -> ":1\r\n";
                    case NEVER -> null;
                    case TIMES_OUT -> {
                        Thread.sleep(Long.parseLong(command.get(2)));
                        yield ":0\r\n";
                    }
                };
                default -> "+OK\r\n";
            };
        }

        private static List<String> readCommand(InputStream in) throws IOException {
            String header = readLine(in);
            if (header == null) {
                return null;
            }
            int count = Integer.parseInt(header.substring(1));
            List<String> arguments = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int length = Integer.parseInt(readLine(in).substring(1));
                byte[] bytes = in.readNBytes(length + 2);
                arguments.add(new String(bytes, 0, length, StandardCharsets.UTF_8));
            }
            return arguments;
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int previous = -1;
            int next = in.read();
            while (next != -1) {
                if (previous == '\r' && next == '\n') {
                    byte[] bytes = line.toByteArray();
                    return new String(bytes, 0, bytes.length - 1, StandardCharsets.UTF_8);
                }
                line.write(next);
                previous = next;
                next = in.read();
            }
            return null;
        }
    }
}
