package org.danteplanner.backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum UserRole {
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
        for (UserRole role : UserRole.values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown UserRole value: " + value);
    }

    /**
     * Check if a string value is a valid user role.
     *
     * @param value the role value to check
     * @return true if valid, false otherwise
     */
    public static boolean isValid(String value) {
        for (UserRole role : UserRole.values()) {
            if (role.value.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if this role has higher or equal rank than another role.
     * ADMIN (3) > MODERATOR (2) > NORMAL (1)
     * Uses explicit rank field to avoid fragility from enum reordering.
     *
     * @param other the role to compare against
     * @return true if this role outranks or equals the other role
     */
    public boolean hasRankAtLeast(UserRole other) {
        return this.rank >= other.rank;
    }

    /**
     * Check if this role is strictly higher than another role.
     * Uses explicit rank field to avoid fragility from enum reordering.
     *
     * @param other the role to compare against
     * @return true if this role strictly outranks the other role
     */
    public boolean outranks(UserRole other) {
        return this.rank > other.rank;
    }
}
