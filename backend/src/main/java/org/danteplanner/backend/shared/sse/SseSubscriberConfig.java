package org.danteplanner.backend.shared.sse;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * A listener container never retries a failed first subscription, and ignores a second start() until stop().
 */
@Configuration
public class SseSubscriberConfig {

    public static final String SSE_SUBSCRIPTION_WORKER = "sseSubscriptionWorker";

    @Bean
    public RedisMessageListenerContainer sseRedisMessageListenerContainer(
            @Qualifier("sseLocalRedisConnectionFactory") RedisConnectionFactory sseLocalRedisConnectionFactory,
            SseRedisSubscriber sseRedisSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(sseLocalRedisConnectionFactory);
        container.setAutoStartup(false);
        container.addMessageListener(sseRedisSubscriber,
                List.of(new ChannelTopic(SseChannel.USER.topic()),
                        new ChannelTopic(SseChannel.COMMENT.topic()),
                        new ChannelTopic(SseChannel.BROADCAST.topic())));
        return container;
    }

    @Bean(SSE_SUBSCRIPTION_WORKER)
    public ThreadPoolTaskScheduler sseSubscriptionWorker() {
        ThreadPoolTaskScheduler worker = new ThreadPoolTaskScheduler();
        worker.setThreadNamePrefix("sse-subscription-");
        worker.setDaemon(true);
        worker.setWaitForTasksToCompleteOnShutdown(false);
        worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return worker;
    }

    @Bean
    public SseSubscription sseSubscription(
            RedisMessageListenerContainer sseRedisMessageListenerContainer,
            @Qualifier(SSE_SUBSCRIPTION_WORKER) ThreadPoolTaskScheduler sseSubscriptionWorker) {
        return new SseSubscription(sseRedisMessageListenerContainer::isListening, () -> {
            sseRedisMessageListenerContainer.stop();
            sseRedisMessageListenerContainer.start();
        }, sseSubscriptionWorker);
    }
}
