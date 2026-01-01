package org.danteplanner.backend.facade;

import org.danteplanner.backend.entity.User;
import org.danteplanner.backend.exception.InvalidTokenException;
import org.danteplanner.backend.service.UserService;
import org.danteplanner.backend.service.oauth.OAuthProvider;
import org.danteplanner.backend.service.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.service.oauth.OAuthTokens;
import org.danteplanner.backend.service.oauth.OAuthUserInfo;
import org.danteplanner.backend.service.token.TokenBlacklistService;
import org.danteplanner.backend.service.token.TokenClaims;
import org.danteplanner.backend.service.token.TokenGenerator;
import org.danteplanner.backend.service.token.TokenValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationFacade.
 *
 * <p>Tests OAuth authentication, token refresh, and logout flows
 * with all dependencies mocked.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationFacadeTest {

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
    private OAuthProvider oauthProvider;

    private AuthenticationFacade authenticationFacade;

    private User testUser;

    @BeforeEach
    void setUp() {
        authenticationFacade = new AuthenticationFacade(
                providerRegistry,
                tokenGenerator,
                tokenValidator,
                tokenBlacklistService,
                userService
        );

        testUser = User.builder()
                .id(123L)
                .email("test@example.com")
                .provider("google")
                .providerId("google-123")
                .build();
    }

    @Nested
    @DisplayName("authenticateWithOAuth Tests")
    class AuthenticateWithOAuthTests {

        @Test
        @DisplayName("Should return user and tokens on successful OAuth")
        void authenticateWithOAuth_returnsUserAndTokens() {
            // Arrange
            String providerName = "google";
            String code = "auth-code";
            String redirectUri = "http://localhost/callback";
            String codeVerifier = "verifier";

            OAuthTokens oauthTokens = new OAuthTokens("oauth-access", "oauth-refresh", null, 3600L);
            OAuthUserInfo userInfo = new OAuthUserInfo("google-123", "test@example.com");

            when(providerRegistry.getProvider(providerName)).thenReturn(oauthProvider);
            when(oauthProvider.exchangeCodeForTokens(code, redirectUri, codeVerifier)).thenReturn(oauthTokens);
            when(oauthProvider.getUserInfo("oauth-access")).thenReturn(userInfo);
            when(userService.findOrCreateUser(eq(providerName), any())).thenReturn(testUser);
            when(tokenGenerator.generateAccessToken(testUser.getId(), testUser.getEmail()))
                    .thenReturn("jwt-access-token");
            when(tokenGenerator.generateRefreshToken(testUser.getId(), testUser.getEmail()))
                    .thenReturn("jwt-refresh-token");

            // Act
            AuthenticationFacade.AuthResult result = authenticationFacade.authenticateWithOAuth(
                    providerName, code, redirectUri, codeVerifier
            );

            // Assert
            assertNotNull(result);
            assertSame(testUser, result.user());
            assertEquals("jwt-access-token", result.accessToken());
            assertEquals("jwt-refresh-token", result.refreshToken());

            // Verify flow
            verify(providerRegistry).getProvider(providerName);
            verify(oauthProvider).exchangeCodeForTokens(code, redirectUri, codeVerifier);
            verify(oauthProvider).getUserInfo("oauth-access");
            verify(userService).findOrCreateUser(eq(providerName), any());
            verify(tokenGenerator).generateAccessToken(testUser.getId(), testUser.getEmail());
            verify(tokenGenerator).generateRefreshToken(testUser.getId(), testUser.getEmail());
        }

        @Test
        @DisplayName("Should throw when provider not found")
        void authenticateWithOAuth_throwsWhenProviderNotFound() {
            // Arrange
            when(providerRegistry.getProvider("unknown"))
                    .thenThrow(new IllegalArgumentException("Unknown OAuth provider: unknown"));

            // Act & Assert
            assertThrows(
                    IllegalArgumentException.class,
                    () -> authenticationFacade.authenticateWithOAuth(
                            "unknown", "code", "redirect", "verifier"
                    )
            );

            // Verify no tokens generated
            verify(tokenGenerator, never()).generateAccessToken(any(), any());
            verify(tokenGenerator, never()).generateRefreshToken(any(), any());
        }

        @Test
        @DisplayName("Should pass user info map to findOrCreateUser")
        void authenticateWithOAuth_passesCorrectUserInfoMap() {
            // Arrange
            OAuthTokens oauthTokens = new OAuthTokens("access", null, null, null);
            OAuthUserInfo userInfo = new OAuthUserInfo("provider-id-123", "user@email.com");

            when(providerRegistry.getProvider(anyString())).thenReturn(oauthProvider);
            when(oauthProvider.exchangeCodeForTokens(any(), any(), any())).thenReturn(oauthTokens);
            when(oauthProvider.getUserInfo(any())).thenReturn(userInfo);
            when(userService.findOrCreateUser(any(), any())).thenReturn(testUser);
            when(tokenGenerator.generateAccessToken(any(), any())).thenReturn("access");
            when(tokenGenerator.generateRefreshToken(any(), any())).thenReturn("refresh");

            // Act
            authenticationFacade.authenticateWithOAuth("google", "code", "redirect", "verifier");

            // Assert
            verify(userService).findOrCreateUser(eq("google"), argThat(map ->
                    "provider-id-123".equals(map.get("id")) &&
                    "user@email.com".equals(map.get("email"))
            ));
        }
    }

    @Nested
    @DisplayName("refreshTokens Tests")
    class RefreshTokensTests {

        @Test
        @DisplayName("Should generate new tokens and blacklist old")
        void refreshTokens_generatesNewTokensAndBlacklistsOld() {
            // Arrange
            String oldRefreshToken = "old-refresh-token";
            Date expiration = new Date(System.currentTimeMillis() + 86400000);

            TokenClaims claims = new TokenClaims(
                    testUser.getId(),
                    testUser.getEmail(),
                    TokenClaims.TYPE_REFRESH,
                    new Date(),
                    expiration
            );

            when(tokenValidator.validateToken(oldRefreshToken)).thenReturn(claims);
            when(tokenBlacklistService.isBlacklisted(oldRefreshToken)).thenReturn(false);
            when(userService.findById(testUser.getId())).thenReturn(testUser);
            when(tokenGenerator.generateAccessToken(testUser.getId(), testUser.getEmail()))
                    .thenReturn("new-access-token");
            when(tokenGenerator.generateRefreshToken(testUser.getId(), testUser.getEmail()))
                    .thenReturn("new-refresh-token");

            // Act
            AuthenticationFacade.AuthResult result = authenticationFacade.refreshTokens(oldRefreshToken);

            // Assert
            assertNotNull(result);
            assertSame(testUser, result.user());
            assertEquals("new-access-token", result.accessToken());
            assertEquals("new-refresh-token", result.refreshToken());

            // Verify old token blacklisted
            verify(tokenBlacklistService).blacklistToken(oldRefreshToken, expiration);
        }

        @Test
        @DisplayName("Should throw for invalid token type")
        void refreshTokens_throwsForInvalidTokenType() {
            // Arrange - access token instead of refresh token
            String accessToken = "access-token";
            TokenClaims accessClaims = new TokenClaims(
                    123L,
                    "test@example.com",
                    TokenClaims.TYPE_ACCESS, // Wrong type
                    new Date(),
                    new Date(System.currentTimeMillis() + 60000)
            );

            when(tokenValidator.validateToken(accessToken)).thenReturn(accessClaims);

            // Act & Assert
            InvalidTokenException exception = assertThrows(
                    InvalidTokenException.class,
                    () -> authenticationFacade.refreshTokens(accessToken)
            );

            assertEquals(InvalidTokenException.Reason.INVALID_TYPE, exception.getReason());

            // Verify no tokens generated and no blacklist
            verify(tokenGenerator, never()).generateAccessToken(any(), any());
            verify(tokenBlacklistService, never()).blacklistToken(any(), any());
        }

        @Test
        @DisplayName("Should throw for blacklisted refresh token")
        void refreshTokens_throwsForBlacklistedToken() {
            // Arrange
            String blacklistedToken = "blacklisted-refresh-token";
            TokenClaims claims = new TokenClaims(
                    123L,
                    "test@example.com",
                    TokenClaims.TYPE_REFRESH,
                    new Date(),
                    new Date(System.currentTimeMillis() + 60000)
            );

            when(tokenValidator.validateToken(blacklistedToken)).thenReturn(claims);
            when(tokenBlacklistService.isBlacklisted(blacklistedToken)).thenReturn(true);

            // Act & Assert
            InvalidTokenException exception = assertThrows(
                    InvalidTokenException.class,
                    () -> authenticationFacade.refreshTokens(blacklistedToken)
            );

            assertEquals(InvalidTokenException.Reason.REVOKED, exception.getReason());

            // Verify no tokens generated
            verify(tokenGenerator, never()).generateAccessToken(any(), any());
        }

        @Test
        @DisplayName("Should throw for expired refresh token")
        void refreshTokens_throwsForExpiredToken() {
            // Arrange
            String expiredToken = "expired-refresh-token";

            when(tokenValidator.validateToken(expiredToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));

            // Act & Assert
            InvalidTokenException exception = assertThrows(
                    InvalidTokenException.class,
                    () -> authenticationFacade.refreshTokens(expiredToken)
            );

            assertEquals(InvalidTokenException.Reason.EXPIRED, exception.getReason());
        }
    }

    @Nested
    @DisplayName("logout Tests")
    class LogoutTests {

        @Test
        @DisplayName("Should blacklist both tokens")
        void logout_blacklistsBothTokens() {
            // Arrange
            String accessToken = "access-token";
            String refreshToken = "refresh-token";
            Date accessExpiry = new Date(System.currentTimeMillis() + 60000);
            Date refreshExpiry = new Date(System.currentTimeMillis() + 86400000);

            TokenClaims accessClaims = new TokenClaims(
                    123L, "test@example.com", TokenClaims.TYPE_ACCESS, new Date(), accessExpiry
            );
            TokenClaims refreshClaims = new TokenClaims(
                    123L, "test@example.com", TokenClaims.TYPE_REFRESH, new Date(), refreshExpiry
            );

            when(tokenValidator.validateToken(accessToken)).thenReturn(accessClaims);
            when(tokenValidator.validateToken(refreshToken)).thenReturn(refreshClaims);

            // Act
            authenticationFacade.logout(accessToken, refreshToken);

            // Assert
            verify(tokenBlacklistService).blacklistToken(accessToken, accessExpiry);
            verify(tokenBlacklistService).blacklistToken(refreshToken, refreshExpiry);
        }

        @Test
        @DisplayName("Should handle null access token")
        void logout_handlesNullAccessToken() {
            // Arrange
            String refreshToken = "refresh-token";
            Date refreshExpiry = new Date(System.currentTimeMillis() + 86400000);
            TokenClaims refreshClaims = new TokenClaims(
                    123L, "test@example.com", TokenClaims.TYPE_REFRESH, new Date(), refreshExpiry
            );

            when(tokenValidator.validateToken(refreshToken)).thenReturn(refreshClaims);

            // Act
            authenticationFacade.logout(null, refreshToken);

            // Assert - only refresh token blacklisted
            verify(tokenBlacklistService).blacklistToken(refreshToken, refreshExpiry);
            verify(tokenValidator, times(1)).validateToken(anyString());
        }

        @Test
        @DisplayName("Should handle null refresh token")
        void logout_handlesNullRefreshToken() {
            // Arrange
            String accessToken = "access-token";
            Date accessExpiry = new Date(System.currentTimeMillis() + 60000);
            TokenClaims accessClaims = new TokenClaims(
                    123L, "test@example.com", TokenClaims.TYPE_ACCESS, new Date(), accessExpiry
            );

            when(tokenValidator.validateToken(accessToken)).thenReturn(accessClaims);

            // Act
            authenticationFacade.logout(accessToken, null);

            // Assert - only access token blacklisted
            verify(tokenBlacklistService).blacklistToken(accessToken, accessExpiry);
            verify(tokenValidator, times(1)).validateToken(anyString());
        }

        @Test
        @DisplayName("Should handle both tokens null")
        void logout_handlesBothTokensNull() {
            // Act
            authenticationFacade.logout(null, null);

            // Assert - no blacklist operations
            verify(tokenBlacklistService, never()).blacklistToken(any(), any());
            verify(tokenValidator, never()).validateToken(any());
        }

        @Test
        @DisplayName("Should skip blacklist for already invalid access token")
        void logout_skipsBlacklistForInvalidAccessToken() {
            // Arrange
            String invalidAccessToken = "invalid-access";
            String validRefreshToken = "valid-refresh";
            Date refreshExpiry = new Date(System.currentTimeMillis() + 86400000);
            TokenClaims refreshClaims = new TokenClaims(
                    123L, "test@example.com", TokenClaims.TYPE_REFRESH, new Date(), refreshExpiry
            );

            when(tokenValidator.validateToken(invalidAccessToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));
            when(tokenValidator.validateToken(validRefreshToken)).thenReturn(refreshClaims);

            // Act
            authenticationFacade.logout(invalidAccessToken, validRefreshToken);

            // Assert - only refresh token blacklisted
            verify(tokenBlacklistService, times(1)).blacklistToken(any(), any());
            verify(tokenBlacklistService).blacklistToken(validRefreshToken, refreshExpiry);
        }

        @Test
        @DisplayName("Should skip blacklist for already invalid refresh token")
        void logout_skipsBlacklistForInvalidRefreshToken() {
            // Arrange
            String validAccessToken = "valid-access";
            String invalidRefreshToken = "invalid-refresh";
            Date accessExpiry = new Date(System.currentTimeMillis() + 60000);
            TokenClaims accessClaims = new TokenClaims(
                    123L, "test@example.com", TokenClaims.TYPE_ACCESS, new Date(), accessExpiry
            );

            when(tokenValidator.validateToken(validAccessToken)).thenReturn(accessClaims);
            when(tokenValidator.validateToken(invalidRefreshToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.MALFORMED));

            // Act
            authenticationFacade.logout(validAccessToken, invalidRefreshToken);

            // Assert - only access token blacklisted
            verify(tokenBlacklistService, times(1)).blacklistToken(any(), any());
            verify(tokenBlacklistService).blacklistToken(validAccessToken, accessExpiry);
        }
    }
}
