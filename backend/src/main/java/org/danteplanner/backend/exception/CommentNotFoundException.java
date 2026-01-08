package org.danteplanner.backend.exception;

import lombok.Getter;

@Getter
public class CommentNotFoundException extends RuntimeException {

    private final Long commentId;

    public CommentNotFoundException(Long commentId) {
        super("Comment not found with id: " + commentId);
        this.commentId = commentId;
    }
}
