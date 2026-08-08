package org.danteplanner.backend.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.auth.exception.OAuthException;
import org.danteplanner.backend.shared.config.OAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for how {@link GoogleOAuthProvider} reads a verified {@code id_token}.
 *
 * <p>A valid signature says nothing about which optional claims the consent screen granted,
 * so the claims the caller requires are asserted separately from verification. Both feed a
 * null-rejecting map downstream, where absence would surface as an unhandled failure rather
 * than an OAuth error.</p>
 */
class GoogleOAuthProviderTest {

    private static final String SUBJECT = "google-subject-1";
    private static final String EMAIL = "person@example.com";

    private GoogleIdTokenVerifier idTokenVerifier;
    private GoogleOAuthProvider provider;

    @BeforeEach
    void setUp() {
        idTokenVerifier = mock(GoogleIdTokenVerifier.class);
        provider = new GoogleOAuthProvider(
                mock(RestTemplate.class), new ObjectMapper(), new OAuthProperties(), idTokenVerifier);
    }

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now().minusSeconds(30))
                .expiresAt(Instant.now().plusSeconds(600));
    }

    private static OAuthTokens tokensWithIdToken() {
        return new OAuthTokens("access-token", null, "id-token");
    }

    @Test
    @DisplayName("A verified token carrying both claims yields the user info")
    void getUserInfo_WhenClaimsPresent_ReturnsUserInfo() {
        when(idTokenVerifier.verify(anyString()))
                .thenReturn(jwt().subject(SUBJECT).claim("email", EMAIL).build());

        OAuthUserInfo info = provider.getUserInfo(tokensWithIdToken());

        assertThat(info.providerId()).isEqualTo(SUBJECT);
        assertThat(info.email()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("A verified token without an email claim is rejected, not passed on as null")
    void getUserInfo_WhenEmailClaimAbsent_ThrowsOAuthException() {
        when(idTokenVerifier.verify(anyString()))
                .thenReturn(jwt().subject(SUBJECT).build());

        assertThatThrownBy(() -> provider.getUserInfo(tokensWithIdToken()))
                .isInstanceOf(OAuthException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("A verified token without a subject is rejected")
    void getUserInfo_WhenSubjectAbsent_ThrowsOAuthException() {
        when(idTokenVerifier.verify(anyString()))
                .thenReturn(jwt().claim("email", EMAIL).claim("unused", "x").build());

        assertThatThrownBy(() -> provider.getUserInfo(tokensWithIdToken()))
                .isInstanceOf(OAuthException.class)
                .hasMessageContaining("sub");
    }

    @Test
    @DisplayName("A blank email claim is rejected the same as an absent one")
    void getUserInfo_WhenEmailClaimBlank_ThrowsOAuthException() {
        when(idTokenVerifier.verify(anyString()))
                .thenReturn(jwt().subject(SUBJECT).claim("email", "   ").build());

        assertThatThrownBy(() -> provider.getUserInfo(tokensWithIdToken()))
                .isInstanceOf(OAuthException.class)
                .hasMessageContaining("email");
    }
}
