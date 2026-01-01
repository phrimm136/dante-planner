package org.danteplanner.backend.service.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        // Create mock providers
        googleProvider = mock(OAuthProvider.class);
        when(googleProvider.getProviderName()).thenReturn("google");

        appleProvider = mock(OAuthProvider.class);
        when(appleProvider.getProviderName()).thenReturn("apple");

        // Create registry with mock providers
        registry = new OAuthProviderRegistry(List.of(googleProvider, appleProvider));
    }

    @Nested
    @DisplayName("getProvider Tests")
    class GetProviderTests {

        @Test
        @DisplayName("Should return Google provider for 'google'")
        void getProvider_returnsGoogleProviderForGoogle() {
            // Act
            OAuthProvider result = registry.getProvider("google");

            // Assert
            assertNotNull(result);
            assertSame(googleProvider, result);
        }

        @Test
        @DisplayName("Should return Apple provider for 'apple'")
        void getProvider_returnsAppleProviderForApple() {
            // Act
            OAuthProvider result = registry.getProvider("apple");

            // Assert
            assertNotNull(result);
            assertSame(appleProvider, result);
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for unknown provider")
        void getProvider_throwsForUnknownProvider() {
            // Act & Assert
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> registry.getProvider("unknown")
            );

            assertTrue(exception.getMessage().contains("unknown"),
                    "Exception message should contain the unknown provider name");
            assertTrue(exception.getMessage().toLowerCase().contains("unknown oauth provider"),
                    "Exception message should indicate it's an unknown provider");
        }

        @Test
        @DisplayName("Should be case-insensitive for provider names")
        void getProvider_isCaseInsensitive() {
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
        @DisplayName("Should throw for empty provider name")
        void getProvider_throwsForEmptyName() {
            // Act & Assert
            assertThrows(
                    IllegalArgumentException.class,
                    () -> registry.getProvider("")
            );
        }
    }

    @Nested
    @DisplayName("hasProvider Tests")
    class HasProviderTests {

        @Test
        @DisplayName("Should return true for registered provider")
        void hasProvider_returnsTrueForRegisteredProvider() {
            // Act & Assert
            assertTrue(registry.hasProvider("google"));
            assertTrue(registry.hasProvider("apple"));
        }

        @Test
        @DisplayName("Should return false for unregistered provider")
        void hasProvider_returnsFalseForUnregisteredProvider() {
            // Act & Assert
            assertFalse(registry.hasProvider("discord"));
            assertFalse(registry.hasProvider("facebook"));
        }

        @Test
        @DisplayName("Should be case-insensitive")
        void hasProvider_isCaseInsensitive() {
            // Act & Assert
            assertTrue(registry.hasProvider("GOOGLE"));
            assertTrue(registry.hasProvider("Google"));
            assertTrue(registry.hasProvider("gOoGlE"));
        }

        @Test
        @DisplayName("Should return false for empty name")
        void hasProvider_returnsFalseForEmptyName() {
            // Act & Assert
            assertFalse(registry.hasProvider(""));
        }
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should handle empty provider list")
        void constructor_handlesEmptyProviderList() {
            // Act
            OAuthProviderRegistry emptyRegistry = new OAuthProviderRegistry(Collections.emptyList());

            // Assert
            assertThrows(
                    IllegalArgumentException.class,
                    () -> emptyRegistry.getProvider("any")
            );
            assertFalse(emptyRegistry.hasProvider("google"));
        }

        @Test
        @DisplayName("Should handle single provider")
        void constructor_handlesSingleProvider() {
            // Arrange
            OAuthProviderRegistry singleProviderRegistry =
                    new OAuthProviderRegistry(List.of(googleProvider));

            // Act & Assert
            assertTrue(singleProviderRegistry.hasProvider("google"));
            assertFalse(singleProviderRegistry.hasProvider("apple"));
            assertSame(googleProvider, singleProviderRegistry.getProvider("google"));
        }

        @Test
        @DisplayName("Should throw IllegalStateException for duplicate provider names")
        void constructor_throwsForDuplicateProviderNames() {
            // Arrange
            OAuthProvider googleProvider1 = mock(OAuthProvider.class);
            when(googleProvider1.getProviderName()).thenReturn("google");

            OAuthProvider googleProvider2 = mock(OAuthProvider.class);
            when(googleProvider2.getProviderName()).thenReturn("google");

            // Act & Assert - Collectors.toMap throws on duplicate keys
            assertThrows(
                    IllegalStateException.class,
                    () -> new OAuthProviderRegistry(List.of(googleProvider1, googleProvider2))
            );
        }
    }

    @Nested
    @DisplayName("Provider Interface Integration")
    class ProviderInterfaceTests {

        @Test
        @DisplayName("Should return provider that implements OAuthProvider interface")
        void getProvider_returnsInterfaceImplementation() {
            // Act
            OAuthProvider provider = registry.getProvider("google");

            // Assert
            assertNotNull(provider);
            assertEquals("google", provider.getProviderName());
        }

        @Test
        @DisplayName("Returned provider should be usable for OAuth operations")
        void getProvider_returnedProviderIsUsable() {
            // Arrange
            OAuthTokens expectedTokens = new OAuthTokens("access", "refresh", null, 3600L);
            OAuthUserInfo expectedUserInfo = new OAuthUserInfo("123", "test@example.com");

            when(googleProvider.exchangeCodeForTokens("code", "redirect", "verifier"))
                    .thenReturn(expectedTokens);
            when(googleProvider.getUserInfo("access"))
                    .thenReturn(expectedUserInfo);

            // Act
            OAuthProvider provider = registry.getProvider("google");
            OAuthTokens tokens = provider.exchangeCodeForTokens("code", "redirect", "verifier");
            OAuthUserInfo userInfo = provider.getUserInfo("access");

            // Assert
            assertSame(expectedTokens, tokens);
            assertSame(expectedUserInfo, userInfo);
        }
    }
}
