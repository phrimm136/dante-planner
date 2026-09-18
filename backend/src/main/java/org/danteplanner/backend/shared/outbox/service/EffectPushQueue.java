package org.danteplanner.backend.shared.outbox.service;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SsePublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public final class EffectPushQueue {

    private final SsePublisher ssePublisher;
    private final List<EffectPush> pushes = new ArrayList<>();

    public EffectPushQueue(SsePublisher ssePublisher) {
        this.ssePublisher = ssePublisher;
    }

    public void userEvent(Long userId, SseEventType type, String entityId, Object payload) {
        pushes.add(new UserPush(userId, type, entityId, payload));
    }

    public void commentEvent(UUID plannerId, SseEventType type, String entityId,
            Long authorUserId, Object payload) {
        pushes.add(new CommentPush(plannerId, type, entityId, authorUserId, payload));
    }

    public void broadcast(Long excludeUserId, SseEventType type, Object payload) {
        pushes.add(new BroadcastPush(excludeUserId, type, payload));
    }

    public void flush() {
        for (EffectPush push : pushes) {
            try {
                push.sendVia(ssePublisher);
            } catch (RuntimeException e) {
                log.error("Announcing a committed effect failed; the row it names stands", e);
            }
        }
    }

    private sealed interface EffectPush permits UserPush, CommentPush, BroadcastPush {

        void sendVia(SsePublisher publisher);
    }

    private record UserPush(Long userId, SseEventType type, String entityId, Object payload)
            implements EffectPush {

        @Override
        public void sendVia(SsePublisher publisher) {
            publisher.publishUserEvent(userId, type, entityId, payload);
        }
    }

    private record CommentPush(UUID plannerId, SseEventType type, String entityId,
            Long authorUserId, Object payload) implements EffectPush {

        @Override
        public void sendVia(SsePublisher publisher) {
            publisher.publishCommentEvent(plannerId, type, entityId, authorUserId, payload);
        }
    }

    private record BroadcastPush(Long excludeUserId, SseEventType type, Object payload)
            implements EffectPush {

        @Override
        public void sendVia(SsePublisher publisher) {
            publisher.publishBroadcast(excludeUserId, type, payload);
        }
    }
}
