package org.danteplanner.backend.shared.sse;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The heartbeat sweep must survive a peer that never drains its socket.
 *
 * <p>A send to such a peer blocks until the emitter times out, and the sweep is driven by the
 * scheduler every other {@code @Scheduled} task in the pod shares. The connections here are two
 * peers whose sends block on a latch plus one that answers, and the scheduler is single-threaded so
 * a sweep that sent inline would leave no thread for the sentinel task.</p>
 */
class SseHeartbeatSweepTest {

    private static final int STALLED_PEERS = 2;

    /** Generous against CI scheduling noise, and far below the 10s heartbeat interval. */
    private static final long PROMPT_MS = 2_000;

    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch stalledSends = new CountDownLatch(STALLED_PEERS);
    private final CountDownLatch healthySend = new CountDownLatch(1);

    private ThreadPoolTaskScheduler scheduler;
    private ThreadPoolTaskScheduler worker;
    private ProbeSseService service;

    @BeforeEach
    void setUp() {
        scheduler = pool(1, "test-scheduling-");
        worker = pool(SseConstants.HEARTBEAT_WORKER_POOL_SIZE, "sse-heartbeat-");
        service = new ProbeSseService(worker);

        for (int i = 0; i < STALLED_PEERS; i++) {
            service.connect(new StalledEmitter(stalledSends, release));
        }
        service.connect(new AnsweringEmitter(healthySend));
    }

    @AfterEach
    void tearDown() {
        release.countDown();
        scheduler.shutdown();
        worker.shutdown();
    }

    @Test
    @DisplayName("the sweep returns while stalled sends are still blocked, leaving the scheduler free")
    void sweepHeartbeatConnections_WhenPeersStall_LeavesTheSchedulerThreadFree() throws Exception {
        CountDownLatch sweepReturned = new CountDownLatch(1);
        scheduler.execute(() -> {
            service.sweepHeartbeatConnections();
            sweepReturned.countDown();
        });

        assertThat(sweepReturned.await(PROMPT_MS, TimeUnit.MILLISECONDS))
                .as("the tick submits and returns; it must not wait on a peer's socket")
                .isTrue();
        assertThat(stalledSends.await(PROMPT_MS, TimeUnit.MILLISECONDS))
                .as("both stalled sends must be in flight, so the workers are the threads being held")
                .isTrue();

        CountDownLatch sentinel = new CountDownLatch(1);
        scheduler.execute(sentinel::countDown);

        assertThat(sentinel.await(PROMPT_MS, TimeUnit.MILLISECONDS))
                .as("a scheduled task queued behind the sweep must still run while peers are stalled")
                .isTrue();
    }

    @Test
    @DisplayName("a responsive connection is heartbeaten even while other peers stall")
    void sweepHeartbeatConnections_WhenPeersStall_StillHeartbeatsTheHealthyConnection() throws Exception {
        scheduler.execute(service::sweepHeartbeatConnections);

        assertThat(healthySend.await(PROMPT_MS, TimeUnit.MILLISECONDS))
                .as("a stalled peer must not cost the healthy connection its heartbeat")
                .isTrue();
    }

    private static ThreadPoolTaskScheduler pool(int size, String threadNamePrefix) {
        ThreadPoolTaskScheduler pool = new ThreadPoolTaskScheduler();
        pool.setPoolSize(size);
        pool.setThreadNamePrefix(threadNamePrefix);
        pool.initialize();
        return pool;
    }

    /** Blocks in {@code send} until released, as a peer whose receive window never opens does. */
    private static final class StalledEmitter extends SseEmitter {

        private final CountDownLatch entered;
        private final CountDownLatch release;

        private StalledEmitter(CountDownLatch entered, CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        @Override
        public void send(SseEmitter.SseEventBuilder builder) {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Answers immediately and records that it was reached. */
    private static final class AnsweringEmitter extends SseEmitter {

        private final CountDownLatch sent;

        private AnsweringEmitter(CountDownLatch sent) {
            this.sent = sent;
        }

        @Override
        public void send(SseEmitter.SseEventBuilder builder) {
            sent.countDown();
        }
    }

    /**
     * A registry holding emitters the test supplies; {@code register} would mint its own, which no
     * test can make block.
     */
    private static final class ProbeSseService extends AbstractSseService<String> {

        private static final String KEY = "key";

        private ProbeSseService(TaskExecutor heartbeatWorker) {
            super(heartbeatWorker);
        }

        private void connect(SseEmitter emitter) {
            emitters.computeIfAbsent(KEY, k -> new CopyOnWriteArrayList<>())
                    .add(new EmitterEntry(UUID.randomUUID(), null, emitter));
        }

        @Override
        protected void onUnsubscribed(String key, UUID deviceId) {
        }

        @Override
        protected void onHeartbeatFailure(String key, UUID deviceId) {
        }
    }
}
