package org.danteplanner.backend.exception;

import lombok.Getter;

/**
 * Exception thrown when a user attempts to report a comment they've already reported.
 * Reports are immutable - users can only report once per comment.
 */
@Getter
public class CommentReportAlreadyExistsException extends RuntimeException {

    private final Long commentId;
    private final Long userId;

    public CommentReportAlreadyExistsException(Long commentId, Long userId) {
        super(String.format("User %d has already reported comment %d. Reports cannot be submitted twice.", userId, commentId));
        this.commentId = commentId;
        this.userId = userId;
    }
}
