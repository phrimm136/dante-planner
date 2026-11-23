package org.danteplanner.backend.user.api;

import org.danteplanner.backend.shared.entity.EnumLookup;
import org.danteplanner.backend.shared.entity.ValuedEnum;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;

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

    public static AuthProviderType fromValue(String value) {
        return EnumLookup.fromValue(AuthProviderType.class, value);
    }

    @JsonCreator
    public static @Nullable AuthProviderType fromJson(@Nullable String value) {
        // Jackson passes null for an absent field; returning null keeps the property optional,
        // where EnumLookup would reject it as unknown.
        return value == null ? null : fromValue(value);
    }
}
