package org.danteplanner.backend.shared.sse;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.danteplanner.backend.support.Hang;

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
    void sweepHeartbeatConnections_WhenPeersStall_LeavesTheSchedulerThreadFree() throws Exception {
        CountDownLatch sweepReturned = new CountDownLatch(1);
        scheduler.execute(() -> {
            service.sweepHeartbeatConnections();
            sweepReturned.countDown();
        });

        Hang.await(sweepReturned, "the tick submitting and returning without waiting on a peer's socket");
        Hang.await(stalledSends, "both stalled sends getting in flight, so the workers are the threads being held");

        CountDownLatch sentinel = new CountDownLatch(1);
        scheduler.execute(sentinel::countDown);

        Hang.await(sentinel, "a scheduled task queued behind the sweep running while peers are stalled");
    }

    @Test
    void sweepHeartbeatConnections_WhenPeersStall_StillHeartbeatsTheHealthyConnection() throws Exception {
        scheduler.execute(service::sweepHeartbeatConnections);

        Hang.await(healthySend, "the healthy connection's heartbeat while a peer is stalled");
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
