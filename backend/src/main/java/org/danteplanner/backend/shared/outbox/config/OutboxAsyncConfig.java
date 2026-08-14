package org.danteplanner.backend.shared.outbox.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * The pool the eager outbox dispatch runs on.
 *
 * <p>The tree's async model is otherwise {@code @Scheduled} plus after-commit listeners plus Redis
 * pub/sub, and this is the one exception: the dispatch has to leave the request thread, because a
 * fan-out with no bound on its size would otherwise be paid by the response.</p>
 *
 * <p>A saturated queue discards rather than throwing or borrowing the caller. Throwing would
 * escape the after-commit callback into a request whose write already committed, and borrowing the
 * caller would put the fan-out back on the thread this pool exists to spare. A discarded hop costs
 * at most one relay interval, because the row it would have dispatched is still open.</p>
 */
@Configuration
@EnableAsync
@EnableRetry
public class OutboxAsyncConfig {

    public static final String OUTBOX_DISPATCH_EXECUTOR = "outboxDispatchExecutor";

    private static final int CORE_POOL_SIZE = 2;
    private static final int MAX_POOL_SIZE = 4;
    private static final int QUEUE_CAPACITY = 500;
    private static final int SHUTDOWN_DRAIN_SECONDS = 20;

    @Bean(OUTBOX_DISPATCH_EXECUTOR)
    public ThreadPoolTaskExecutor outboxDispatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("outbox-dispatch-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_DRAIN_SECONDS);
        return executor;
    }
}
