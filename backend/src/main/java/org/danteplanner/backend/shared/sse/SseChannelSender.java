package org.danteplanner.backend.shared.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.QueryTimeoutException;
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
 * <p>Retried for the two shapes a failover takes: the connection cannot be acquired, or it can and
 * the command then times out. Only the first is a {@code RedisConnectionFailureException} — Lettuce
 * raises {@code RedisCommandTimeoutException} for the second, which Spring translates to
 * {@link QueryTimeoutException}, so naming the connection failure alone leaves the more common
 * failover symptom unretried. Anything else is a fault the same call would reproduce.</p>
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
     * @throws RedisConnectionFailureException when every attempt failed to reach the primary
     */
    @Retryable(retryFor = {RedisConnectionFailureException.class, QueryTimeoutException.class},
            maxAttempts = SseConstants.PUBLISH_MAX_ATTEMPTS,
            backoff = @Backoff(delay = SseConstants.PUBLISH_RETRY_DELAY_MS,
                    multiplier = SseConstants.PUBLISH_RETRY_MULTIPLIER))
    public void send(String topic, String json) {
        stringRedisTemplate.convertAndSend(topic, json);
    }
}
