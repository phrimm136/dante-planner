package org.danteplanner.backend.shared.outbox.service;

import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SsePublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The pushes one dispatch owes, held until its transaction commits.
 *
 * <p>An arm calls these methods where it would otherwise have published. Nothing leaves the process
 * until {@link #flush()} runs from the dispatcher's after-commit synchronization, because a push
 * sent inside the dispatch announces a row that a rollback then discards — and the frontend patches
 * its cache from the envelope, so the recipient would be left holding a notification no query could
 * ever confirm.</p>
 *
 * <p>Not a bean: a queue is one dispatch's worth of state, and a shared one would mix two.</p>
 */
public final class EffectPushQueue {

    private final SsePublisher ssePublisher;
    private final List<EffectPush> pushes = new ArrayList<>();

    public EffectPushQueue(SsePublisher ssePublisher) {
        this.ssePublisher = ssePublisher;
    }

    /**
     * Enqueue a push to one user's own devices.
     *
     * @param userId   the recipient
     * @param type     the event type
     * @param entityId the affected entity id
     * @param payload  the event payload
     */
    public void userEvent(Long userId, SseEventType type, String entityId, Object payload) {
        pushes.add(new UserPush(userId, type, entityId, payload));
    }

    /**
     * Enqueue a push to everyone reading a planner's comment thread.
     *
     * @param plannerId    the planner whose comment subscribers receive the event
     * @param type         the event type
     * @param entityId     the affected comment id
     * @param authorUserId the account whose action raised the event, skipped on delivery
     * @param payload      the event payload
     */
    public void commentEvent(UUID plannerId, SseEventType type, String entityId,
            Long authorUserId, Object payload) {
        pushes.add(new CommentPush(plannerId, type, entityId, authorUserId, payload));
    }

    /**
     * Enqueue a push to every connected client except the one whose action raised it.
     *
     * @param excludeUserId the user whose action raised the event
     * @param type          the event type
     * @param payload       the event payload
     */
    public void broadcast(Long excludeUserId, SseEventType type, Object payload) {
        pushes.add(new BroadcastPush(excludeUserId, type, payload));
    }

    /**
     * Send everything enqueued. Called once the dispatch is durable, and never before.
     */
    public void flush() {
        for (EffectPush push : pushes) {
            push.sendVia(ssePublisher);
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
