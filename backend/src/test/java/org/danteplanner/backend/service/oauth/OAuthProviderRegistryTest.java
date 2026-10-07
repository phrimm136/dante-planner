package org.danteplanner.backend.service.oauth;
import org.danteplanner.backend.auth.oauth.OAuthTokens;
import org.danteplanner.backend.auth.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.auth.oauth.OAuthUserInfo;
import org.danteplanner.backend.auth.oauth.OAuthProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
            assertThat(result).isNotNull();
            assertThat(result).isSameAs(googleProvider);
        }

        @Test
        void getProvider_WhenApple_ReturnsAppleProvider() {
            // Act
            OAuthProvider result = registry.getProvider("apple");

            // Assert
            assertThat(result).isNotNull();
            assertThat(result).isSameAs(appleProvider);
        }

        @Test
        void getProvider_WhenUnknownProvider_Throws() {
            // Act & Assert
            assertRejected(() -> registry.getProvider("unknown"))
                    .withCode("UNKNOWN_OAUTH_PROVIDER")
                    .satisfies(rejection -> {
                        assertThat(rejection.getMessage())
                                .as("Exception message should contain the unknown provider name")
                                .contains("unknown");
                        assertThat(rejection.getMessage().toLowerCase(Locale.ROOT))
                                .as("Exception message should indicate it's an unknown provider")
                                .contains("unknown oauth provider");
                    });
        }

        @Test
        void getProvider_WhenMixedCase_IsCaseInsensitive() {
            // Act
            OAuthProvider upperCase = registry.getProvider("GOOGLE");
            OAuthProvider mixedCase = registry.getProvider("GoOgLe");
            OAuthProvider lowerCase = registry.getProvider("google");

            // Assert
            assertThat(upperCase).isSameAs(googleProvider);
            assertThat(mixedCase).isSameAs(googleProvider);
            assertThat(lowerCase).isSameAs(googleProvider);
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
            assertThat(registry.hasProvider("google")).isTrue();
            assertThat(registry.hasProvider("apple")).isTrue();
        }

        @Test
        void hasProvider_WhenUnregistered_ReturnsFalse() {
            // Act & Assert
            assertThat(registry.hasProvider("discord")).isFalse();
            assertThat(registry.hasProvider("facebook")).isFalse();
        }

        @Test
        void hasProvider_WhenMixedCase_IsCaseInsensitive() {
            // Act & Assert
            assertThat(registry.hasProvider("GOOGLE")).isTrue();
            assertThat(registry.hasProvider("Google")).isTrue();
            assertThat(registry.hasProvider("gOoGlE")).isTrue();
        }

        @Test
        void hasProvider_WhenEmptyName_ReturnsFalse() {
            // Act & Assert
            assertThat(registry.hasProvider("")).isFalse();
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
            assertThat(emptyRegistry.hasProvider("google")).isFalse();
        }

        @Test
        void constructor_WhenSingleProvider_RegistersIt() {
            // Arrange
            OAuthProviderRegistry singleProviderRegistry =
                    new OAuthProviderRegistry(List.of(googleProvider));

            // Act & Assert
            assertThat(singleProviderRegistry.hasProvider("google")).isTrue();
            assertThat(singleProviderRegistry.hasProvider("apple")).isFalse();
            assertThat(singleProviderRegistry.getProvider("google")).isSameAs(googleProvider);
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
            assertThat(provider).isNotNull();
            assertThat(provider.getProviderName()).isEqualTo("google");
        }
    }
}
