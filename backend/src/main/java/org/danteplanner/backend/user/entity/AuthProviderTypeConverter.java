package org.danteplanner.backend.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.jspecify.annotations.Nullable;

import org.danteplanner.backend.user.api.AuthProviderType;

@Converter
public class AuthProviderTypeConverter implements AttributeConverter<AuthProviderType, String> {

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable AuthProviderType attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public @Nullable AuthProviderType convertToEntityAttribute(@Nullable String dbData) {
        return dbData == null ? null : AuthProviderType.fromValue(dbData);
    }
}
