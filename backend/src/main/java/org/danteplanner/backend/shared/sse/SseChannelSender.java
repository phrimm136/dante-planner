package org.danteplanner.backend.shared.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * <p>A private, self-invoked method is never seen by a proxy, so an annotation on it is inert.</p>
 *
 * <p>Only a connection that cannot be acquired is a {@code RedisConnectionFailureException};
 * Lettuce raises {@code RedisCommandTimeoutException} when the connection is acquired and the
 * command then times out, which Spring translates to {@link QueryTimeoutException}.</p>
 */
@Component
@RequiredArgsConstructor
public class SseChannelSender {

    private final StringRedisTemplate stringRedisTemplate;

    @Retryable(retryFor = {RedisConnectionFailureException.class, QueryTimeoutException.class},
            maxAttempts = SseConstants.PUBLISH_MAX_ATTEMPTS,
            backoff = @Backoff(delay = SseConstants.PUBLISH_RETRY_DELAY_MS,
                    multiplier = SseConstants.PUBLISH_RETRY_MULTIPLIER))
    public void send(String topic, String json) {
        stringRedisTemplate.convertAndSend(topic, json);
    }
}
