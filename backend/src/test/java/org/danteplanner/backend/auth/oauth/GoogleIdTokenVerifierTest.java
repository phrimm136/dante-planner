package org.danteplanner.backend.auth.oauth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.danteplanner.backend.auth.exception.OAuthException;
import org.danteplanner.backend.shared.config.OAuthProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link GoogleIdTokenVerifier}.
 *
 * <p>Serves a JWK set from a loopback HTTP server so the real fetch-and-select path runs,
 * then presents tokens that differ from a genuine one in exactly one respect each: the
 * signing key, the audience, the issuer, or expiry. The signing-key case is the one that
 * matters — before verification existed, a token minted by anyone was accepted.</p>
 */
class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "client-under-test.apps.googleusercontent.com";
    private static final String ISSUER = "https://accounts.google.com";
    private static final String SUBJECT = "google-subject-1";
    private static final String EMAIL = "person@example.com";

    private static HttpServer jwkServer;
    private static RSAKey signingKey;
    private static RSAKey foreignKey;
    private static GoogleIdTokenVerifier verifier;

    @BeforeAll
    static void startJwkServer() throws Exception {
        signingKey = generateKey();
        foreignKey = generateKey();

        String jwks = new JWKSet(signingKey.toPublicJWK()).toString();
        jwkServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwkServer.createContext("/certs", exchange -> {
            byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        jwkServer.start();

        OAuthProperties properties = new OAuthProperties();
        OAuthProperties.GoogleConfig google = properties.getGoogle();
        google.setClientId(CLIENT_ID);
        google.setIssuer(ISSUER);
        google.setJwksUri("http://127.0.0.1:" + jwkServer.getAddress().getPort() + "/certs");
        verifier = new GoogleIdTokenVerifier(properties);
    }

    @AfterAll
    static void stopJwkServer() {
        jwkServer.stop(0);
    }

    private static RSAKey generateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
    }

    private static String mint(RSAKey key, String issuer, String audience, Instant expiry)
            throws Exception {
        return mint(key, issuer, audience, expiry, EMAIL);
    }

    static String mint(RSAKey key, String issuer, String audience, Instant expiry, String email)
            throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(SUBJECT)
                .audience(List.of(audience))
                .claim("email", email)
                .issueTime(Date.from(Instant.now().minusSeconds(30)))
                .expirationTime(Date.from(expiry))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key.toRSAPrivateKey()));
        return jwt.serialize();
    }

    private static String genuineToken() throws Exception {
        return mint(signingKey, ISSUER, CLIENT_ID, Instant.now().plusSeconds(600));
    }

    @Test
    @DisplayName("A token signed by the published key is accepted and yields its claims")
    void verify_WhenGenuineToken_ReturnsClaims() throws Exception {
        var verified = verifier.verify(genuineToken());

        assertThat(verified.getSubject()).isEqualTo(SUBJECT);
        assertThat(verified.getClaimAsString("email")).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("A token signed by a key the issuer never published is rejected")
    void verify_WhenForeignSigningKey_Rejected() throws Exception {
        String forged = mint(foreignKey, ISSUER, CLIENT_ID, Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> verifier.verify(forged))
                .isInstanceOf(OAuthException.class);
    }

    @Test
    @DisplayName("A token minted for another client is rejected")
    void verify_WhenAudienceIsAnotherClient_Rejected() throws Exception {
        String otherAudience =
                mint(signingKey, ISSUER, "someone-else.apps.googleusercontent.com",
                        Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> verifier.verify(otherAudience))
                .isInstanceOf(OAuthException.class);
    }

    @Test
    @DisplayName("A token from an unexpected issuer is rejected")
    void verify_WhenIssuerUnexpected_Rejected() throws Exception {
        String wrongIssuer =
                mint(signingKey, "https://accounts.evil.example", CLIENT_ID,
                        Instant.now().plusSeconds(600));

        assertThatThrownBy(() -> verifier.verify(wrongIssuer))
                .isInstanceOf(OAuthException.class);
    }

    @Test
    @DisplayName("An expired token is rejected")
    void verify_WhenExpired_Rejected() throws Exception {
        String expired = mint(signingKey, ISSUER, CLIENT_ID, Instant.now().minusSeconds(600));

        assertThatThrownBy(() -> verifier.verify(expired))
                .isInstanceOf(OAuthException.class);
    }

    @Test
    @DisplayName("A structurally valid but unsigned payload is rejected")
    void verify_WhenUnsignedPayload_Rejected() {
        String unsigned = "eyJhbGciOiJub25lIn0"
                + ".eyJzdWIiOiJhdHRhY2tlciIsImVtYWlsIjoiYUBiLmMifQ.";

        assertThatThrownBy(() -> verifier.verify(unsigned))
                .isInstanceOf(OAuthException.class);
    }
}
