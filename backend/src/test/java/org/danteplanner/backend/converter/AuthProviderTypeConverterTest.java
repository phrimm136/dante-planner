package org.danteplanner.backend.converter;
import org.danteplanner.backend.auth.converter.AuthProviderTypeConverter;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    @DisplayName("convertToDatabaseColumn")
    class ToDatabaseColumn {

        @Test
        @DisplayName("GOOGLE maps to lowercase 'google'")
        void convertToDatabaseColumn_WhenGoogle_ReturnsLowercaseGoogle() {
            assertEquals("google", converter.convertToDatabaseColumn(AuthProviderType.GOOGLE));
        }

        @Test
        @DisplayName("APPLE maps to lowercase 'apple'")
        void convertToDatabaseColumn_WhenApple_ReturnsLowercaseApple() {
            assertEquals("apple", converter.convertToDatabaseColumn(AuthProviderType.APPLE));
        }

        @Test
        @DisplayName("SYSTEM maps to lowercase 'system' (V009 deleted-user sentinel)")
        void convertToDatabaseColumn_WhenSystem_ReturnsLowercaseSystem() {
            assertEquals("system", converter.convertToDatabaseColumn(AuthProviderType.SYSTEM));
        }

        @Test
        @DisplayName("null maps to null")
        void convertToDatabaseColumn_WhenNull_ReturnsNull() {
            assertNull(converter.convertToDatabaseColumn(null));
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute")
    class ToEntityAttribute {

        @Test
        @DisplayName("'google' maps to GOOGLE")
        void convertToEntityAttribute_WhenGoogle_ReturnsGoogleEnum() {
            assertEquals(AuthProviderType.GOOGLE, converter.convertToEntityAttribute("google"));
        }

        @Test
        @DisplayName("'apple' maps to APPLE")
        void convertToEntityAttribute_WhenApple_ReturnsAppleEnum() {
            assertEquals(AuthProviderType.APPLE, converter.convertToEntityAttribute("apple"));
        }

        @Test
        @DisplayName("'system' maps to SYSTEM (V009 deleted-user sentinel)")
        void convertToEntityAttribute_WhenSystem_ReturnsSystemEnum() {
            assertEquals(AuthProviderType.SYSTEM, converter.convertToEntityAttribute("system"));
        }

        @Test
        @DisplayName("null maps to null")
        void convertToEntityAttribute_WhenNull_ReturnsNull() {
            assertNull(converter.convertToEntityAttribute(null));
        }
    }
}
