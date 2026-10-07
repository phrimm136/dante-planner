package org.danteplanner.backend.converter;
import org.danteplanner.backend.user.entity.AuthProviderTypeConverter;

import org.danteplanner.backend.user.api.AuthProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AuthProviderTypeConverter.
 *
 * <p>Verifies the converter maps {@link AuthProviderType} to the lowercase
 * {@code google}/{@code apple} values persisted in the provider VARCHAR column.</p>
 */
class AuthProviderTypeConverterTest {

    private AuthProviderTypeConverter converter;

    @BeforeEach
    void setUp() {
        converter = new AuthProviderTypeConverter();
    }

    @Nested
    class ToDatabaseColumn {

        @Test
        void convertToDatabaseColumn_WhenGoogle_ReturnsLowercaseGoogle() {
            assertThat(converter.convertToDatabaseColumn(AuthProviderType.GOOGLE)).isEqualTo("google");
        }

        @Test
        void convertToDatabaseColumn_WhenApple_ReturnsLowercaseApple() {
            assertThat(converter.convertToDatabaseColumn(AuthProviderType.APPLE)).isEqualTo("apple");
        }

        // V009 deleted-user sentinel
        @Test
        void convertToDatabaseColumn_WhenSystem_ReturnsLowercaseSystem() {
            assertThat(converter.convertToDatabaseColumn(AuthProviderType.SYSTEM)).isEqualTo("system");
        }

        @Test
        void convertToDatabaseColumn_WhenNull_ReturnsNull() {
            assertThat(converter.convertToDatabaseColumn(null)).isNull();
        }
    }

    @Nested
    class ToEntityAttribute {

        @Test
        void convertToEntityAttribute_WhenGoogle_ReturnsGoogleEnum() {
            assertThat(converter.convertToEntityAttribute("google")).isEqualTo(AuthProviderType.GOOGLE);
        }

        @Test
        void convertToEntityAttribute_WhenApple_ReturnsAppleEnum() {
            assertThat(converter.convertToEntityAttribute("apple")).isEqualTo(AuthProviderType.APPLE);
        }

        // V009 deleted-user sentinel
        @Test
        void convertToEntityAttribute_WhenSystem_ReturnsSystemEnum() {
            assertThat(converter.convertToEntityAttribute("system")).isEqualTo(AuthProviderType.SYSTEM);
        }

        @Test
        void convertToEntityAttribute_WhenNull_ReturnsNull() {
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }
    }
}
