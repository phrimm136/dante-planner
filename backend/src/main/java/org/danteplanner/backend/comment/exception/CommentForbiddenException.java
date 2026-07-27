package org.danteplanner.backend.comment.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

import lombok.Getter;

@Getter
public class CommentForbiddenException extends DomainException {

    private final Long commentId;

    public CommentForbiddenException(String message) {
        super(ErrorKind.FORBIDDEN, "COMMENT_FORBIDDEN", message);
        this.commentId = null;
    }

    public CommentForbiddenException(Long commentId, String message) {
        super(ErrorKind.FORBIDDEN, "COMMENT_FORBIDDEN", message);
        this.commentId = commentId;
    }
}
