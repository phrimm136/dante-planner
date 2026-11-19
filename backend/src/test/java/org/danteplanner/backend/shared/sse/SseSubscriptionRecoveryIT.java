package org.danteplanner.backend.shared.sse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.redis.testcontainers.RedisContainer;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;

import org.danteplanner.backend.shared.config.BoundedRedisConnections;
import org.danteplanner.backend.support.Hang;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("containerized")
class SseSubscriptionRecoveryIT {

    @Test
    void connect_WhenThePodStartedWhileRedisWasSilent_SubscribesOnceRedisAnswers() throws Exception {
        SseSubscriberConfig config = new SseSubscriberConfig();
        AtomicInteger delivered = new AtomicInteger();
        SseRedisSubscriber subscriber = new SseRedisSubscriber(null, null, null) {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                delivered.incrementAndGet();
            }
        };
        try (RedisContainer redis = new RedisContainer("redis:7-alpine")) {
            redis.start();
            LettuceConnectionFactory factory = BoundedRedisConnections.connectionFactory(
                    new RedisStandaloneConfiguration(redis.getRedisHost(), redis.getRedisPort()));
            factory.afterPropertiesSet();
            RedisMessageListenerContainer container = config.sseRedisMessageListenerContainer(factory, subscriber);
            container.afterPropertiesSet();
            ThreadPoolTaskScheduler worker = config.sseSubscriptionWorker();
            worker.afterPropertiesSet();
            SseSubscription subscription = config.sseSubscription(container, worker);
            Supplier<Void> pause = redis.getDockerClient().pauseContainerCmd(redis.getContainerId())::exec;
            Supplier<Void> unpause = redis.getDockerClient().unpauseContainerCmd(redis.getContainerId())::exec;
            RedisClient publisherClient = BoundedRedisConnections.redisClient(redis.getRedisHost(), redis.getRedisPort());
            try {
                pause.get();
                CompletableFuture<Void> whileSilent = subscription.connect();
                Hang.until(whileSilent::isDone, "the subscription attempt against a silent Redis giving up");
                assertThat(whileSilent.isCompletedExceptionally()).isTrue();
                assertThat(container.isListening()).isFalse();

                unpause.get();
                Hang.get(subscription.connect(), "the subscription attempt once Redis answers");
                assertThat(container.isListening()).isTrue();

                try (StatefulRedisConnection<String, String> publisher = publisherClient.connect()) {
                    Hang.until(() -> {
                        publisher.sync().publish(SseChannel.USER.topic(), "{}");
                        return delivered.get() > 0;
                    }, "a message published once Redis answers reaching the listener");
                }
            } finally {
                subscription.close();
                publisherClient.shutdown();
                container.destroy();
                worker.shutdown();
                factory.destroy();
            }
        }
    }
}
