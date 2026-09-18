package org.danteplanner.backend.auth.oauth;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.auth.entity.AuthProviderType;
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
 */
@Component
@Slf4j
public class GoogleIdTokenVerifier {

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
     * <p>Rejects on a bad signature, an unexpected issuer, an audience that is not this
     * client, or expiry.</p>
     */
    public Jwt verify(String idToken) {
        try {
            return decoder.decode(idToken);
        } catch (JwtException e) {
            log.warn("Rejected an unverifiable Google id_token: {}", e.getMessage());
            throw new OAuthException(AuthProviderType.GOOGLE.getValue(), "id_token", "id_token failed verification", e);
        }
    }
}
