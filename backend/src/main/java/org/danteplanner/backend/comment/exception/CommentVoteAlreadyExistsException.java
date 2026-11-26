package org.danteplanner.backend.comment.exception;

import org.danteplanner.backend.shared.exception.DomainException;
import org.danteplanner.backend.shared.exception.ErrorKind;

import lombok.Getter;

import java.util.UUID;

@Getter
public class CommentVoteAlreadyExistsException extends DomainException {

    private final UUID commentPublicId;
    private final Long userId;

    public CommentVoteAlreadyExistsException(UUID commentPublicId, Long userId) {
        super(ErrorKind.CONFLICT, "VOTE_ALREADY_EXISTS", String.format("User %d has already voted on comment %s. Votes are permanent and cannot be changed.", userId, commentPublicId));
        this.commentPublicId = commentPublicId;
        this.userId = userId;
    }
}
