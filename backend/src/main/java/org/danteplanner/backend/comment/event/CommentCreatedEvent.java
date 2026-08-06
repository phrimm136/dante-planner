package org.danteplanner.backend.comment.event;

import org.danteplanner.backend.comment.dto.CommentTreeNode;

import java.util.UUID;

/**
 * A comment that committed and still has to reach the planner's readers.
 *
 * <p>Carries the rendered tree node rather than the entity: the listener runs after commit with no
 * live transaction, so the author lookup the payload needs has to happen before it.</p>
 *
 * @param plannerId       the planner the comment belongs to
 * @param commentPublicId the comment's public UUID
 * @param authorUserId    the comment author, excluded from delivery
 * @param payload         the rendered comment recipients insert into their thread
 */
public record CommentCreatedEvent(
        UUID plannerId,
        UUID commentPublicId,
        Long authorUserId,
        CommentTreeNode payload) {
}
