package org.danteplanner.backend.comment.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;


public class CommentForbiddenException extends DomainException {

    public CommentForbiddenException(String message) {
        super(ErrorKind.FORBIDDEN, "COMMENT_FORBIDDEN", message);
    }
}
