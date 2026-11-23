package org.danteplanner.backend.shared.sse;

import java.nio.charset.StandardCharsets;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

@Component
@RequiredArgsConstructor
@Slf4j
public class SseRedisSubscriber implements MessageListener {

    private final SseService sseService;
    private final SseChannelHandler commentChannelHandler;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        String topic = new String(message.getChannel(), StandardCharsets.UTF_8);
        SseEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message.getBody(), SseEnvelope.Wire.class)
                    .on(SseChannel.fromTopic(topic));
        } catch (JacksonException | IllegalArgumentException e) {
            log.error("Failed to read SSE envelope from channel {}", topic, e);
            return;
        }

        switch (envelope) {
            case SseEnvelope.CommentEvent event -> commentChannelHandler.deliver(event,
                    clientPayload(event, event.payload(), ClientSseEvent.from(event)));
            case SseEnvelope.Broadcast broadcast -> sseService.broadcastToAll(broadcast.exclusion(),
                    broadcast.type().getValue(),
                    clientPayload(broadcast, broadcast.payload(), ClientSseEvent.from(broadcast)));
            case SseEnvelope.SettingsInvalidation invalidation ->
                    sseService.invalidateSettingsCache(invalidation.userId());
            case SseEnvelope.AccountSuspended suspended -> sseService.notifyAccountSuspended(suspended.userId(),
                    clientPayload(suspended, suspended.payload(), ClientSseEvent.from(suspended)));
            case SseEnvelope.UserEvent event -> sseService.sendToUser(event.userId(),
                    event.type().getValue(), clientPayload(event, event.payload(), ClientSseEvent.from(event)));
        }
    }

    private static Object clientPayload(SseEnvelope envelope, Object payload, ClientSseEvent event) {
        return envelope.type().deliversRawPayload() ? payload : event;
    }
}
