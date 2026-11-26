package org.danteplanner.backend.user.api;

import org.danteplanner.backend.shared.entity.EnumLookup;
import org.danteplanner.backend.shared.entity.ValuedEnum;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

public enum UserRole implements ValuedEnum {
    NORMAL("NORMAL", 1),
    MODERATOR("MODERATOR", 2),
    ADMIN("ADMIN", 3);

    private final String value;
    private final int rank;

    UserRole(String value, int rank) {
        this.value = value;
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static UserRole fromValue(String value) {
        return EnumLookup.fromValue(UserRole.class, value);
    }

    public static UserRole fromClaim(@Nullable String claim) {
        return Arrays.stream(values())
                .filter(role -> role.value.equals(claim))
                .findFirst()
                .orElse(NORMAL);
    }

    public boolean hasRankAtLeast(UserRole other) {
        return this.rank >= other.rank;
    }

    public boolean outranks(UserRole other) {
        return this.rank > other.rank;
    }
}
