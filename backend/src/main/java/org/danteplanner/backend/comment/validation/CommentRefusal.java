package org.danteplanner.backend.comment.validation;

import org.danteplanner.backend.comment.exception.CommentForbiddenException;

public enum CommentRefusal {
    EDIT_DELETED("Cannot edit a deleted comment"),
    VOTE_DELETED("Cannot vote on a deleted comment"),
    REPORT_DELETED("Cannot report a deleted comment"),
    EDIT_NOT_AUTHOR("Only the author can edit this comment"),
    DELETE_NOT_AUTHOR("Only the author can delete this comment"),
    TOGGLE_NOT_AUTHOR("Only the author can toggle notification settings"),
    OTHER_PLANNER("Parent comment belongs to a different planner"),
    REPLY_DELETED("Cannot reply to deleted top-level comment");

    private final String message;

    CommentRefusal(String message) {
        this.message = message;
    }

    public CommentForbiddenException toException() {
        return new CommentForbiddenException(message);
    }
}
