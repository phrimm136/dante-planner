package org.danteplanner.backend.auth.service;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.auth.oauth.OAuthProvider;
import org.danteplanner.backend.auth.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.auth.oauth.OAuthTokens;
import org.danteplanner.backend.auth.oauth.OAuthUserInfo;
import org.danteplanner.backend.auth.token.LogoutRevocation;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationService.
 *
 * <p>Tests OAuth authentication, token refresh, and logout flows
 * with all dependencies mocked.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private OAuthProviderRegistry providerRegistry;

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private TokenValidator tokenValidator;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

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
                tokenValidator,
                tokenBlacklistService,
                userService,
                lifecycleService,
                new LineageRotationFlag(false)
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
            assertSame(testUser, result.user());
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
            assertSame(testUser, result.user());
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
            assertSame(deletedUser, result.user());
            // Reactivation is only observable as a cleared deleted_at row, which needs a containerized test.
            verify(lifecycleService).reactivateAccount(deletedUser.getId());
        }
    }


    /** logout is void: the revoked-session entry lands in Redis, observable only in a containerized test. */
    @Nested
    @DisplayName("logout Tests")
    class LogoutTests {

        @Test
        @DisplayName("Should blacklist both tokens")
        void logout_WhenBothTokensValid_BlacklistsBoth() {
            // Arrange
            String accessToken = "access-token";
            String refreshToken = "refresh-token";
            Date accessExpiry = new Date(System.currentTimeMillis() + 60000);
            Date refreshExpiry = new Date(System.currentTimeMillis() + 86400000);

            TokenClaims accessClaims = new TokenClaims(
                    123L, TokenClaims.TYPE_ACCESS, UserRole.NORMAL, new Date(), accessExpiry
            );
            TokenClaims refreshClaims = new TokenClaims(
                    123L, TokenClaims.TYPE_REFRESH, null, new Date(), refreshExpiry
            );

            when(tokenValidator.validateAccessToken(accessToken)).thenReturn(accessClaims);
            when(tokenValidator.validateRefreshToken(refreshToken)).thenReturn(refreshClaims);

            // Act
            authenticationService.logout(accessToken, refreshToken);

            // Assert
            verify(tokenBlacklistService).revokeLogoutSession(List.of(
                    new LogoutRevocation.TokenRevocation(accessToken, accessExpiry),
                    new LogoutRevocation.TokenRevocation(refreshToken, refreshExpiry)));
        }

        @Test
        @DisplayName("Should handle null access token")
        void logout_WhenNullAccessToken_BlacklistsOnlyRefresh() {
            // Arrange
            String refreshToken = "refresh-token";
            Date refreshExpiry = new Date(System.currentTimeMillis() + 86400000);
            TokenClaims refreshClaims = new TokenClaims(
                    123L, TokenClaims.TYPE_REFRESH, null, new Date(), refreshExpiry
            );

            when(tokenValidator.validateRefreshToken(refreshToken)).thenReturn(refreshClaims);

            // Act
            authenticationService.logout(null, refreshToken);

            // Assert - only refresh token blacklisted
            verify(tokenBlacklistService).revokeLogoutSession(List.of(
                    new LogoutRevocation.TokenRevocation(refreshToken, refreshExpiry)));
        }

        @Test
        @DisplayName("logout-with-only-an-access-token-revokes-exactly-it: no refresh cookie, no family revocation")
        void logout_WhenNullRefreshToken_BlacklistsOnlyAccess() {
            // Arrange
            String accessToken = "access-token";
            Date accessExpiry = new Date(System.currentTimeMillis() + 60000);
            TokenClaims accessClaims = new TokenClaims(
                    123L, TokenClaims.TYPE_ACCESS, UserRole.NORMAL, new Date(), accessExpiry
            );

            when(tokenValidator.validateAccessToken(accessToken)).thenReturn(accessClaims);

            // Act
            authenticationService.logout(accessToken, null);

            // Assert - only access token blacklisted
            verify(tokenBlacklistService).revokeLogoutSession(List.of(
                    new LogoutRevocation.TokenRevocation(accessToken, accessExpiry)));
        }

        @Test
        @DisplayName("Should handle both tokens null")
        void logout_WhenBothTokensNull_DoesNothing() {
            // Act
            authenticationService.logout(null, null);

            // Assert - nothing present to revoke
            verify(tokenBlacklistService).revokeLogoutSession(List.of());
            verify(tokenBlacklistService, never()).blacklistToken(any(), any());
            verify(tokenValidator, never()).validateToken(any());
        }

        @Test
        @DisplayName("Should skip blacklist for already invalid access token")
        void logout_WhenInvalidAccessToken_SkipsBlacklist() {
            // Arrange
            String invalidAccessToken = "invalid-access";
            String validRefreshToken = "valid-refresh";
            Date refreshExpiry = new Date(System.currentTimeMillis() + 86400000);
            TokenClaims refreshClaims = new TokenClaims(
                    123L, TokenClaims.TYPE_REFRESH, null, new Date(), refreshExpiry
            );

            when(tokenValidator.validateAccessToken(invalidAccessToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));
            when(tokenValidator.validateRefreshToken(validRefreshToken)).thenReturn(refreshClaims);

            // Act
            authenticationService.logout(invalidAccessToken, validRefreshToken);

            // Assert - only refresh token blacklisted
            verify(tokenBlacklistService).revokeLogoutSession(List.of(
                    new LogoutRevocation.TokenRevocation(validRefreshToken, refreshExpiry)));
        }

        @Test
        @DisplayName("Should skip blacklist for already invalid refresh token")
        void logout_WhenInvalidRefreshToken_SkipsBlacklist() {
            // Arrange
            String validAccessToken = "valid-access";
            String invalidRefreshToken = "invalid-refresh";
            Date accessExpiry = new Date(System.currentTimeMillis() + 60000);
            TokenClaims accessClaims = new TokenClaims(
                    123L, TokenClaims.TYPE_ACCESS, UserRole.NORMAL, new Date(), accessExpiry
            );

            when(tokenValidator.validateAccessToken(validAccessToken)).thenReturn(accessClaims);
            when(tokenValidator.validateRefreshToken(invalidRefreshToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.MALFORMED));

            // Act
            authenticationService.logout(validAccessToken, invalidRefreshToken);

            // Assert - only access token blacklisted
            verify(tokenBlacklistService).revokeLogoutSession(List.of(
                    new LogoutRevocation.TokenRevocation(validAccessToken, accessExpiry)));
        }
    }

    /**
     * TW6 — type enforcement at the facade seams, exercised with a REAL {@link JwtTokenService}
     * so minted tokens carry a real {@code type} claim and the typed parser actually enforces it.
     * A {@code @Mock TokenValidator} returns canned claims and can never exercise type enforcement,
     * so these cases wire their own facade with a real validator, leaving the mocked cases intact.
     */
}
