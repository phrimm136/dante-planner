package org.danteplanner.backend.service.oauth;
import org.danteplanner.backend.shared.exception.InvalidRequestException;
import org.danteplanner.backend.auth.oauth.OAuthTokens;
import org.danteplanner.backend.auth.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.auth.oauth.OAuthUserInfo;
import org.danteplanner.backend.auth.oauth.OAuthProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.danteplanner.backend.support.Rejections.assertRejected;
import java.util.Locale;

/**
 * Unit tests for OAuthProviderRegistry.
 *
 * <p>Tests provider lookup and registration functionality.</p>
 */
class OAuthProviderRegistryTest {

    private OAuthProvider googleProvider;
    private OAuthProvider appleProvider;
    private OAuthProviderRegistry registry;

    @BeforeEach
    void setUp() {
        googleProvider = named("google");
        appleProvider = named("apple");

        registry = new OAuthProviderRegistry(List.of(googleProvider, appleProvider));
    }

    private static OAuthProvider named(String name) {
        return new OAuthProvider() {
            @Override
            public String getProviderName() {
                return name;
            }

            @Override
            public String buildAuthorizationUrl(String state, String codeChallenge) {
                throw new UnsupportedOperationException();
            }

            @Override
            public OAuthTokens exchangeCodeForTokens(String code, String redirectUri, String codeVerifier) {
                throw new UnsupportedOperationException();
            }

            @Override
            public OAuthUserInfo getUserInfo(String accessToken) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Nested
    class GetProviderTests {

        @Test
        void getProvider_WhenGoogle_ReturnsGoogleProvider() {
            // Act
            OAuthProvider result = registry.getProvider("google");

            // Assert
            assertNotNull(result);
            assertSame(googleProvider, result);
        }

        @Test
        void getProvider_WhenApple_ReturnsAppleProvider() {
            // Act
            OAuthProvider result = registry.getProvider("apple");

            // Assert
            assertNotNull(result);
            assertSame(appleProvider, result);
        }

        @Test
        void getProvider_WhenUnknownProvider_Throws() {
            // Act & Assert
            InvalidRequestException exception = assertThrows(
                    InvalidRequestException.class,
                    () -> registry.getProvider("unknown")
            );

            assertTrue(exception.getMessage().contains("unknown"),
                    "Exception message should contain the unknown provider name");
            assertTrue(exception.getMessage().toLowerCase(Locale.ROOT).contains("unknown oauth provider"),
                    "Exception message should indicate it's an unknown provider");
        }

        @Test
        void getProvider_WhenMixedCase_IsCaseInsensitive() {
            // Act
            OAuthProvider upperCase = registry.getProvider("GOOGLE");
            OAuthProvider mixedCase = registry.getProvider("GoOgLe");
            OAuthProvider lowerCase = registry.getProvider("google");

            // Assert
            assertSame(googleProvider, upperCase);
            assertSame(googleProvider, mixedCase);
            assertSame(googleProvider, lowerCase);
        }

        @Test
        void getProvider_WhenEmptyName_Throws() {
            // Act & Assert
            assertRejected(() -> registry.getProvider("")).withCode("UNKNOWN_OAUTH_PROVIDER");
        }
    }

    @Nested
    class HasProviderTests {

        @Test
        void hasProvider_WhenRegistered_ReturnsTrue() {
            // Act & Assert
            assertTrue(registry.hasProvider("google"));
            assertTrue(registry.hasProvider("apple"));
        }

        @Test
        void hasProvider_WhenUnregistered_ReturnsFalse() {
            // Act & Assert
            assertFalse(registry.hasProvider("discord"));
            assertFalse(registry.hasProvider("facebook"));
        }

        @Test
        void hasProvider_WhenMixedCase_IsCaseInsensitive() {
            // Act & Assert
            assertTrue(registry.hasProvider("GOOGLE"));
            assertTrue(registry.hasProvider("Google"));
            assertTrue(registry.hasProvider("gOoGlE"));
        }

        @Test
        void hasProvider_WhenEmptyName_ReturnsFalse() {
            // Act & Assert
            assertFalse(registry.hasProvider(""));
        }
    }

    @Nested
    class ConstructorTests {

        @Test
        void constructor_WhenEmptyProviderList_BuildsEmptyRegistry() {
            // Act
            OAuthProviderRegistry emptyRegistry = new OAuthProviderRegistry(Collections.emptyList());

            // Assert
            assertRejected(() -> emptyRegistry.getProvider("any")).withCode("UNKNOWN_OAUTH_PROVIDER");
            assertFalse(emptyRegistry.hasProvider("google"));
        }

        @Test
        void constructor_WhenSingleProvider_RegistersIt() {
            // Arrange
            OAuthProviderRegistry singleProviderRegistry =
                    new OAuthProviderRegistry(List.of(googleProvider));

            // Act & Assert
            assertTrue(singleProviderRegistry.hasProvider("google"));
            assertFalse(singleProviderRegistry.hasProvider("apple"));
            assertSame(googleProvider, singleProviderRegistry.getProvider("google"));
        }

        @Test
        void constructor_WhenDuplicateProviderNames_Throws() {
            // Arrange
            OAuthProvider googleProvider1 = named("google");
            OAuthProvider googleProvider2 = named("google");

            // Act & Assert - Collectors.toMap throws on duplicate keys
            assertThatThrownBy(() -> new OAuthProviderRegistry(List.of(googleProvider1, googleProvider2)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("Duplicate key google");
        }
    }

    @Nested
    class ProviderInterfaceTests {

        @Test
        void getProvider_WhenCalled_ReturnsInterfaceImplementation() {
            // Act
            OAuthProvider provider = registry.getProvider("google");

            // Assert
            assertNotNull(provider);
            assertEquals("google", provider.getProviderName());
        }
    }
}
