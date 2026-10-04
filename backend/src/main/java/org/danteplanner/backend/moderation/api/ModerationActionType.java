package org.danteplanner.backend.moderation.api;

public enum ModerationActionType {
    BAN,
    UNBAN,
    TIMEOUT,
    CLEAR_TIMEOUT,
    PROMOTE,
    DEMOTE,
    DELETE_PLANNER,
    DELETE_COMMENT,
    UNPUBLISH_PLANNER,
    HIDE_FROM_RECOMMENDED,
    UNHIDE_FROM_RECOMMENDED
}
