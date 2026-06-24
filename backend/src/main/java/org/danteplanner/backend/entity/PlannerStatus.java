package org.danteplanner.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PlannerStatus implements ValuedEnum {
    DRAFT("draft"),
    SAVED("saved");

    private final String value;

    PlannerStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PlannerStatus fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (PlannerStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid status");
    }
}
