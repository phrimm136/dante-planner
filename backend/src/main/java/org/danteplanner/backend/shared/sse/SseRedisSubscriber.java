package org.danteplanner.backend.shared.sse;

import java.io.IOException;
import java.util.UUID;

import org.danteplanner.backend.comment.service.PlannerCommentSseService;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis pub/sub listener that fans SSE envelopes out to this node's local emitters.
 *
 * <p>Deserializes each {@link SseEnvelope} received on the subscribed channel and
 * dispatches it to the local {@link SseService}, carrying the full envelope so the
 * recipient patches its cache from the payload rather than refetching. Parse failures
 * are logged and swallowed so a poison message never kills the listener thread.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SseRedisSubscriber implements MessageListener {

    private final SseService sseService;
    private final PlannerCommentSseService plannerCommentSseService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        SseEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message.getBody(), SseEnvelope.class);
        } catch (IOException e) {
            log.error("Failed to deserialize SSE envelope from channel {}", new String(message.getChannel()), e);
            return;
        }

        String channel = new String(message.getChannel());
        if (SseChannels.COMMENT.equals(channel)) {
            if (envelope.plannerId() == null || envelope.plannerId().isBlank()) {
                log.error("Comment SSE envelope missing plannerId; dropping");
                return;
            }
            plannerCommentSseService.broadcast(
                    UUID.fromString(envelope.plannerId()), envelope.type().getValue(), envelope);
        } else if (SseChannels.USER.equals(channel)) {
            if (envelope.type() == SseEventType.SETTINGS_INVALIDATED) {
                sseService.invalidateSettingsCache(envelope.userId());
            } else {
                UUID excludeDeviceId = envelope.excludeDeviceId() != null
                        ? UUID.fromString(envelope.excludeDeviceId())
                        : null;
                sseService.sendToUser(envelope.userId(), excludeDeviceId, envelope.type().getValue(), envelope);
            }
        }
    }
}
