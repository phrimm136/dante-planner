package org.danteplanner.backend.planner.entity;
import org.danteplanner.backend.shared.entity.EnumLookup;
import org.danteplanner.backend.shared.entity.ValuedEnum;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;

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

    public static PlannerStatus fromValue(String value) {
        return EnumLookup.fromValue(PlannerStatus.class, value);
    }

    @JsonCreator
    public static @Nullable PlannerStatus fromJson(@Nullable String value) {
        // Jackson passes null for an absent field; returning null keeps the property optional,
        // where EnumLookup would reject it as unknown.
        return value == null ? null : fromValue(value);
    }
}
