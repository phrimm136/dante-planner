package org.danteplanner.backend.shared.sse;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SseSubscriberConfigTest {

    @Test
    void sseSubscriptionWorker_WhenStarted_RunsOneSubscriptionThread() {
        ThreadPoolTaskScheduler worker = new SseSubscriberConfig().sseSubscriptionWorker();
        worker.initialize();
        try {
            assertThat(worker.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
        } finally {
            worker.shutdown();
        }
    }
}
