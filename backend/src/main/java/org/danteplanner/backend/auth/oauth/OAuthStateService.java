package org.danteplanner.backend.auth.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.auth.token.AesGcmCipher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/**
 * <p>The scratchpad is <b>encrypt-then-sign</b>: the {@code {state, codeVerifier}} JSON is
 * AES-256-GCM encrypted (confidential at rest — the verifier never leaves the server in the
 * clear, INV5) and embedded as a claim in a short-lived RS256-signed JWT (tamper-proof,
 * 90-second expiry).
 * {@link #open} fails closed on any signature, expiry, or decryption error.</p>
 */
@Service
@Slf4j
public class OAuthStateService {

    public static final int OAUTH_TX_EXPIRY_SECONDS = 90;

    private static final String CLAIM_TX = "tx";
    private static final String SHA_256 = "SHA-256";

    /**
     * 32 bytes base64url-encodes
     * to 43 unreserved characters — the PKCE minimum (RFC 7636 §4.1).
     */
    private static final int RANDOM_BYTE_LENGTH = 32;

    private final byte[] encryptionKey;
    private final PrivateKey privateKey;
    private final ObjectMapper objectMapper;
    private final AesGcmCipher aesGcmCipher;
    private final JwtParser jwtParser;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder urlDecoder = Base64.getUrlDecoder();

    public record OAuthTransaction(String state, String codeVerifier, String returnTo) {
    }

    public OAuthStateService(JwtProperties jwtProperties, ObjectMapper objectMapper, AesGcmCipher aesGcmCipher) {
        this.encryptionKey = jwtProperties.getEncryptionKeyBytes();
        this.privateKey = jwtProperties.getPrivateKey();
        this.objectMapper = objectMapper;
        this.aesGcmCipher = aesGcmCipher;
        this.jwtParser = Jwts.parser()
                .verifyWith(jwtProperties.getPublicKey())
                .build();
    }

    public String generateState() {
        return randomUrlToken();
    }

    public String generateCodeVerifier() {
        return randomUrlToken();
    }

    public String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return urlEncoder.encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    public String seal(String state, String codeVerifier, String returnTo) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(new OAuthTransaction(state, codeVerifier, returnTo));
            byte[] encrypted = aesGcmCipher.encrypt(encryptionKey, json);
            String txClaim = urlEncoder.encodeToString(encrypted);

            Date now = new Date();
            Date expiration = new Date(now.getTime() + OAUTH_TX_EXPIRY_SECONDS * 1000L);
            return Jwts.builder()
                    .claim(CLAIM_TX, txClaim)
                    .issuedAt(now)
                    .expiration(expiration)
                    .signWith(privateKey, SignatureAlgorithm.RS256)
                    .compact();
        } catch (GeneralSecurityException | JsonProcessingException e) {
            throw new IllegalStateException("Failed to seal oauth_tx", e);
        }
    }

    public Optional<OAuthTransaction> open(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = jwtParser.parseSignedClaims(tokenValue).getPayload();
            String txClaim = claims.get(CLAIM_TX, String.class);
            if (txClaim == null) {
                return Optional.empty();
            }
            byte[] encrypted = urlDecoder.decode(txClaim);
            byte[] json = aesGcmCipher.decrypt(encryptionKey, encrypted);
            return Optional.of(objectMapper.readValue(json, OAuthTransaction.class));
        } catch (JwtException | GeneralSecurityException | IOException | IllegalArgumentException e) {
            log.warn("oauth_tx open rejected [{}]: {}", e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    private String randomUrlToken() {
        byte[] bytes = new byte[RANDOM_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return urlEncoder.encodeToString(bytes);
    }
}
