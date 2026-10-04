package org.danteplanner.backend.auth.service;

import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.user.service.UsersApiAdapter;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.auth.oauth.OAuthProvider;
import org.danteplanner.backend.auth.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.auth.oauth.OAuthTokens;
import org.danteplanner.backend.auth.oauth.OAuthUserInfo;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationService.
 *
 * <p>Tests OAuth authentication and token refresh flows
 * with all dependencies mocked.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private OAuthProviderRegistry providerRegistry;

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private UserService userService;

    @Mock
    private UserAccountLifecycleService lifecycleService;

    @Mock
    private OAuthProvider oauthProvider;

    @Mock
    private jakarta.servlet.http.HttpServletResponse response;

    private AuthenticationService authenticationService;

    private User testUser;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                providerRegistry,
                tokenGenerator,
                new UsersApiAdapter(userService, lifecycleService)
        );

        testUser = TestDataFactory.unsavedUser(123L);
    }

    @Nested
    @DisplayName("authenticateWithOAuth Tests")
    class AuthenticateWithOAuthTests {

        @Test
        @DisplayName("Should return user and tokens on successful OAuth for existing active user")
        void authenticateWithOAuth_WhenExistingActiveUser_ReturnsUserAndTokens() {
            // Arrange
            String providerName = "google";
            String code = "auth-code";
            String redirectUri = "http://localhost/callback";
            String codeVerifier = "verifier";

            OAuthTokens oauthTokens = new OAuthTokens("oauth-access", "oauth-refresh", null);
            OAuthUserInfo userInfo = new OAuthUserInfo("google-123", "test@example.com");

            when(providerRegistry.getProvider(providerName)).thenReturn(oauthProvider);
            when(oauthProvider.exchangeCodeForTokens(code, redirectUri, codeVerifier)).thenReturn(oauthTokens);
            when(oauthProvider.getUserInfo(oauthTokens)).thenReturn(userInfo);
            when(userService.findActiveByProvider(AuthProviderType.GOOGLE, "google-123"))
                    .thenReturn(Optional.of(testUser));
            when(tokenGenerator.generateAccessToken(testUser.getId(), UserRole.NORMAL))
                    .thenReturn("jwt-access-token");
            when(tokenGenerator.generateRefreshToken(testUser.getId()))
                    .thenReturn("jwt-refresh-token");

            // Act
            AuthenticationService.AuthResult result = authenticationService.authenticateWithOAuth(
                    providerName, code, redirectUri, codeVerifier
            );

            // Assert
            assertNotNull(result);
            assertEquals(testUser.getId(), result.user().id());
            assertEquals("jwt-access-token", result.accessToken());
            assertEquals("jwt-refresh-token", result.refreshToken());
            assertFalse(result.reactivated());
        }

        @Test
        @DisplayName("Should throw when provider not found")
        void authenticateWithOAuth_WhenProviderNotFound_Throws() {
            // Arrange
            when(providerRegistry.getProvider("unknown"))
                    .thenThrow(new IllegalArgumentException("Unknown OAuth provider: unknown"));

            // Act & Assert
            assertThrows(
                    IllegalArgumentException.class,
                    () -> authenticationService.authenticateWithOAuth(
                            "unknown", "code", "redirect", "verifier"
                    )
            );

            // Verify no tokens generated
            verify(tokenGenerator, never()).generateAccessToken(any(), any());
            verify(tokenGenerator, never()).generateRefreshToken(any());
        }

        @Test
        @DisplayName("Should create new user when not found")
        void authenticateWithOAuth_WhenUserNotFound_CreatesNewUser() {
            // Arrange
            OAuthTokens oauthTokens = new OAuthTokens("access", null, null);
            OAuthUserInfo userInfo = new OAuthUserInfo("provider-id-123", "user@email.com");

            when(providerRegistry.getProvider(anyString())).thenReturn(oauthProvider);
            when(oauthProvider.exchangeCodeForTokens(any(), any(), any())).thenReturn(oauthTokens);
            when(oauthProvider.getUserInfo(any(OAuthTokens.class))).thenReturn(userInfo);
            when(userService.findActiveByProvider(any(), any()))
                    .thenReturn(Optional.empty());
            when(userService.findByProvider(any(), any()))
                    .thenReturn(Optional.empty());
            when(userService.findOrCreateUser("google", Map.of("id", "provider-id-123", "email", "user@email.com")))
                    .thenReturn(testUser);
            when(tokenGenerator.generateAccessToken(testUser.getId(), UserRole.NORMAL)).thenReturn("access");
            when(tokenGenerator.generateRefreshToken(testUser.getId())).thenReturn("refresh");

            // Act
            AuthenticationService.AuthResult result = authenticationService.authenticateWithOAuth(
                    "google", "code", "redirect", "verifier");

            // Assert
            assertFalse(result.reactivated());
            assertEquals(testUser.getId(), result.user().id());
            assertEquals("access", result.accessToken());
            assertEquals("refresh", result.refreshToken());
        }

        @Test
        @DisplayName("Should reactivate soft-deleted user on OAuth login")
        void authenticateWithOAuth_WhenUserSoftDeleted_ReactivatesUser() {
            // Arrange
            User deletedUser = User.builder()
                    .id(456L)
                    .email("deleted@example.com")
                    .provider(AuthProviderType.GOOGLE)
                    .providerId("deleted-123")
                    .usernameEpithet("W_CORP")
                    .usernameSuffix("test2")
                    .build();
            deletedUser.softDelete(java.time.Instant.now().plusSeconds(86400 * 30));

            OAuthTokens oauthTokens = new OAuthTokens("access", null, null);
            OAuthUserInfo userInfo = new OAuthUserInfo("deleted-123", "deleted@example.com");

            when(providerRegistry.getProvider("google")).thenReturn(oauthProvider);
            when(oauthProvider.exchangeCodeForTokens(any(), any(), any())).thenReturn(oauthTokens);
            when(oauthProvider.getUserInfo(any(OAuthTokens.class))).thenReturn(userInfo);
            when(userService.findActiveByProvider(AuthProviderType.GOOGLE, "deleted-123"))
                    .thenReturn(Optional.empty());
            when(userService.findByProvider(AuthProviderType.GOOGLE, "deleted-123"))
                    .thenReturn(Optional.of(deletedUser));
            when(tokenGenerator.generateAccessToken(any(), any())).thenReturn("access");
            when(tokenGenerator.generateRefreshToken(any())).thenReturn("refresh");

            // Act
            AuthenticationService.AuthResult result = authenticationService.authenticateWithOAuth(
                    "google", "code", "redirect", "verifier");

            // Assert
            assertTrue(result.reactivated());
            assertEquals(deletedUser.getId(), result.user().id());
            // Reactivation is only observable as a cleared deleted_at row, which needs a containerized test.
            verify(lifecycleService).reactivateAccount(deletedUser.getId());
        }
    }


    /**
     * TW6 — type enforcement at the facade seams, exercised with a REAL {@link JwtTokenService}
     * so minted tokens carry a real {@code type} claim and the typed parser actually enforces it.
     * A {@code @Mock TokenValidator} returns canned claims and can never exercise type enforcement,
     * so these cases wire their own facade with a real validator, leaving the mocked cases intact.
     */
}
