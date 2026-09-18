package org.danteplanner.backend.comment.entity;

import org.danteplanner.backend.shared.entity.EnumLookup;
import org.danteplanner.backend.shared.entity.ValuedEnum;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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
