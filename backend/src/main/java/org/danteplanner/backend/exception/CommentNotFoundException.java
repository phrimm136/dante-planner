package org.danteplanner.backend.exception;

import java.util.UUID;

import lombok.Getter;

@Getter
public class CommentNotFoundException extends RuntimeException {

    private final Long commentId;

    public CommentNotFoundException(Long commentId) {
        super("Comment not found with id: " + commentId);
        this.commentId = commentId;
    }

    public CommentNotFoundException(UUID publicId) {
        super("Comment not found with publicId: " + publicId);
        this.commentId = null;
    }
}
