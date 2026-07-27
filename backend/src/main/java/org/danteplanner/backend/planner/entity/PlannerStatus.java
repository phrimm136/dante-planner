package org.danteplanner.backend.planner.entity;
import org.danteplanner.backend.shared.entity.EnumLookup;
import org.danteplanner.backend.shared.entity.ValuedEnum;

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

    /**
     * @param value the stored value, or null for an absent JSON field
     * @return the matching constant, or null when the field was absent
     * @throws IllegalArgumentException if the value names no constant
     */
    @JsonCreator
    public static PlannerStatus fromValue(String value) {
        // Jackson passes null for an absent field; returning null keeps the property optional,
        // where EnumLookup would reject it as unknown.
        return value == null ? null : EnumLookup.fromValue(PlannerStatus.class, value);
    }
}
