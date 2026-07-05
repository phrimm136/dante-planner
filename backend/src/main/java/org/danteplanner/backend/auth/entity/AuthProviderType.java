package org.danteplanner.backend.auth.entity;

import org.danteplanner.backend.shared.entity.ValuedEnum;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AuthProviderType implements ValuedEnum {
    GOOGLE("google"),
    APPLE("apple"),
    SYSTEM("system");

    private final String value;

    AuthProviderType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AuthProviderType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (AuthProviderType provider : values()) {
            if (provider.value.equals(value)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Invalid provider");
    }
}
