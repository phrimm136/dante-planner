package org.danteplanner.backend.auth.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import org.danteplanner.backend.auth.entity.AuthProviderType;

/**
 * JPA AttributeConverter mapping {@link AuthProviderType} to the lowercase
 * {@code google}/{@code apple} values persisted in the {@code provider} VARCHAR column.
 *
 * <p>Used instead of {@code @Enumerated(STRING)}, which would persist {@code GOOGLE}/{@code APPLE}
 * and break the existing lowercase data and {@code UNIQUE(provider, providerId)} constraint.</p>
 */
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
