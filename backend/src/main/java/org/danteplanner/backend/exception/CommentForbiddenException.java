package org.danteplanner.backend.exception;

import lombok.Getter;

@Getter
public class CommentForbiddenException extends RuntimeException {

    private final Long commentId;

    public CommentForbiddenException(String message) {
        super(message);
        this.commentId = null;
    }

    public CommentForbiddenException(Long commentId, String message) {
        super(message);
        this.commentId = commentId;
    }
}
