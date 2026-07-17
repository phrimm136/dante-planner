package org.danteplanner.backend.facade;
import org.danteplanner.backend.auth.facade.AuthenticationFacade;

import org.danteplanner.backend.auth.entity.AuthProviderType;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.entity.UserRole;
import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.user.repository.UserRepository;
import org.danteplanner.backend.user.service.UserAccountLifecycleService;
import org.danteplanner.backend.user.service.UserService;
import org.danteplanner.backend.auth.oauth.OAuthProvider;
import org.danteplanner.backend.auth.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.auth.oauth.OAuthTokens;
import org.danteplanner.backend.auth.oauth.OAuthUserInfo;
import org.danteplanner.backend.auth.token.JwtTokenService;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Optional;

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
    private UserAccountLifecycleService lifecycleService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuthProvider oauthProvider;

    @Mock
    private org.danteplanner.backend.auth.token.RefreshRotationService refreshRotationService;

    @Mock
    private org.danteplanner.backend.shared.util.CookieUtils cookieUtils;

    @Mock
    private org.danteplanner.backend.shared.config.JwtProperties jwtProperties;

    @Mock
    private jakarta.servlet.http.HttpServletResponse response;

    private AuthenticationFacade authenticationFacade;

    private User testUser;

    @BeforeEach
    void setUp() {
        authenticationFacade = new AuthenticationFacade(
                providerRegistry,
                tokenGenerator,
                tokenValidator,
                tokenBlacklistService,
                userService,
                lifecycleService,
                userRepository,
                refreshRotationService,
                cookieUtils,
                jwtProperties,
                new LineageRotationFlag(false)
        );

        testUser = User.builder()
                .id(123L)
                .email("test@example.com")
                .provider(AuthProviderType.GOOGLE)
                .providerId("google-123")
                .usernameEpithet("W_CORP")
                .usernameSuffix("test1")
                .build();
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

            OAuthTokens oauthTokens = new OAuthTokens("oauth-access", "oauth-refresh", null, 3600L);
            OAuthUserInfo userInfo = new OAuthUserInfo("google-123", "test@example.com");

            when(providerRegistry.getProvider(providerName)).thenReturn(oauthProvider);
            when(oauthProvider.exchangeCodeForTokens(code, redirectUri, codeVerifier)).thenReturn(oauthTokens);
            when(oauthProvider.getUserInfo(any(OAuthTokens.class))).thenReturn(userInfo);
            when(userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(AuthProviderType.GOOGLE, "google-123"))
                    .thenReturn(Optional.of(testUser));
            when(tokenGenerator.generateAccessToken(eq(testUser.getId()), any(UserRole.class)))
                    .thenReturn("jwt-access-token");
            when(tokenGenerator.generateRefreshToken(testUser.getId()))
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
            assertFalse(result.reactivated());

            // Verify flow
            verify(providerRegistry).getProvider(providerName);
            verify(oauthProvider).exchangeCodeForTokens(code, redirectUri, codeVerifier);
            verify(oauthProvider).getUserInfo(any(OAuthTokens.class));
            verify(userRepository).findByProviderAndProviderIdAndDeletedAtIsNull(AuthProviderType.GOOGLE, "google-123");
            verify(tokenGenerator).generateAccessToken(eq(testUser.getId()), any(UserRole.class));
            verify(tokenGenerator).generateRefreshToken(testUser.getId());
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
                    () -> authenticationFacade.authenticateWithOAuth(
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
            OAuthTokens oauthTokens = new OAuthTokens("access", null, null, null);
            OAuthUserInfo userInfo = new OAuthUserInfo("provider-id-123", "user@email.com");

            when(providerRegistry.getProvider(anyString())).thenReturn(oauthProvider);
            when(oauthProvider.exchangeCodeForTokens(any(), any(), any())).thenReturn(oauthTokens);
            when(oauthProvider.getUserInfo(any(OAuthTokens.class))).thenReturn(userInfo);
            when(userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(any(), any()))
                    .thenReturn(Optional.empty());
            when(userRepository.findByProviderAndProviderId(any(), any()))
                    .thenReturn(Optional.empty());
            when(userService.findOrCreateUser(any(), any())).thenReturn(testUser);
            when(tokenGenerator.generateAccessToken(any(), any())).thenReturn("access");
            when(tokenGenerator.generateRefreshToken(any())).thenReturn("refresh");

            // Act
            AuthenticationFacade.AuthResult result = authenticationFacade.authenticateWithOAuth(
                    "google", "code", "redirect", "verifier");

            // Assert
            assertFalse(result.reactivated());
            verify(userService).findOrCreateUser(eq("google"), argThat(map ->
                    "provider-id-123".equals(map.get("id")) &&
                    "user@email.com".equals(map.get("email"))
            ));
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

            OAuthTokens oauthTokens = new OAuthTokens("access", null, null, null);
            OAuthUserInfo userInfo = new OAuthUserInfo("deleted-123", "deleted@example.com");

            when(providerRegistry.getProvider("google")).thenReturn(oauthProvider);
            when(oauthProvider.exchangeCodeForTokens(any(), any(), any())).thenReturn(oauthTokens);
            when(oauthProvider.getUserInfo(any(OAuthTokens.class))).thenReturn(userInfo);
            when(userRepository.findByProviderAndProviderIdAndDeletedAtIsNull(AuthProviderType.GOOGLE, "deleted-123"))
                    .thenReturn(Optional.empty());
            when(userRepository.findByProviderAndProviderId(AuthProviderType.GOOGLE, "deleted-123"))
                    .thenReturn(Optional.of(deletedUser));
            when(tokenGenerator.generateAccessToken(any(), any())).thenReturn("access");
            when(tokenGenerator.generateRefreshToken(any())).thenReturn("refresh");

            // Act
            AuthenticationFacade.AuthResult result = authenticationFacade.authenticateWithOAuth(
                    "google", "code", "redirect", "verifier");

            // Assert
            assertTrue(result.reactivated());
            assertSame(deletedUser, result.user());
            verify(lifecycleService).reactivateAccount(deletedUser.getId());
        }
    }

    @Nested
    @DisplayName("refreshTokens Tests")
    class RefreshTokensTests {

        @Test
        @DisplayName("Should generate new tokens and blacklist old")
        void refreshTokens_WhenValid_GeneratesNewTokensAndBlacklistsOld() {
            // Arrange
            String oldRefreshToken = "old-refresh-token";
            Date expiration = new Date(System.currentTimeMillis() + 86400000);

            TokenClaims claims = new TokenClaims(
                    testUser.getId(),
                    testUser.getEmail(),
                    TokenClaims.TYPE_REFRESH,
                    null, // refresh tokens have no role
                    new Date(),
                    expiration
            );

            lenient().when(tokenValidator.validateToken(oldRefreshToken)).thenReturn(claims);
            lenient().when(tokenValidator.validateRefreshToken(oldRefreshToken)).thenReturn(claims);
            when(tokenBlacklistService.isBlacklisted(oldRefreshToken)).thenReturn(false);
            when(userService.findById(testUser.getId())).thenReturn(testUser);
            when(tokenGenerator.generateAccessToken(eq(testUser.getId()), any(UserRole.class)))
                    .thenReturn("new-access-token");
            when(tokenGenerator.generateRefreshToken(testUser.getId()))
                    .thenReturn("new-refresh-token");

            // Act
            AuthenticationFacade.AuthResult result = authenticationFacade.refreshTokens(oldRefreshToken, response);

            // Assert
            assertNotNull(result);
            assertSame(testUser, result.user());
            assertEquals("new-access-token", result.accessToken());
            assertEquals("new-refresh-token", result.refreshToken());
            assertFalse(result.reactivated());

            // Verify old token blacklisted (rotation with grace period)
            verify(tokenBlacklistService).blacklistTokenForRotation(oldRefreshToken, expiration);
        }

        @Test
        @DisplayName("Should throw for invalid token type")
        void refreshTokens_WhenInvalidTokenType_Throws() {
            // Arrange - access token instead of refresh token
            String accessToken = "access-token";
            TokenClaims accessClaims = new TokenClaims(
                    123L,
                    "test@example.com",
                    TokenClaims.TYPE_ACCESS, // Wrong type
                    UserRole.NORMAL,
                    new Date(),
                    new Date(System.currentTimeMillis() + 60000)
            );

            lenient().when(tokenValidator.validateToken(accessToken)).thenReturn(accessClaims);
            lenient().when(tokenValidator.validateRefreshToken(accessToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.INVALID_TYPE));

            // Act & Assert
            InvalidTokenException exception = assertThrows(
                    InvalidTokenException.class,
                    () -> authenticationFacade.refreshTokens(accessToken, response)
            );

            assertEquals(InvalidTokenException.Reason.INVALID_TYPE, exception.getReason());

            // Verify no tokens generated and no blacklist
            verify(tokenGenerator, never()).generateAccessToken(any(), any());
            verify(tokenBlacklistService, never()).blacklistToken(any(), any());
        }

        @Test
        @DisplayName("Should throw for blacklisted refresh token")
        void refreshTokens_WhenBlacklistedToken_Throws() {
            // Arrange
            String blacklistedToken = "blacklisted-refresh-token";
            TokenClaims claims = new TokenClaims(
                    123L,
                    "test@example.com",
                    TokenClaims.TYPE_REFRESH,
                    null, // refresh tokens have no role
                    new Date(),
                    new Date(System.currentTimeMillis() + 60000)
            );

            lenient().when(tokenValidator.validateToken(blacklistedToken)).thenReturn(claims);
            lenient().when(tokenValidator.validateRefreshToken(blacklistedToken)).thenReturn(claims);
            when(tokenBlacklistService.isBlacklisted(blacklistedToken)).thenReturn(true);

            // Act & Assert
            InvalidTokenException exception = assertThrows(
                    InvalidTokenException.class,
                    () -> authenticationFacade.refreshTokens(blacklistedToken, response)
            );

            assertEquals(InvalidTokenException.Reason.REVOKED, exception.getReason());

            // Verify no tokens generated
            verify(tokenGenerator, never()).generateAccessToken(any(), any());
        }

        @Test
        @DisplayName("Should throw for expired refresh token")
        void refreshTokens_WhenExpiredToken_Throws() {
            // Arrange
            String expiredToken = "expired-refresh-token";

            lenient().when(tokenValidator.validateToken(expiredToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));
            lenient().when(tokenValidator.validateRefreshToken(expiredToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));

            // Act & Assert
            InvalidTokenException exception = assertThrows(
                    InvalidTokenException.class,
                    () -> authenticationFacade.refreshTokens(expiredToken, response)
            );

            assertEquals(InvalidTokenException.Reason.EXPIRED, exception.getReason());
        }

        @Test
        @DisplayName("Should throw AccountDeletedException for deleted user")
        void refreshTokens_deletedUser_throwsException() {
            // Arrange
            String refreshToken = "valid-refresh-token";
            Date expiration = new Date(System.currentTimeMillis() + 86400000);

            TokenClaims claims = new TokenClaims(
                    testUser.getId(),
                    testUser.getEmail(),
                    TokenClaims.TYPE_REFRESH,
                    null, // refresh tokens have no role
                    new Date(),
                    expiration
            );

            // User is soft-deleted
            User deletedUser = User.builder()
                    .id(testUser.getId())
                    .email(testUser.getEmail())
                    .provider(AuthProviderType.GOOGLE)
                    .providerId("google-123")
                    .usernameEpithet("W_CORP")
                    .usernameSuffix("test3")
                    .build();
            deletedUser.softDelete(java.time.Instant.now().plusSeconds(86400 * 30));

            lenient().when(tokenValidator.validateToken(refreshToken)).thenReturn(claims);
            lenient().when(tokenValidator.validateRefreshToken(refreshToken)).thenReturn(claims);
            when(tokenBlacklistService.isBlacklisted(refreshToken)).thenReturn(false);
            when(userService.findById(testUser.getId())).thenReturn(deletedUser);

            // Act & Assert
            org.danteplanner.backend.user.exception.AccountDeletedException exception = assertThrows(
                    org.danteplanner.backend.user.exception.AccountDeletedException.class,
                    () -> authenticationFacade.refreshTokens(refreshToken, response)
            );

            assertEquals(testUser.getId(), exception.getUserId());

            // Verify old token was blacklisted before the check (rotation with grace period)
            verify(tokenBlacklistService).blacklistTokenForRotation(refreshToken, expiration);

            // Verify no new tokens generated
            verify(tokenGenerator, never()).generateAccessToken(any(), any());
            verify(tokenGenerator, never()).generateRefreshToken(any());
        }
    }

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
                    123L, "test@example.com", TokenClaims.TYPE_ACCESS, UserRole.NORMAL, new Date(), accessExpiry
            );
            TokenClaims refreshClaims = new TokenClaims(
                    123L, "test@example.com", TokenClaims.TYPE_REFRESH, null, new Date(), refreshExpiry
            );

            lenient().when(tokenValidator.validateToken(accessToken)).thenReturn(accessClaims);
            lenient().when(tokenValidator.validateToken(refreshToken)).thenReturn(refreshClaims);
            lenient().when(tokenValidator.validateAccessToken(accessToken)).thenReturn(accessClaims);
            lenient().when(tokenValidator.validateRefreshToken(refreshToken)).thenReturn(refreshClaims);

            // Act
            authenticationFacade.logout(accessToken, refreshToken);

            // Assert
            verify(tokenBlacklistService).blacklistToken(accessToken, accessExpiry);
            verify(tokenBlacklistService).blacklistToken(refreshToken, refreshExpiry);
        }

        @Test
        @DisplayName("Should handle null access token")
        void logout_WhenNullAccessToken_BlacklistsOnlyRefresh() {
            // Arrange
            String refreshToken = "refresh-token";
            Date refreshExpiry = new Date(System.currentTimeMillis() + 86400000);
            TokenClaims refreshClaims = new TokenClaims(
                    123L, "test@example.com", TokenClaims.TYPE_REFRESH, null, new Date(), refreshExpiry
            );

            lenient().when(tokenValidator.validateToken(refreshToken)).thenReturn(refreshClaims);
            when(tokenValidator.validateRefreshToken(refreshToken)).thenReturn(refreshClaims);

            // Act
            authenticationFacade.logout(null, refreshToken);

            // Assert - only refresh token blacklisted
            verify(tokenBlacklistService).blacklistToken(refreshToken, refreshExpiry);
            verify(tokenValidator, times(1)).validateRefreshToken(anyString());
        }

        @Test
        @DisplayName("Should handle null refresh token")
        void logout_WhenNullRefreshToken_BlacklistsOnlyAccess() {
            // Arrange
            String accessToken = "access-token";
            Date accessExpiry = new Date(System.currentTimeMillis() + 60000);
            TokenClaims accessClaims = new TokenClaims(
                    123L, "test@example.com", TokenClaims.TYPE_ACCESS, UserRole.NORMAL, new Date(), accessExpiry
            );

            lenient().when(tokenValidator.validateToken(accessToken)).thenReturn(accessClaims);
            when(tokenValidator.validateAccessToken(accessToken)).thenReturn(accessClaims);

            // Act
            authenticationFacade.logout(accessToken, null);

            // Assert - only access token blacklisted
            verify(tokenBlacklistService).blacklistToken(accessToken, accessExpiry);
            verify(tokenValidator, times(1)).validateAccessToken(anyString());
        }

        @Test
        @DisplayName("Should handle both tokens null")
        void logout_WhenBothTokensNull_DoesNothing() {
            // Act
            authenticationFacade.logout(null, null);

            // Assert - no blacklist operations
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
                    123L, "test@example.com", TokenClaims.TYPE_REFRESH, null, new Date(), refreshExpiry
            );

            lenient().when(tokenValidator.validateToken(invalidAccessToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));
            lenient().when(tokenValidator.validateToken(validRefreshToken)).thenReturn(refreshClaims);
            lenient().when(tokenValidator.validateAccessToken(invalidAccessToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.EXPIRED));
            lenient().when(tokenValidator.validateRefreshToken(validRefreshToken)).thenReturn(refreshClaims);

            // Act
            authenticationFacade.logout(invalidAccessToken, validRefreshToken);

            // Assert - only refresh token blacklisted
            verify(tokenBlacklistService, times(1)).blacklistToken(any(), any());
            verify(tokenBlacklistService).blacklistToken(validRefreshToken, refreshExpiry);
        }

        @Test
        @DisplayName("Should skip blacklist for already invalid refresh token")
        void logout_WhenInvalidRefreshToken_SkipsBlacklist() {
            // Arrange
            String validAccessToken = "valid-access";
            String invalidRefreshToken = "invalid-refresh";
            Date accessExpiry = new Date(System.currentTimeMillis() + 60000);
            TokenClaims accessClaims = new TokenClaims(
                    123L, "test@example.com", TokenClaims.TYPE_ACCESS, UserRole.NORMAL, new Date(), accessExpiry
            );

            lenient().when(tokenValidator.validateToken(validAccessToken)).thenReturn(accessClaims);
            lenient().when(tokenValidator.validateToken(invalidRefreshToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.MALFORMED));
            lenient().when(tokenValidator.validateAccessToken(validAccessToken)).thenReturn(accessClaims);
            lenient().when(tokenValidator.validateRefreshToken(invalidRefreshToken))
                    .thenThrow(new InvalidTokenException(InvalidTokenException.Reason.MALFORMED));

            // Act
            authenticationFacade.logout(validAccessToken, invalidRefreshToken);

            // Assert - only access token blacklisted
            verify(tokenBlacklistService, times(1)).blacklistToken(any(), any());
            verify(tokenBlacklistService).blacklistToken(validAccessToken, accessExpiry);
        }
    }

    /**
     * TW6 — type enforcement at the facade seams, exercised with a REAL {@link JwtTokenService}
     * so minted tokens carry a real {@code type} claim and the typed parser actually enforces it.
     * A {@code @Mock TokenValidator} returns canned claims and can never exercise type enforcement,
     * so these cases wire their own facade with a real validator, leaving the mocked cases intact.
     */
    @Nested
    @DisplayName("Typed-parser enforcement with real tokens (TW6)")
    class TypedParserRealTokenTests {

        private static final Long REAL_USER_ID = 123L;

        private JwtTokenService realTokenService;
        private AuthenticationFacade realFacade;

        @BeforeEach
        void setUpRealValidator() throws Exception {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            byte[] aesKey = new byte[32];
            new SecureRandom().nextBytes(aesKey);

            JwtProperties realProperties = new JwtProperties();
            realProperties.setPrivateKey(keyPair.getPrivate());
            realProperties.setPublicKey(keyPair.getPublic());
            realProperties.setEncryptionKeyBytes(aesKey);
            realProperties.setAccessTokenExpiry(900000L);
            realProperties.setRefreshTokenExpiry(604800000L);

            realTokenService = new JwtTokenService(realProperties);

            realFacade = new AuthenticationFacade(
                    providerRegistry,
                    tokenGenerator,
                    realTokenService,
                    tokenBlacklistService,
                    userService,
                    lifecycleService,
                    userRepository,
                    refreshRotationService,
                    cookieUtils,
                    jwtProperties,
                    new LineageRotationFlag(false)
            );
        }

        @Test
        @DisplayName("TW6a: real access token into refreshTokens (flag off) is rejected INVALID_TYPE and not blacklisted")
        void refreshTokens_realAccessTokenFlagOff_rejectedInvalidTypeNotBlacklisted() {
            String accessJwt = realTokenService.generateAccessToken(REAL_USER_ID, UserRole.NORMAL);

            InvalidTokenException exception = assertThrows(
                    InvalidTokenException.class,
                    () -> realFacade.refreshTokens(accessJwt, response)
            );

            assertEquals(InvalidTokenException.Reason.INVALID_TYPE, exception.getReason());
            verify(tokenBlacklistService, never()).blacklistTokenForRotation(any(), any());
            verify(tokenBlacklistService, never()).blacklistToken(any(), any());
        }

        @Test
        @DisplayName("TW6b: real access token into logout REFRESH slot is not blacklisted and family not revoked")
        void logout_realAccessTokenInRefreshSlot_notBlacklistedFamilyNotRevoked() {
            String accessJwt = realTokenService.generateAccessToken(REAL_USER_ID, UserRole.NORMAL);

            realFacade.logout(null, accessJwt);

            verify(tokenBlacklistService, never()).blacklistToken(eq(accessJwt), any());
            verify(refreshRotationService, never()).revokeFamily(any());
        }

        @Test
        @DisplayName("TW6c: real refresh token into logout ACCESS slot is not blacklisted")
        void logout_realRefreshTokenInAccessSlot_notBlacklisted() {
            String refreshJwt = realTokenService.generateRefreshToken(REAL_USER_ID);

            realFacade.logout(refreshJwt, null);

            verify(tokenBlacklistService, never()).blacklistToken(eq(refreshJwt), any());
        }

        @Test
        @DisplayName("TW6d: real refresh token into logoutAll ACCESS slot is not blacklisted (user invalidation still runs)")
        void logoutAll_realRefreshTokenInAccessSlot_notBlacklisted() {
            String refreshJwt = realTokenService.generateRefreshToken(REAL_USER_ID);

            realFacade.logoutAll(REAL_USER_ID, refreshJwt);

            verify(tokenBlacklistService, never()).blacklistToken(eq(refreshJwt), any());
        }
    }
}
