package org.danteplanner.backend.converter;
import org.danteplanner.backend.user.entity.AuthProviderTypeConverter;

import org.danteplanner.backend.user.api.AuthProviderType;
import org.junit.jupiter.api.BeforeEach;
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
    class ToDatabaseColumn {

        @Test
        void convertToDatabaseColumn_WhenGoogle_ReturnsLowercaseGoogle() {
            assertEquals("google", converter.convertToDatabaseColumn(AuthProviderType.GOOGLE));
        }

        @Test
        void convertToDatabaseColumn_WhenApple_ReturnsLowercaseApple() {
            assertEquals("apple", converter.convertToDatabaseColumn(AuthProviderType.APPLE));
        }

        // V009 deleted-user sentinel
        @Test
        void convertToDatabaseColumn_WhenSystem_ReturnsLowercaseSystem() {
            assertEquals("system", converter.convertToDatabaseColumn(AuthProviderType.SYSTEM));
        }

        @Test
        void convertToDatabaseColumn_WhenNull_ReturnsNull() {
            assertNull(converter.convertToDatabaseColumn(null));
        }
    }

    @Nested
    class ToEntityAttribute {

        @Test
        void convertToEntityAttribute_WhenGoogle_ReturnsGoogleEnum() {
            assertEquals(AuthProviderType.GOOGLE, converter.convertToEntityAttribute("google"));
        }

        @Test
        void convertToEntityAttribute_WhenApple_ReturnsAppleEnum() {
            assertEquals(AuthProviderType.APPLE, converter.convertToEntityAttribute("apple"));
        }

        // V009 deleted-user sentinel
        @Test
        void convertToEntityAttribute_WhenSystem_ReturnsSystemEnum() {
            assertEquals(AuthProviderType.SYSTEM, converter.convertToEntityAttribute("system"));
        }

        @Test
        void convertToEntityAttribute_WhenNull_ReturnsNull() {
            assertNull(converter.convertToEntityAttribute(null));
        }
    }
}
