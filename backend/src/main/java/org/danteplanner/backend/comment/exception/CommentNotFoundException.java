package org.danteplanner.backend.comment.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

import java.util.UUID;

import lombok.Getter;

@Getter
public class CommentNotFoundException extends DomainException {

    private final Long commentId;

    public CommentNotFoundException(Long commentId) {
        super(ErrorKind.NOT_FOUND, "COMMENT_NOT_FOUND", "Comment not found with id: " + commentId);
        this.commentId = commentId;
    }

    public CommentNotFoundException(UUID publicId) {
        super(ErrorKind.NOT_FOUND, "COMMENT_NOT_FOUND", "Comment not found with publicId: " + publicId);
        this.commentId = null;
    }
}
