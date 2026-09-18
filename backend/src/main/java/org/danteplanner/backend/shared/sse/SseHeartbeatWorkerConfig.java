package org.danteplanner.backend.shared.sse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

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
