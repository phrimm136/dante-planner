package org.danteplanner.backend.auth.service;

import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.shared.exception.InvalidRequestException;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.TestJwt;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.auth.oauth.OAuthProvider;
import org.danteplanner.backend.auth.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.auth.oauth.OAuthTokens;
import org.danteplanner.backend.auth.oauth.OAuthUserInfo;
import org.danteplanner.backend.auth.token.TokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for AuthenticationService.
 *
 * <p>Tests OAuth authentication and token refresh flows.</p>
 */
class AuthenticationServiceTest {

    private static final String PROVIDER_NAME = "google";

    private final StubOAuthProvider oauthProvider = new StubOAuthProvider();

    private final TestJwt jwt = new TestJwt();

    private final FakeUsers users = new FakeUsers();

    private AuthenticationService authenticationService;

    private User testUser;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                new OAuthProviderRegistry(List.of(oauthProvider)),
                jwt.service(),
                users
        );

        testUser = users.add(TestDataFactory.unsavedUser(123L));
    }

    private void assertTokensBelongTo(AuthenticationService.AuthResult result, Long userId, UserRole role) {
        TokenClaims access = jwt.service().validateAccessToken(result.accessToken());
        assertEquals(userId, access.userId());
        assertEquals(role, access.role());
        TokenClaims refresh = jwt.service().validateRefreshToken(result.refreshToken());
        assertEquals(userId, refresh.userId());
        assertNull(refresh.parentJti(), "a login opens a new refresh family, it does not rotate one");
    }

    @Nested
    class AuthenticateWithOAuthTests {

        @Test
        void authenticateWithOAuth_WhenExistingActiveUser_ReturnsUserAndTokens() {
            // Arrange
            String providerName = "google";
            String code = "auth-code";
            String redirectUri = "http://localhost/callback";
            String codeVerifier = "verifier";

            OAuthTokens oauthTokens = new OAuthTokens("oauth-access", "oauth-refresh", null);
            oauthProvider.answer(oauthTokens, new OAuthUserInfo("google-123", "test@example.com"));

            // Act
            AuthenticationService.AuthResult result = authenticationService.authenticateWithOAuth(
                    providerName, code, redirectUri, codeVerifier
            );

            // Assert
            assertNotNull(result);
            assertEquals(testUser.getId(), result.user().id());
            assertTokensBelongTo(result, testUser.getId(), UserRole.NORMAL);
            assertFalse(result.reactivated());
            assertThat(oauthProvider.exchanges).containsExactly(List.of(code, redirectUri, codeVerifier));
            assertThat(oauthProvider.userInfoRequests).containsExactly(oauthTokens.accessToken());
        }

        @Test
        void authenticateWithOAuth_WhenProviderNotFound_Throws() {
            // Act & Assert
            assertThatThrownBy(() -> authenticationService.authenticateWithOAuth("unknown", "code", "redirect", "verifier"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("Unknown OAuth provider: unknown");

            assertThat(oauthProvider.exchanges).isEmpty();
        }

        @Test
        void authenticateWithOAuth_WhenUserNotFound_CreatesNewUser() {
            // Arrange
            oauthProvider.answer(new OAuthTokens("access", null, null),
                    new OAuthUserInfo("provider-id-123", "user@email.com"));

            // Act
            AuthenticationService.AuthResult result = authenticationService.authenticateWithOAuth(
                    "google", "code", "redirect", "verifier");

            // Assert
            UserSummary created = users.findByProvider(AuthProviderType.GOOGLE, "provider-id-123").orElseThrow();
            assertFalse(result.reactivated());
            assertEquals(created.id(), result.user().id());
            assertEquals("user@email.com", created.email());
            assertTokensBelongTo(result, created.id(), UserRole.NORMAL);
        }

        @Test
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
            users.add(deletedUser);

            oauthProvider.answer(new OAuthTokens("access", null, null),
                    new OAuthUserInfo("deleted-123", "deleted@example.com"));

            // Act
            AuthenticationService.AuthResult result = authenticationService.authenticateWithOAuth(
                    "google", "code", "redirect", "verifier");

            // Assert
            assertTrue(result.reactivated());
            assertEquals(deletedUser.getId(), result.user().id());
            assertFalse(deletedUser.isDeleted());
            assertTokensBelongTo(result, deletedUser.getId(), UserRole.NORMAL);
        }
    }

    private static final class StubOAuthProvider implements OAuthProvider {

        private final List<List<String>> exchanges = new ArrayList<>();
        private final List<String> userInfoRequests = new ArrayList<>();
        private OAuthTokens tokens;
        private OAuthUserInfo userInfo;

        void answer(OAuthTokens answeredTokens, OAuthUserInfo answeredUserInfo) {
            this.tokens = answeredTokens;
            this.userInfo = answeredUserInfo;
        }

        @Override
        public String getProviderName() {
            return PROVIDER_NAME;
        }

        @Override
        public String buildAuthorizationUrl(String state, String codeChallenge) {
            throw new UnsupportedOperationException("authorization URLs are not part of a code exchange");
        }

        @Override
        public OAuthTokens exchangeCodeForTokens(String code, String redirectUri, String codeVerifier) {
            exchanges.add(List.of(code, redirectUri, codeVerifier));
            return tokens;
        }

        @Override
        public OAuthUserInfo getUserInfo(String accessToken) {
            userInfoRequests.add(accessToken);
            return userInfo;
        }
    }
}
