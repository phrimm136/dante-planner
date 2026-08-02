package org.danteplanner.backend.auth.oauth;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.auth.exception.OAuthException;
import org.danteplanner.backend.shared.config.OAuthProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Verifies Google-issued {@code id_token}s before any claim inside one is trusted.
 *
 * <p>The token arrives over the provider's token endpoint, which is deployment
 * configuration, so the transport proves nothing about who minted the token. The
 * signature is the only evidence, checked against Google's published keys.</p>
 */
@Component
@Slf4j
public class GoogleIdTokenVerifier {

    private static final String PROVIDER_NAME = "google";

    private final NimbusJwtDecoder decoder;

    public GoogleIdTokenVerifier(OAuthProperties oAuthProperties) {
        OAuthProperties.GoogleConfig google = oAuthProperties.getGoogle();
        this.decoder = NimbusJwtDecoder.withJwkSetUri(google.getJwksUri()).build();

        OAuth2TokenValidator<Jwt> audienceMatchesClient = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                audience -> audience != null && audience.contains(google.getClientId()));

        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(google.getIssuer()),
                audienceMatchesClient));
    }

    /**
     * Verify an {@code id_token} and return its claims.
     *
     * <p>Rejects on a bad signature, an unexpected issuer, an audience that is not this
     * client, or expiry. A token that fails is never downgraded to another lookup path:
     * a genuine Google flow always yields a verifiable token, so a failure means either
     * an attack or a misconfiguration and both should be loud.</p>
     *
     * @param idToken the raw compact JWT
     * @return the verified claims
     * @throws OAuthException if the token cannot be verified
     */
    public Jwt verify(String idToken) {
        try {
            return decoder.decode(idToken);
        } catch (JwtException e) {
            log.warn("Rejected an unverifiable Google id_token: {}", e.getMessage());
            throw new OAuthException(PROVIDER_NAME, "id_token", "id_token failed verification", e);
        }
    }
}
