package org.danteplanner.backend.planner.entity;
import org.danteplanner.backend.shared.entity.EnumLookup;
import org.danteplanner.backend.shared.entity.ValuedEnum;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Vote type enum for planner voting system.
 * Only UP (upvote) is supported.
 */
public enum VoteType implements ValuedEnum {
    UP("UP");

    private final String value;

    VoteType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static VoteType fromValue(String value) {
        return EnumLookup.fromValue(VoteType.class, value);
    }
}
