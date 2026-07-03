package org.danteplanner.backend.comment.entity;

import org.danteplanner.backend.shared.entity.EnumLookup;
import org.danteplanner.backend.shared.entity.ValuedEnum;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Vote type enum for comment voting system.
 * Currently UP only (upvote-only to reduce echo chamber effect).
 * Enum allows future expansion (HELPFUL, INSIGHTFUL, etc.).
 */
public enum CommentVoteType implements ValuedEnum {
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
        return EnumLookup.fromValue(CommentVoteType.class, value);
    }
}
