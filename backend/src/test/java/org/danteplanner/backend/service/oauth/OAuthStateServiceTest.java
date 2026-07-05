package org.danteplanner.backend.service.oauth;
import org.danteplanner.backend.auth.oauth.OAuthStateService;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.auth.oauth.OAuthStateService.OAuthTransaction;
import org.danteplanner.backend.auth.token.AesGcmCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OAuthStateService}.
 *
 * <p>Verifies the seal/open round-trip and, critically, that {@link OAuthStateService#open}
 * fails closed on every tamper, wrong-signer, expiry, and absence path — the guarantee the
 * login-fixation defense (INV2) and verifier confidentiality (INV5) rest on.</p>
 */
class OAuthStateServiceTest {

    private static final String TX_CLAIM = "tx";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private OAuthStateService stateService;
    private AesGcmCipher aesGcmCipher;
    private ObjectMapper objectMapper;
    private byte[] aesKey;
    private PrivateKey signingKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        signingKey = keyPair.getPrivate();

        aesKey = new byte[32];
        new SecureRandom().nextBytes(aesKey);

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setPrivateKey(keyPair.getPrivate());
        jwtProperties.setPublicKey(keyPair.getPublic());
        jwtProperties.setEncryptionKeyBytes(aesKey);

        aesGcmCipher = new AesGcmCipher();
        objectMapper = new ObjectMapper();
        stateService = new OAuthStateService(jwtProperties, objectMapper, aesGcmCipher);
    }

    @Test
    @DisplayName("seal then open returns the same state and code verifier")
    void open_whenSealedByService_returnsSameTransaction() {
        String state = "state-abc";
        String verifier = "verifier-xyz";
        String returnTo = "http://localhost:5173/planner/1";

        Optional<OAuthTransaction> result = stateService.open(stateService.seal(state, verifier, returnTo));

        assertThat(result).isPresent();
        assertThat(result.get().state()).isEqualTo(state);
        assertThat(result.get().codeVerifier()).isEqualTo(verifier);
        assertThat(result.get().returnTo()).isEqualTo(returnTo);
    }

    @Test
    @DisplayName("open returns empty when the token is null")
    void open_whenNull_returnsEmpty() {
        assertThat(stateService.open(null)).isEmpty();
    }

    @Test
    @DisplayName("open returns empty when the token is blank")
    void open_whenBlank_returnsEmpty() {
        assertThat(stateService.open("   ")).isEmpty();
    }

    @Test
    @DisplayName("open returns empty when the signature is tampered")
    void open_whenTampered_returnsEmpty() {
        String sealed = stateService.seal("state", "verifier", "http://localhost");
        // Corrupt a character mid-payload so the signed content changes (flipping the final
        // signature char only touches unused base64 bits and can still verify).
        int mid = sealed.length() / 2;
        char current = sealed.charAt(mid);
        char swapped = current == 'a' ? 'b' : 'a';
        String tampered = sealed.substring(0, mid) + swapped + sealed.substring(mid + 1);

        assertThat(stateService.open(tampered)).isEmpty();
    }

    @Test
    @DisplayName("open returns empty when the token is signed by a different key")
    void open_whenSignedByForeignKey_returnsEmpty() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        PrivateKey foreignKey = generator.generateKeyPair().getPrivate();

        String foreignToken = Jwts.builder()
                .claim(TX_CLAIM, encryptTx("state", "verifier"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 90_000L))
                .signWith(foreignKey, SignatureAlgorithm.RS256)
                .compact();

        assertThat(stateService.open(foreignToken)).isEmpty();
    }

    @Test
    @DisplayName("open returns empty when the token is expired")
    void open_whenExpired_returnsEmpty() throws Exception {
        Date past = new Date(System.currentTimeMillis() - 1_000L);
        String expiredToken = Jwts.builder()
                .claim(TX_CLAIM, encryptTx("state", "verifier"))
                .issuedAt(new Date(System.currentTimeMillis() - 120_000L))
                .expiration(past)
                .signWith(signingKey, SignatureAlgorithm.RS256)
                .compact();

        assertThat(stateService.open(expiredToken)).isEmpty();
    }

    @Test
    @DisplayName("generateCodeChallenge is the S256 hash of the verifier")
    void generateCodeChallenge_WhenGivenVerifier_ReturnsS256Hash() throws Exception {
        String verifier = stateService.generateCodeVerifier();

        byte[] expected = java.security.MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        assertThat(stateService.generateCodeChallenge(verifier))
                .isEqualTo(URL_ENCODER.encodeToString(expected));
    }

    /**
     * Builds a {@code tx} claim with the service's exact encoding so hand-crafted
     * (foreign-signed, expired) tokens are otherwise well-formed.
     */
    private String encryptTx(String state, String verifier) throws Exception {
        byte[] json = objectMapper.writeValueAsBytes(new OAuthTransaction(state, verifier, "http://localhost"));
        return URL_ENCODER.encodeToString(aesGcmCipher.encrypt(aesKey, json));
    }
}
