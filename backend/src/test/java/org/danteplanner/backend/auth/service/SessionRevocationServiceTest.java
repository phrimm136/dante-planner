package org.danteplanner.backend.auth.service;

import org.danteplanner.backend.auth.exception.InvalidTokenException;
import org.danteplanner.backend.auth.token.LogoutRevocation;
import org.danteplanner.backend.auth.token.TokenBlacklistService;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.danteplanner.backend.auth.token.TokenValidator;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.user.api.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionRevocationServiceTest {

    private static final Long USER_ID = 123L;

    @Mock
    private TokenValidator tokenValidator;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private SessionRevocationService sessionRevocationService;

    @BeforeEach
    void setUp() {
        sessionRevocationService = new SessionRevocationService(
                tokenValidator, tokenBlacklistService, new LineageRotationFlag(false));
    }

    /** logout is void: the revoked-session entry lands in Redis, observable only in a containerized test. */
    @Nested
    class LogoutTests {

        @Test
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
            sessionRevocationService.logout(accessToken, refreshToken);

            // Assert
            verify(tokenBlacklistService).revokeLogoutSession(List.of(
                    new LogoutRevocation.TokenRevocation(accessToken, accessExpiry),
                    new LogoutRevocation.TokenRevocation(refreshToken, refreshExpiry)));
        }

        @Test
        void logout_WhenNullAccessToken_BlacklistsOnlyRefresh() {
            // Arrange
            String refreshToken = "refresh-token";
            Date refreshExpiry = new Date(System.currentTimeMillis() + 86400000);
            TokenClaims refreshClaims = new TokenClaims(
                    123L, TokenClaims.TYPE_REFRESH, null, new Date(), refreshExpiry
            );

            when(tokenValidator.validateRefreshToken(refreshToken)).thenReturn(refreshClaims);

            // Act
            sessionRevocationService.logout(null, refreshToken);

            // Assert - only refresh token blacklisted
            verify(tokenBlacklistService).revokeLogoutSession(List.of(
                    new LogoutRevocation.TokenRevocation(refreshToken, refreshExpiry)));
        }

        @Test
        void logout_WhenNullRefreshToken_BlacklistsOnlyAccess() {
            // Arrange
            String accessToken = "access-token";
            Date accessExpiry = new Date(System.currentTimeMillis() + 60000);
            TokenClaims accessClaims = new TokenClaims(
                    123L, TokenClaims.TYPE_ACCESS, UserRole.NORMAL, new Date(), accessExpiry
            );

            when(tokenValidator.validateAccessToken(accessToken)).thenReturn(accessClaims);

            // Act
            sessionRevocationService.logout(accessToken, null);

            // Assert - only access token blacklisted
            verify(tokenBlacklistService).revokeLogoutSession(List.of(
                    new LogoutRevocation.TokenRevocation(accessToken, accessExpiry)));
        }

        @Test
        void logout_WhenBothTokensNull_DoesNothing() {
            // Act
            sessionRevocationService.logout(null, null);

            // Assert - nothing present to revoke
            verify(tokenBlacklistService).revokeLogoutSession(List.of());
            verify(tokenBlacklistService, never()).blacklistToken(any(), any());
            verify(tokenValidator, never()).validateToken(any());
        }

        @Test
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
            sessionRevocationService.logout(invalidAccessToken, validRefreshToken);

            // Assert - only refresh token blacklisted
            verify(tokenBlacklistService).revokeLogoutSession(List.of(
                    new LogoutRevocation.TokenRevocation(validRefreshToken, refreshExpiry)));
        }

        @Test
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
            sessionRevocationService.logout(validAccessToken, invalidRefreshToken);

            // Assert - only access token blacklisted
            verify(tokenBlacklistService).revokeLogoutSession(List.of(
                    new LogoutRevocation.TokenRevocation(validAccessToken, accessExpiry)));
        }
    }

    @Nested
    class SessionEndTests {

        @Test
        void endAllSessions_WhenCalled_InvalidatesUserTokens() {
            sessionRevocationService.endAllSessions(USER_ID);

            verify(tokenBlacklistService).invalidateUserTokens(USER_ID);
        }

        @Test
        void endAllSessions_WhenTokenStoreUnreachable_Throws() {
            doThrow(new RedisConnectionFailureException("token store unreachable"))
                    .when(tokenBlacklistService).invalidateUserTokens(USER_ID);

            assertThrows(RedisConnectionFailureException.class,
                    () -> sessionRevocationService.endAllSessions(USER_ID));
        }

        @Test
        void endSession_WhenBothTokensValid_RevokesBoth() {
            Date accessExpiry = new Date(System.currentTimeMillis() + 60000);
            Date refreshExpiry = new Date(System.currentTimeMillis() + 86400000);
            when(tokenValidator.validateAccessToken("access-token")).thenReturn(new TokenClaims(
                    USER_ID, TokenClaims.TYPE_ACCESS, UserRole.NORMAL, new Date(), accessExpiry));
            when(tokenValidator.validateRefreshToken("refresh-token")).thenReturn(new TokenClaims(
                    USER_ID, TokenClaims.TYPE_REFRESH, null, new Date(), refreshExpiry));

            sessionRevocationService.endSession("access-token", "refresh-token");

            verify(tokenBlacklistService).revokeLogoutSession(List.of(
                    new LogoutRevocation.TokenRevocation("access-token", accessExpiry),
                    new LogoutRevocation.TokenRevocation("refresh-token", refreshExpiry)));
        }
    }
}
