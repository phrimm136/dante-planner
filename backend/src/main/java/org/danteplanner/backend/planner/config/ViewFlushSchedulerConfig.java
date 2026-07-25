package org.danteplanner.backend.planner.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Dedicated scheduler for the planner view-buffer flush.
 *
 * <p>The flush runs every 500ms and writes cross-region. On the shared scheduling pool a stalled
 * cross-region write would consume a thread every interval and crowd out the other scheduled work,
 * so the flush is isolated onto its own single-threaded scheduler: it can stall without reaching
 * SSE heartbeats, and a slow neighbour cannot delay it.</p>
 */
@Configuration
public class ViewFlushSchedulerConfig {

    public static final String VIEW_FLUSH_SCHEDULER = "viewFlushScheduler";

    @Bean(VIEW_FLUSH_SCHEDULER)
    public ThreadPoolTaskScheduler viewFlushScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("view-flush-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
