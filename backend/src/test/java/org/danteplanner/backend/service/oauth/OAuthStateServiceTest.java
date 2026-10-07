package org.danteplanner.backend.service.oauth;
import org.danteplanner.backend.auth.oauth.OAuthStateService;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.auth.oauth.OAuthStateService.OAuthTransaction;
import org.danteplanner.backend.auth.token.AesGcmCipher;
import org.danteplanner.backend.support.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
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
 * login-fixation defense and verifier confidentiality rest on.</p>
 */
class OAuthStateServiceTest {

    private static final String TX_CLAIM = "tx";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final JwtProperties SHARED_KEYS = new TestJwt().properties();
    private static final KeyPair FOREIGN_KEY_PAIR = generateKeyPair();

    private OAuthStateService stateService;
    private AesGcmCipher aesGcmCipher;
    private ObjectMapper objectMapper;
    private byte[] aesKey;
    private PrivateKey signingKey;

    @BeforeEach
    void setUp() throws Exception {
        signingKey = SHARED_KEYS.getPrivateKey();

        aesKey = new byte[32];
        new SecureRandom().nextBytes(aesKey);

        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setPrivateKey(SHARED_KEYS.getPrivateKey());
        jwtProperties.setPublicKey(SHARED_KEYS.getPublicKey());
        jwtProperties.setEncryptionKeyBytes(aesKey);

        aesGcmCipher = new AesGcmCipher();
        objectMapper = new ObjectMapper();
        stateService = new OAuthStateService(jwtProperties, objectMapper, aesGcmCipher);
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void open_WhenSealedByService_ReturnsSameTransaction() {
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
    void open_WhenNull_ReturnsEmpty() {
        assertThat(stateService.open(null)).isEmpty();
    }

    @Test
    void open_WhenBlank_ReturnsEmpty() {
        assertThat(stateService.open("   ")).isEmpty();
    }

    @Test
    void open_WhenTampered_ReturnsEmpty() {
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
    void open_WhenSignedByForeignKey_ReturnsEmpty() throws Exception {
        PrivateKey foreignKey = FOREIGN_KEY_PAIR.getPrivate();

        String foreignToken = Jwts.builder()
                .claim(TX_CLAIM, encryptTx("state", "verifier"))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 90_000L))
                .signWith(foreignKey, SignatureAlgorithm.RS256)
                .compact();

        assertThat(stateService.open(foreignToken)).isEmpty();
    }

    @Test
    void open_WhenExpired_ReturnsEmpty() throws Exception {
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
