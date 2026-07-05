package org.danteplanner.backend.controller;

import jakarta.servlet.http.Cookie;
import org.danteplanner.backend.config.TestConfig;
import org.danteplanner.backend.auth.facade.AuthenticationFacade;
import org.danteplanner.backend.auth.oauth.OAuthStateService;
import org.danteplanner.backend.shared.util.CookieConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adversarial tests: an attacker who has <b>intercepted the authorization code</b> (the
 * application-layer consequence a transport MITM would aim for — transport itself is closed by
 * TLS, not by code) cannot complete login against the BFF flow.
 *
 * <p>The defense is structural: the PKCE {@code code_verifier} never leaves the server — it lives
 * only inside the signed-and-encrypted {@code oauth_tx} cookie. So a stolen {@code code} is inert
 * without the matching server-held verifier, and the verifier cannot be forged (RS256 signature) or
 * substituted (state binding, INV2). In every case the token exchange is never even attempted.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestConfig.class, OAuthMitmResistanceTest.MockAuthFacadeConfig.class})
@Transactional
class OAuthMitmResistanceTest {

    @TestConfiguration
    static class MockAuthFacadeConfig {
        @Bean
        @Primary
        public AuthenticationFacade authenticationFacade() {
            return Mockito.mock(AuthenticationFacade.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuthStateService oAuthStateService;

    @Autowired
    private AuthenticationFacade authFacade;

    @BeforeEach
    void setUp() {
        Mockito.reset(authFacade);
    }

    @Test
    @DisplayName("Intercepted code with no server-side verifier cannot redeem (PKCE/BFF core defense)")
    void interceptedCode_withoutOauthTx_cannotRedeem() throws Exception {
        // Attacker has a valid-looking code + state from interception, but no oauth_tx —
        // the code_verifier never left the server, so the exchange is never attempted.
        mockMvc.perform(get("/api/auth/google/callback")
                        .param("code", "intercepted-authorization-code")
                        .param("state", "intercepted-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("login=error")))
                .andExpect(cookie().doesNotExist(CookieConstants.ACCESS_TOKEN))
                .andExpect(cookie().doesNotExist(CookieConstants.REFRESH_TOKEN));

        verify(authFacade, never()).authenticateWithOAuth(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Intercepted code paired with a forged oauth_tx is rejected (cannot supply own verifier)")
    void interceptedCode_withForgedOauthTx_isRejected() throws Exception {
        // Attacker forges an oauth_tx to inject a chosen verifier alongside the stolen code.
        // The cookie is not a validly-signed token, so open() fails closed.
        String forgedOauthTx = "attacker.forged.oauth-tx-token";

        mockMvc.perform(get("/api/auth/google/callback")
                        .param("code", "intercepted-authorization-code")
                        .param("state", "intercepted-state")
                        .cookie(new Cookie(CookieConstants.OAUTH_TX, forgedOauthTx)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("login=error")))
                .andExpect(cookie().doesNotExist(CookieConstants.ACCESS_TOKEN));

        verify(authFacade, never()).authenticateWithOAuth(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Tampered (bit-flipped) oauth_tx from a real session is rejected")
    void interceptedCode_withTamperedOauthTx_isRejected() throws Exception {
        // Attacker captures a genuine oauth_tx and mutates it to alter the sealed verifier/state.
        String genuine = oAuthStateService.seal("victim-state", "victim-verifier", "http://localhost");
        int mid = genuine.length() / 2;
        char swapped = genuine.charAt(mid) == 'a' ? 'b' : 'a';
        String tampered = genuine.substring(0, mid) + swapped + genuine.substring(mid + 1);

        mockMvc.perform(get("/api/auth/google/callback")
                        .param("code", "intercepted-authorization-code")
                        .param("state", "victim-state")
                        .cookie(new Cookie(CookieConstants.OAUTH_TX, tampered)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("login=error")))
                .andExpect(cookie().doesNotExist(CookieConstants.ACCESS_TOKEN));

        verify(authFacade, never()).authenticateWithOAuth(any(), any(), any(), any());
    }
}
