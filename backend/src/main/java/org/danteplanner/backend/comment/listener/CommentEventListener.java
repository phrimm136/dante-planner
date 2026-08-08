package org.danteplanner.backend.comment.listener;

import lombok.RequiredArgsConstructor;
import org.danteplanner.backend.comment.event.CommentCreatedEvent;
import org.danteplanner.backend.shared.entity.SseEventType;
import org.danteplanner.backend.shared.sse.SsePublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Fans a committed comment out to the planner's readers over Redis pub/sub.
 */
@Component
@RequiredArgsConstructor
public class CommentEventListener {

    private final SsePublisher ssePublisher;

    /**
     * Announce a committed comment to everyone reading its planner.
     *
     * @param event the committed comment
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCommentCreated(CommentCreatedEvent event) {
        ssePublisher.publishCommentEvent(event.plannerId(), SseEventType.COMMENT_ADDED,
                event.commentPublicId().toString(), event.authorUserId(), event.payload());
    }
}
