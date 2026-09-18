package org.danteplanner.backend.shared.sse;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.RedisListenerExecutionFailedException;

import lombok.extern.slf4j.Slf4j;

/**
 * A listener container's initial subscription bypasses the recovery backoff, so an unreachable
 * local Redis at startup fails context load unless the failure is caught.
 */
@Configuration
@Slf4j
public class SseSubscriberConfig {

    @Bean
    public RedisMessageListenerContainer sseRedisMessageListenerContainer(
            @Qualifier("sseLocalRedisConnectionFactory") RedisConnectionFactory sseLocalRedisConnectionFactory,
            SseRedisSubscriber sseRedisSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer() {
            @Override
            public void start() {
                try {
                    super.start();
                } catch (RedisListenerExecutionFailedException e) {
                    log.error("SSE subscriber could not connect to local Redis at startup; "
                            + "cross-node fan-out is inactive until this pod restarts with Redis reachable", e);
                }
            }
        };
        container.setConnectionFactory(sseLocalRedisConnectionFactory);
        container.addMessageListener(sseRedisSubscriber,
                List.of(new ChannelTopic(SseChannel.USER.topic()),
                        new ChannelTopic(SseChannel.COMMENT.topic()),
                        new ChannelTopic(SseChannel.BROADCAST.topic())));
        return container;
    }
}
