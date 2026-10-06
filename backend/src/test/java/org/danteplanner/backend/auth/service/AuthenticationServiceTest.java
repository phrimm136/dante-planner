package org.danteplanner.backend.auth.service;

import org.danteplanner.backend.user.api.AuthProviderType;
import org.danteplanner.backend.shared.exception.InvalidRequestException;
import org.danteplanner.backend.support.FakeUsers;
import org.danteplanner.backend.support.TestDataFactory;
import org.danteplanner.backend.user.entity.User;
import org.danteplanner.backend.user.api.UserRole;
import org.danteplanner.backend.user.api.UserSummary;
import org.danteplanner.backend.auth.oauth.OAuthProvider;
import org.danteplanner.backend.auth.oauth.OAuthProviderRegistry;
import org.danteplanner.backend.auth.oauth.OAuthTokens;
import org.danteplanner.backend.auth.oauth.OAuthUserInfo;
import org.danteplanner.backend.auth.token.TokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for AuthenticationService.
 *
 * <p>Tests OAuth authentication and token refresh flows
 * with all dependencies mocked.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private OAuthProvider oauthProvider;

    @Mock
    private jakarta.servlet.http.HttpServletResponse response;

    private final FakeUsers users = new FakeUsers();

    private AuthenticationService authenticationService;

    private User testUser;

    @BeforeEach
    void setUp() {
        when(oauthProvider.getProviderName()).thenReturn("google");
        authenticationService = new AuthenticationService(
                new OAuthProviderRegistry(List.of(oauthProvider)),
                tokenGenerator,
                users
        );

        testUser = users.add(TestDataFactory.unsavedUser(123L));
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
            OAuthUserInfo userInfo = new OAuthUserInfo("google-123", "test@example.com");

            when(oauthProvider.exchangeCodeForTokens(code, redirectUri, codeVerifier)).thenReturn(oauthTokens);
            when(oauthProvider.getUserInfo(oauthTokens)).thenReturn(userInfo);
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
        void authenticateWithOAuth_WhenProviderNotFound_Throws() {
            // Act & Assert
            assertThatThrownBy(() -> authenticationService.authenticateWithOAuth("unknown", "code", "redirect", "verifier"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage("Unknown OAuth provider: unknown");

            // Verify no tokens generated
            verify(tokenGenerator, never()).generateAccessToken(any(), any());
            verify(tokenGenerator, never()).generateRefreshToken(any());
        }

        @Test
        void authenticateWithOAuth_WhenUserNotFound_CreatesNewUser() {
            // Arrange
            OAuthTokens oauthTokens = new OAuthTokens("access", null, null);
            OAuthUserInfo userInfo = new OAuthUserInfo("provider-id-123", "user@email.com");

            when(oauthProvider.exchangeCodeForTokens(any(), any(), any())).thenReturn(oauthTokens);
            when(oauthProvider.getUserInfo(any(OAuthTokens.class))).thenReturn(userInfo);
            when(tokenGenerator.generateAccessToken(any(), eq(UserRole.NORMAL))).thenReturn("access");
            when(tokenGenerator.generateRefreshToken(any())).thenReturn("refresh");

            // Act
            AuthenticationService.AuthResult result = authenticationService.authenticateWithOAuth(
                    "google", "code", "redirect", "verifier");

            // Assert
            UserSummary created = users.findByProvider(AuthProviderType.GOOGLE, "provider-id-123").orElseThrow();
            assertFalse(result.reactivated());
            assertEquals(created.id(), result.user().id());
            assertEquals("user@email.com", created.email());
            verify(tokenGenerator).generateAccessToken(created.id(), UserRole.NORMAL);
            verify(tokenGenerator).generateRefreshToken(created.id());
            assertEquals("access", result.accessToken());
            assertEquals("refresh", result.refreshToken());
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

            OAuthTokens oauthTokens = new OAuthTokens("access", null, null);
            OAuthUserInfo userInfo = new OAuthUserInfo("deleted-123", "deleted@example.com");

            when(oauthProvider.exchangeCodeForTokens(any(), any(), any())).thenReturn(oauthTokens);
            when(oauthProvider.getUserInfo(any(OAuthTokens.class))).thenReturn(userInfo);
            when(tokenGenerator.generateAccessToken(any(), any())).thenReturn("access");
            when(tokenGenerator.generateRefreshToken(any())).thenReturn("refresh");

            // Act
            AuthenticationService.AuthResult result = authenticationService.authenticateWithOAuth(
                    "google", "code", "redirect", "verifier");

            // Assert
            assertTrue(result.reactivated());
            assertEquals(deletedUser.getId(), result.user().id());
            assertFalse(deletedUser.isDeleted());
        }
    }


    /**
     * TW6 — type enforcement at the facade seams, exercised with a REAL {@link JwtTokenService}
     * so minted tokens carry a real {@code type} claim and the typed parser actually enforces it.
     * A {@code @Mock TokenValidator} returns canned claims and can never exercise type enforcement,
     * so these cases wire their own facade with a real validator, leaving the mocked cases intact.
     */
}
