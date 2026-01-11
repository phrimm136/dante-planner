package org.danteplanner.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Vote type enum for planner voting system.
 * Only UP (upvote) is supported.
 */
public enum VoteType {
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
        for (VoteType type : VoteType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown VoteType value: " + value);
    }
}
