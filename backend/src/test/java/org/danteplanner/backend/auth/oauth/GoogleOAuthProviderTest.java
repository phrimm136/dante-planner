package org.danteplanner.backend.auth.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.danteplanner.backend.auth.exception.OAuthException;
import org.danteplanner.backend.shared.config.OAuthProperties;
import org.danteplanner.backend.support.GoogleStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private final ScriptedIdTokenVerifier idTokenVerifier = new ScriptedIdTokenVerifier();
    private GoogleOAuthProvider provider;

    @BeforeEach
    void setUp() {
        provider = new GoogleOAuthProvider(null, new ObjectMapper(), new OAuthProperties(), idTokenVerifier);
    }

    private static final class ScriptedIdTokenVerifier extends GoogleIdTokenVerifier {

        private Jwt verified;

        private ScriptedIdTokenVerifier() {
            super(googleProperties());
        }

        private static OAuthProperties googleProperties() {
            OAuthProperties properties = new OAuthProperties();
            properties.getGoogle().setClientId(GoogleStub.CLIENT_ID);
            properties.getGoogle().setJwksUri("https://www.googleapis.com/oauth2/v3/certs");
            properties.getGoogle().setIssuer("https://accounts.google.com");
            return properties;
        }

        private void verifies(Jwt jwt) {
            verified = jwt;
        }

        @Override
        public Jwt verify(String idToken) {
            return verified;
        }
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
    void getUserInfo_WhenClaimsPresent_ReturnsUserInfo() {
        idTokenVerifier.verifies(jwt().subject(SUBJECT).claim("email", EMAIL).build());

        OAuthUserInfo info = provider.getUserInfo(tokensWithIdToken());

        assertThat(info.providerId()).isEqualTo(SUBJECT);
        assertThat(info.email()).isEqualTo(EMAIL);
    }

    @Test
    void getUserInfo_WhenEmailClaimAbsent_ThrowsOAuthException() {
        idTokenVerifier.verifies(jwt().subject(SUBJECT).build());

        assertThatThrownBy(() -> provider.getUserInfo(tokensWithIdToken()))
                .isInstanceOf(OAuthException.class)
                .hasMessageContaining("email");
    }

    @Test
    void getUserInfo_WhenSubjectAbsent_ThrowsOAuthException() {
        idTokenVerifier.verifies(jwt().claim("email", EMAIL).claim("unused", "x").build());

        assertThatThrownBy(() -> provider.getUserInfo(tokensWithIdToken()))
                .isInstanceOf(OAuthException.class)
                .hasMessageContaining("sub");
    }

    @Test
    void getUserInfo_WhenEmailClaimBlank_ThrowsOAuthException() {
        idTokenVerifier.verifies(jwt().subject(SUBJECT).claim("email", "   ").build());

        assertThatThrownBy(() -> provider.getUserInfo(tokensWithIdToken()))
                .isInstanceOf(OAuthException.class)
                .hasMessageContaining("email");
    }
}
