package org.danteplanner.backend.auth.service;

import org.danteplanner.backend.auth.token.LogoutRevocation;
import org.danteplanner.backend.shared.config.LineageRotationFlag;
import org.danteplanner.backend.support.RecordingTokenBlacklist;
import org.danteplanner.backend.support.ScriptedFailuresCheck;
import org.danteplanner.backend.support.TestJwt;
import org.danteplanner.backend.user.api.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(ScriptedFailuresCheck.class)
class SessionRevocationServiceTest {

    private static final Long USER_ID = 123L;

    private final TestJwt jwt = new TestJwt();

    private final RecordingTokenBlacklist tokenBlacklist = new RecordingTokenBlacklist(jwt.clock());

    private SessionRevocationService sessionRevocationService;

    @BeforeEach
    void setUp() {
        sessionRevocationService = new SessionRevocationService(
                jwt.service(), tokenBlacklist, new LineageRotationFlag(false));
    }

    private String accessToken() {
        return jwt.service().generateAccessToken(USER_ID, UserRole.NORMAL);
    }

    private String refreshToken() {
        return jwt.service().generateRefreshToken(USER_ID);
    }

    private Date expiryOf(String token) {
        return jwt.service().validateToken(token).expiration();
    }

    /** logout is void: the revoked-session entry lands in Redis, observable only in a containerized test. */
    @Nested
    class LogoutTests {

        @Test
        void logout_WhenBothTokensValid_BlacklistsBoth() {
            // Arrange
            String accessToken = accessToken();
            String refreshToken = refreshToken();

            // Act
            sessionRevocationService.logout(accessToken, refreshToken);

            // Assert
            assertThat(tokenBlacklist.revocations()).containsExactly(
                    new LogoutRevocation.TokenRevocation(accessToken, expiryOf(accessToken)),
                    new LogoutRevocation.TokenRevocation(refreshToken, expiryOf(refreshToken)));
        }

        @Test
        void logout_WhenNullAccessToken_BlacklistsOnlyRefresh() {
            // Arrange
            String refreshToken = refreshToken();

            // Act
            sessionRevocationService.logout(null, refreshToken);

            // Assert - only refresh token blacklisted
            assertThat(tokenBlacklist.revocations()).containsExactly(
                    new LogoutRevocation.TokenRevocation(refreshToken, expiryOf(refreshToken)));
        }

        @Test
        void logout_WhenNullRefreshToken_BlacklistsOnlyAccess() {
            // Arrange
            String accessToken = accessToken();

            // Act
            sessionRevocationService.logout(accessToken, null);

            // Assert - only access token blacklisted
            assertThat(tokenBlacklist.revocations()).containsExactly(
                    new LogoutRevocation.TokenRevocation(accessToken, expiryOf(accessToken)));
        }

        @Test
        void logout_WhenBothTokensNull_DoesNothing() {
            // Act
            sessionRevocationService.logout(null, null);

            // Assert - nothing present to revoke
            assertThat(tokenBlacklist.revocations()).isEmpty();
            assertThat(tokenBlacklist.size()).isZero();
        }

        @Test
        void logout_WhenInvalidAccessToken_SkipsBlacklist() {
            // Arrange
            String invalidAccessToken = accessToken();
            jwt.clock().advance(Duration.ofMillis(jwt.properties().getAccessTokenExpiry()).plusSeconds(1));
            String validRefreshToken = refreshToken();

            // Act
            sessionRevocationService.logout(invalidAccessToken, validRefreshToken);

            // Assert - only refresh token blacklisted
            assertThat(tokenBlacklist.revocations()).containsExactly(
                    new LogoutRevocation.TokenRevocation(validRefreshToken, expiryOf(validRefreshToken)));
        }

        @Test
        void logout_WhenInvalidRefreshToken_SkipsBlacklist() {
            // Arrange
            String validAccessToken = accessToken();
            String invalidRefreshToken = "invalid-refresh";

            // Act
            sessionRevocationService.logout(validAccessToken, invalidRefreshToken);

            // Assert - only access token blacklisted
            assertThat(tokenBlacklist.revocations()).containsExactly(
                    new LogoutRevocation.TokenRevocation(validAccessToken, expiryOf(validAccessToken)));
        }
    }

    @Nested
    class SessionEndTests {

        @Test
        void endAllSessions_WhenCalled_InvalidatesUserTokens() {
            sessionRevocationService.endAllSessions(USER_ID);

            assertThat(tokenBlacklist.invalidatedUserIds()).containsExactly(USER_ID);
        }

        @Test
        void endAllSessions_WhenTokenStoreUnreachable_Throws() {
            RedisConnectionFailureException unreachable = new RedisConnectionFailureException("token store unreachable");
            tokenBlacklist.failNext(RecordingTokenBlacklist.Operation.INVALIDATE_USER_TOKENS, unreachable);

            assertThatThrownBy(() -> sessionRevocationService.endAllSessions(USER_ID))
                    .isInstanceOf(RedisConnectionFailureException.class)
                    .hasMessage("token store unreachable");
            assertThat(tokenBlacklist.failed()).containsExactly(new RecordingTokenBlacklist.Failed(
                    RecordingTokenBlacklist.Operation.INVALIDATE_USER_TOKENS, unreachable));
        }

        @Test
        void endSession_WhenBothTokensValid_RevokesBoth() {
            String accessToken = accessToken();
            String refreshToken = refreshToken();

            sessionRevocationService.endSession(accessToken, refreshToken);

            assertThat(tokenBlacklist.revocations()).containsExactly(
                    new LogoutRevocation.TokenRevocation(accessToken, expiryOf(accessToken)),
                    new LogoutRevocation.TokenRevocation(refreshToken, expiryOf(refreshToken)));
        }
    }
}
