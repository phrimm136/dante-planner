package org.danteplanner.backend.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * The framework's auto-configuration backs off as soon as any {@code TaskScheduler} bean exists,
 * which would otherwise leave {@code spring.task.scheduling.pool.size} unread.
 */
@Configuration
public class TaskSchedulerConfig {

    @Bean
    @Primary
    public ThreadPoolTaskScheduler taskScheduler(
            @Value("${spring.task.scheduling.pool.size:4}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("scheduling-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return scheduler;
    }
}
