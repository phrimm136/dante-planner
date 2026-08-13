package org.danteplanner.backend.shared.sse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * The pool that carries out an SSE heartbeat send.
 *
 * <p>A heartbeat writes to a client socket, so it blocks for as long as that peer's receive window
 * stays full. The scheduled sweep therefore only submits: a stalled peer occupies one of these
 * workers, while the scheduler thread — shared with every other {@code @Scheduled} task in the pod
 * — returns immediately.</p>
 */
@Configuration
public class SseHeartbeatWorkerConfig {

    public static final String SSE_HEARTBEAT_WORKER = "sseHeartbeatWorker";

    @Bean(SSE_HEARTBEAT_WORKER)
    public ThreadPoolTaskScheduler sseHeartbeatWorker() {
        ThreadPoolTaskScheduler worker = new ThreadPoolTaskScheduler();
        worker.setPoolSize(SseConstants.HEARTBEAT_WORKER_POOL_SIZE);
        worker.setThreadNamePrefix("sse-heartbeat-");
        worker.setRemoveOnCancelPolicy(true);
        return worker;
    }
}
