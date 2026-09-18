package org.danteplanner.backend.shared.sse;

import java.io.IOException;
import java.util.UUID;

import org.danteplanner.backend.comment.service.PlannerCommentSseService;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;

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
            log.error("Failed to deserialize SSE envelope from channel {}", new String(message.getChannel(), StandardCharsets.UTF_8), e);
            return;
        }

        String topic = new String(message.getChannel(), StandardCharsets.UTF_8);
        SseChannel channel = SseChannel.fromTopic(topic);
        if (channel == null) {
            log.error("SSE envelope arrived on unrecognized channel {}; dropping", topic);
            return;
        }

        switch (channel) {
            case COMMENT -> dispatchComment(envelope);
            case BROADCAST -> sseService.broadcastToAll(
                    envelope.excludeUserId(), envelope.type().getValue(), clientPayload(envelope));
            case USER -> dispatchUser(envelope);
        }
    }

    private void dispatchComment(SseEnvelope envelope) {
        if (envelope.plannerId() == null || envelope.plannerId().isBlank()) {
            log.error("Comment SSE envelope missing plannerId; dropping");
            return;
        }
        plannerCommentSseService.broadcast(
                UUID.fromString(envelope.plannerId()), envelope.type().getValue(),
                clientPayload(envelope), envelope.excludeUserId());
    }

    private void dispatchUser(SseEnvelope envelope) {
        switch (envelope.type().userDelivery()) {
            case SETTINGS_CACHE -> sseService.invalidateSettingsCache(envelope.userId());
            case SUSPENSION_NOTICE -> sseService.notifyAccountSuspended(
                    envelope.userId(), clientPayload(envelope));
            case EMITTERS -> sendToEmitters(envelope);
        }
    }

    private void sendToEmitters(SseEnvelope envelope) {
        UUID excludeDeviceId = envelope.excludeDeviceId() != null
                ? UUID.fromString(envelope.excludeDeviceId())
                : null;
        sseService.sendToUser(envelope.userId(), excludeDeviceId,
                envelope.type().getValue(), clientPayload(envelope));
    }

    private static Object clientPayload(SseEnvelope envelope) {
        return envelope.type().deliversRawPayload()
                ? envelope.payload()
                : ClientSseEvent.from(envelope);
    }
}
