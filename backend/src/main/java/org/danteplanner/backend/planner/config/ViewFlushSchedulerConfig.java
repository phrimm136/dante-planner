package org.danteplanner.backend.planner.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Declaring either scheduler suppresses the framework's own: its auto-configuration backs off as
 * soon as any {@code TaskScheduler} bean exists, which would otherwise leave
 * {@code spring.task.scheduling.pool.size} unread.
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
