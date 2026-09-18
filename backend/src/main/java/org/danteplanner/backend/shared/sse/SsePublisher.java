package org.danteplanner.backend.shared.sse;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SsePublisher {

    private static final String DROPPED_COUNTER = "sse.publish.dropped";

    private static final String UNSERIALIZABLE_COUNTER = "sse.publish.unserializable";

    private final SseChannelSender channelSender;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public void publishUserEvent(Long userId, SseEventType type, String entityId, Object payload) {
        publishUserEvent(userId, null, type, entityId, payload);
    }

    public void publishUserEvent(Long userId, UUID excludeDeviceId, SseEventType type,
            String entityId, Object payload) {
        publish(SseChannel.USER, SseEnvelope.userEvent(userId, type, entityId,
                excludeDeviceId != null ? excludeDeviceId.toString() : null, payload));
    }

    public void publishSettingsInvalidation(Long userId) {
        publish(SseChannel.USER, SseEnvelope.settingsInvalidation(userId));
    }

    public void publishCommentEvent(UUID plannerId, SseEventType type, String entityId,
            Long authorUserId, Object payload) {
        publish(SseChannel.COMMENT,
                SseEnvelope.commentEvent(plannerId, type, entityId, authorUserId, payload));
    }

    public void publishBroadcast(Long excludeUserId, SseEventType type, Object payload) {
        publish(SseChannel.BROADCAST, SseEnvelope.broadcast(excludeUserId, type, payload));
    }

    public void publishAccountSuspended(
            Long userId, String reason, SuspensionType suspensionType, Integer durationMinutes) {
        publish(SseChannel.USER, SseEnvelope.accountSuspended(userId,
                AccountSuspendedPayload.of(suspensionType, reason, durationMinutes)));
    }

    private void publish(SseChannel channel, SseEnvelope envelope) {
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            meterRegistry.counter(UNSERIALIZABLE_COUNTER, "channel", channel.name()).increment();
            log.error("Failed to serialize SSE envelope for channel {} type {}", channel, envelope.type(), e);
            Sentry.captureException(e);
            return;
        }

        try {
            channelSender.send(channel.topic(), json);
        } catch (DataAccessException e) {
            meterRegistry.counter(DROPPED_COUNTER, "channel", channel.name()).increment();
            log.warn("SSE publish skipped, Redis unreachable (transient): channel {} type {}: {}",
                    channel, envelope.type(), e.getMessage());
        }
    }
}
