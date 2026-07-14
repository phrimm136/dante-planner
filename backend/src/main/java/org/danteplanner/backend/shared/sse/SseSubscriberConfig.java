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
 * Wires the SSE fan-out subscription against this pod's local Redis endpoint.
 *
 * <p>Registers {@link SseRedisSubscriber} on the {@code sse:user} channel of the
 * {@code sse-local} connection factory (the regional replica in a multi-region
 * deployment), so events published on the primary reach local emitters.</p>
 *
 * <p>The container tolerates an unreachable local Redis at startup: its initial
 * subscription bypasses the recovery backoff and would otherwise fail context load,
 * so a boot-time connection failure is logged rather than propagated.</p>
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
                List.of(new ChannelTopic(SseChannels.USER), new ChannelTopic(SseChannels.COMMENT)));
        return container;
    }
}
