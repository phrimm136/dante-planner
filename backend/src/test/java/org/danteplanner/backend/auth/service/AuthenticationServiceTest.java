package org.danteplanner.backend.auth.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.support.LogCapture;
import org.danteplanner.backend.shared.exception.InvalidRequestException;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.support.TestJwt;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.auth.token.AccessClaims;
import org.danteplanner.backend.auth.token.RefreshClaims;
import org.danteplanner.backend.auth.oauth.OAuthProvider;
import org.danteplanner.backend.auth.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.auth.oauth.OAuthTokens;
import org.danteplanner.backend.auth.oauth.OAuthUserInfo;
import org.danteplanner.backend.auth.token.RefreshLineage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        AccessClaims access = jwt.service().validateAccessToken(result.accessToken());
        assertThat(access.userId()).isEqualTo(userId);
        assertThat(access.role()).isEqualTo(role);
        RefreshClaims refresh = jwt.service().validateRefreshToken(result.refreshToken());
        assertThat(refresh.userId()).isEqualTo(userId);
        assertThat(refresh.lineage()).as("a login opens a new refresh family, it does not rotate one")
                .isInstanceOf(RefreshLineage.Root.class);
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

            OAuthTokens.Response oauthTokens = new OAuthTokens.Response("oauth-access", null);
            oauthProvider.answer(oauthTokens, new OAuthUserInfo("google-123", "test@example.com"));

            // Act
            AuthenticationService.AuthResult result = authenticationService.authenticateWithOAuth(
                    providerName, code, redirectUri, codeVerifier
            );

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.user().id()).isEqualTo(testUser.getId());
            assertTokensBelongTo(result, testUser.getId(), UserRole.NORMAL);
            assertThat(result.reactivated()).isFalse();
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
            oauthProvider.answer(new OAuthTokens.Response("access", null),
                    new OAuthUserInfo("provider-id-123", "user@email.com"));

            // Act
            AuthenticationService.AuthResult result = authenticationService.authenticateWithOAuth(
                    "google", "code", "redirect", "verifier");

            // Assert
            UserSummary created = users.findByProvider(AuthProviderType.GOOGLE, "provider-id-123").orElseThrow();
            assertThat(result.reactivated()).isFalse();
            assertThat(result.user().id()).isEqualTo(created.id());
            assertThat(created.email()).isEqualTo("user@email.com");
            assertTokensBelongTo(result, created.id(), UserRole.NORMAL);
        }

        @Test
        void authenticateWithOAuth_WhenUserSoftDeleted_ReactivatesUser() {
            // Arrange
            User deletedUser = TestDataFactory.user()
                    .id(456L)
                    .provider(AuthProviderType.GOOGLE)
                    .providerId("deleted-123")
                    .build();
            deletedUser.softDelete(Instant.now(), Instant.now().plusSeconds(86400 * 30));
            users.add(deletedUser);

            oauthProvider.answer(new OAuthTokens.Response("access", null),
                    new OAuthUserInfo("deleted-123", "deleted@example.com"));

            // Act
            AuthenticationService.AuthResult result = authenticationService.authenticateWithOAuth(
                    "google", "code", "redirect", "verifier");

            // Assert
            assertThat(result.reactivated()).isTrue();
            assertThat(result.user().id()).isEqualTo(deletedUser.getId());
            assertThat(deletedUser.isDeleted()).isFalse();
            assertTokensBelongTo(result, deletedUser.getId(), UserRole.NORMAL);
        }

        @Test
        void authenticateWithOAuth_WhenTheAccountBecameActiveBetweenTheLookups_SignsInWithoutWriting() {
            CallOrder calls = new CallOrder(false);
            calls.add(activeUser());
            oauthProvider.answer(new OAuthTokens.Response("access", null),
                    new OAuthUserInfo("raced-123", "raced@example.com"));

            AuthenticationService.AuthResult result = authenticateOver(calls);

            assertThat(result.reactivated()).isFalse();
            assertThat(result.user().id()).isEqualTo(789L);
            assertThat(calls.made).containsExactly("findActiveByProvider", "findByProvider");
        }

        @Test
        void authenticateWithOAuth_WhenTheAccountIsActive_AsksForNothingElse() {
            CallOrder calls = new CallOrder(true);
            calls.add(activeUser());
            oauthProvider.answer(new OAuthTokens.Response("access", null),
                    new OAuthUserInfo("raced-123", "raced@example.com"));

            assertThat(authenticateOver(calls).reactivated()).isFalse();

            assertThat(calls.made).containsExactly("findActiveByProvider");
        }

        @Test
        void authenticateWithOAuth_WhenThereIsNoAccount_LooksTwiceBeforeCreating() {
            CallOrder calls = new CallOrder(true);
            oauthProvider.answer(new OAuthTokens.Response("access", null),
                    new OAuthUserInfo("new-123", "new@example.com"));

            authenticateOver(calls);

            assertThat(calls.made).containsExactly("findActiveByProvider", "findByProvider", "findOrCreateUser");
        }

        @Test
        void authenticateWithOAuth_WhenTheAccountIsSoftDeleted_ReactivatesAfterBothLookupsAndLogsIt() {
            CallOrder calls = new CallOrder(true);
            User deleted = activeUser();
            deleted.softDelete(Instant.now(), Instant.now().plusSeconds(86400 * 30));
            calls.add(deleted);
            oauthProvider.answer(new OAuthTokens.Response("access", null),
                    new OAuthUserInfo("raced-123", "raced@example.com"));

            List<String> logged = LogCapture.during(() -> authenticateOver(calls), Level.INFO,
                            AuthenticationService.class).stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            assertThat(calls.made).containsExactly("findActiveByProvider", "findByProvider", "reactivateAccount");
            assertThat(logged).containsExactly(
                    "Processing OAuth authentication for provider: google",
                    "Reactivated soft-deleted account for user: 789",
                    "User authenticated successfully via google: userId=789 (reactivated: true)");
        }

        private User activeUser() {
            return TestDataFactory.user()
                    .id(789L)
                    .provider(AuthProviderType.GOOGLE)
                    .providerId("raced-123")
                    .build();
        }

        private AuthenticationService.AuthResult authenticateOver(FakeUsers accounts) {
            return new AuthenticationService(new OAuthProviderRegistry(List.of(oauthProvider)), jwt.service(), accounts)
                    .authenticateWithOAuth("google", "code", "redirect", "verifier");
        }
    }

    private static final class CallOrder extends FakeUsers {

        private final List<String> made = new ArrayList<>();
        private final boolean seesActiveAccounts;

        private CallOrder(boolean seesActiveAccounts) {
            this.seesActiveAccounts = seesActiveAccounts;
        }

        @Override
        public Optional<UserSummary> findActiveByProvider(AuthProviderType providerType, String providerId) {
            made.add("findActiveByProvider");
            return seesActiveAccounts ? super.findActiveByProvider(providerType, providerId) : Optional.empty();
        }

        @Override
        public Optional<UserSummary> findByProvider(AuthProviderType providerType, String providerId) {
            made.add("findByProvider");
            return super.findByProvider(providerType, providerId);
        }

        @Override
        public UserSummary findOrCreateUser(String provider, String providerId, String email) {
            made.add("findOrCreateUser");
            return super.findOrCreateUser(provider, providerId, email);
        }

        @Override
        public void reactivateAccount(Long userId) {
            made.add("reactivateAccount");
            super.reactivateAccount(userId);
        }
    }

    private static final class StubOAuthProvider implements OAuthProvider {

        private final List<List<String>> exchanges = new ArrayList<>();
        private final List<String> userInfoRequests = new ArrayList<>();
        private OAuthTokens.Response tokens;
        private OAuthUserInfo userInfo;

        void answer(OAuthTokens.Response answeredTokens, OAuthUserInfo answeredUserInfo) {
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
        public OAuthTokens.Response exchangeCodeForTokens(String code, String redirectUri, String codeVerifier) {
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
