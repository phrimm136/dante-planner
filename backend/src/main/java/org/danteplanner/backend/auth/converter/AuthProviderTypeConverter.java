package org.danteplanner.backend.auth.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.danteplanner.backend.auth.entity.AuthProviderType;

@Converter
public class AuthProviderTypeConverter implements AttributeConverter<AuthProviderType, String> {

    @Override
    public String convertToDatabaseColumn(AuthProviderType attribute) {
        return attribute == null ? null : attribute.getValue();
    }

    @Override
    public AuthProviderType convertToEntityAttribute(String dbData) {
        return AuthProviderType.fromValue(dbData);
    }
}
