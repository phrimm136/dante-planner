package org.danteplanner.backend.moderation.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

import lombok.Getter;

@Getter
public class CommentReportAlreadyExistsException extends DomainException {

    private final Long commentId;
    private final Long userId;

    public CommentReportAlreadyExistsException(Long commentId, Long userId) {
        super(ErrorKind.CONFLICT, "COMMENT_REPORT_ALREADY_EXISTS", String.format("User %d has already reported comment %d. Reports cannot be submitted twice.", userId, commentId));
        this.commentId = commentId;
        this.userId = userId;
    }
}
