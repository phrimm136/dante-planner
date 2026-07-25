package org.danteplanner.backend.planner.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Schedulers for the application's periodic work.
 *
 * <p>Both are declared here because declaring either one suppresses the framework's own scheduler:
 * its auto-configuration backs off as soon as any {@code TaskScheduler} bean exists, which would
 * otherwise leave {@code spring.task.scheduling.pool.size} unread and route every scheduled task
 * onto the single-threaded flush scheduler below.</p>
 *
 * <p>The shared scheduler carries more than one thread so a task blocked on a cross-region write
 * cannot stop SSE heartbeats. The planner view-buffer flush runs every 500ms and writes
 * cross-region on every tick, so it is isolated onto its own thread: it can stall without reaching
 * the others, and a slow neighbour cannot delay it.</p>
 */
@Configuration
public class ViewFlushSchedulerConfig {

    public static final String VIEW_FLUSH_SCHEDULER = "viewFlushScheduler";

    @Bean
    @Primary
    public ThreadPoolTaskScheduler taskScheduler(
            @Value("${spring.task.scheduling.pool.size:4}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("scheduling-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Bean(VIEW_FLUSH_SCHEDULER)
    public ThreadPoolTaskScheduler viewFlushScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("view-flush-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
