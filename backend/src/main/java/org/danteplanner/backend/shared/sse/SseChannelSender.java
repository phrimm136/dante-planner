package org.danteplanner.backend.shared.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * The one Redis publish, held apart so a retry can wrap it.
 *
 * <p>{@code SsePublisher.publish} is private and self-invoked, so no proxy ever sees a call to it
 * and an annotation there would be inert. Delegating the send to a collaborator is what puts a
 * proxy boundary between the caller and the network hop.</p>
 *
 * <p>Retried only for a connection failure, which is what a primary failover looks like from here.
 * Anything else is a fault the same call would reproduce.</p>
 */
@Component
@RequiredArgsConstructor
public class SseChannelSender {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Publish one serialized envelope.
     *
     * @param topic the Redis pub/sub channel
     * @param json  the serialized envelope
     * @throws RedisConnectionFailureException when every attempt found the primary unreachable
     */
    @Retryable(retryFor = RedisConnectionFailureException.class,
            maxAttempts = SseConstants.PUBLISH_MAX_ATTEMPTS,
            backoff = @Backoff(delay = SseConstants.PUBLISH_RETRY_DELAY_MS,
                    multiplier = SseConstants.PUBLISH_RETRY_MULTIPLIER))
    public void send(String topic, String json) {
        stringRedisTemplate.convertAndSend(topic, json);
    }
}
