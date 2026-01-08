package org.danteplanner.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Vote type enum for comment voting system.
 * Currently UP only (upvote-only to reduce echo chamber effect).
 * Enum allows future expansion (HELPFUL, INSIGHTFUL, etc.).
 */
public enum CommentVoteType {
    UP("UP");

    private final String value;

    CommentVoteType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static CommentVoteType fromValue(String value) {
        for (CommentVoteType type : CommentVoteType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown CommentVoteType value: " + value);
    }
}
