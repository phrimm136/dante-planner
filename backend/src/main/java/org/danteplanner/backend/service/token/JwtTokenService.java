package org.danteplanner.backend.service.token;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.config.JwtProperties;
import org.danteplanner.backend.entity.UserRole;
import org.danteplanner.backend.exception.InvalidTokenException;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT token service implementing both generation and validation.
 * Uses RS256 signature + AES-GCM encrypted claims for enhanced security.
 */
@Service
@Slf4j
public class JwtTokenService implements TokenGenerator, TokenValidator {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_ENCRYPTED = "enc";

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private static final ThreadLocal<SecureRandom> SECURE_RANDOM =
        ThreadLocal.withInitial(SecureRandom::new);

    private final JwtProperties jwtProperties;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final byte[] encryptionKey;
    private final ObjectMapper objectMapper;
    private final JwtParser jwtParser;

    public JwtTokenService(JwtProperties jwtProperties, ObjectMapper objectMapper) {
        this.jwtProperties = jwtProperties;
        this.privateKey = jwtProperties.getPrivateKey();
        this.publicKey = jwtProperties.getPublicKey();
        this.encryptionKey = jwtProperties.getEncryptionKeyBytes();
        this.objectMapper = objectMapper;
        this.jwtParser = Jwts.parser()
                .verifyWith(publicKey)
                .build();
    }

    // ==================== TokenGenerator Implementation ====================

    @Override
    public String generateAccessToken(Long userId, String email, UserRole role) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_EMAIL, email);
        claims.put(CLAIM_TYPE, TokenClaims.TYPE_ACCESS);
        claims.put(CLAIM_ROLE, role.getValue());

        return buildToken(claims, email, jwtProperties.getAccessTokenExpiry());
    }

    @Override
    public String generateRefreshToken(Long userId, String email) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (email == null) {
            throw new IllegalArgumentException("email must not be null");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_TYPE, TokenClaims.TYPE_REFRESH);

        return buildToken(claims, email, jwtProperties.getRefreshTokenExpiry());
    }

    // ==================== TokenValidator Implementation ====================

    @Override
    public TokenClaims validateToken(String token) {
        Claims claims = parseToken(token);

        Long userId = claims.get(CLAIM_USER_ID, Long.class);
        String email = claims.getSubject();
        String type = claims.get(CLAIM_TYPE, String.class);
        String roleValue = claims.get(CLAIM_ROLE, String.class);

        // Parse role - null for old tokens or refresh tokens (backward compat)
        UserRole role = null;
        if (roleValue != null && UserRole.isValid(roleValue)) {
            role = UserRole.fromValue(roleValue);
        }

        if (userId == null) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MISSING_CLAIMS);
        }

        return new TokenClaims(
                userId,
                email,
                type,
                role,
                claims.getIssuedAt(),
                claims.getExpiration()
        );
    }

    @Override
    public Long getUserIdFromToken(String token) {
        TokenClaims claims = validateToken(token);
        return claims.userId();
    }

    @Override
    public boolean isTokenExpired(String token) {
        try {
            TokenClaims claims = validateToken(token);
            return claims.isExpired();
        } catch (InvalidTokenException e) {
            // Invalid tokens are treated as expired
            return true;
        }
    }

    // ==================== Additional Methods ====================

    /**
     * Extracts email from a token.
     *
     * @param token JWT token string
     * @return email from token subject
     * @throws InvalidTokenException if token is invalid
     */
    public String getEmailFromToken(String token) {
        TokenClaims claims = validateToken(token);
        return claims.email();
    }

    /**
     * Gets the token type (access or refresh).
     *
     * @param token JWT token string
     * @return token type
     * @throws InvalidTokenException if token is invalid
     */
    public String getTokenType(String token) {
        TokenClaims claims = validateToken(token);
        return claims.type();
    }

    // ==================== Private Helpers ====================

    private String buildToken(Map<String, Object> claims, String subject, Long expiryMs) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expiryMs);

        try {
            // Serialize claims to JSON
            String claimsJson = objectMapper.writeValueAsString(claims);

            // Encrypt claims with AES-GCM
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.get().nextBytes(iv);

            Cipher cipher = createEncryptCipher(encryptionKey, iv);
            byte[] ciphertext = cipher.doFinal(claimsJson.getBytes());

            // Prepend IV to ciphertext and Base64 encode
            byte[] ivAndCiphertext = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, ivAndCiphertext, 0, iv.length);
            System.arraycopy(ciphertext, 0, ivAndCiphertext, iv.length, ciphertext.length);
            String encryptedClaims = Base64.getEncoder().encodeToString(ivAndCiphertext);

            // Build JWT with only sub/iat/exp + encrypted claims
            return Jwts.builder()
                    .claim(CLAIM_ENCRYPTED, encryptedClaims)
                    .subject(subject)
                    .issuedAt(now)
                    .expiration(expiration)
                    .signWith(privateKey, SignatureAlgorithm.RS256)
                    .compact();
        } catch (Exception e) {
            log.error("Token generation failed: {}", e.getMessage(), e);
            throw new TokenGenerationException("Failed to generate JWT token", e);
        }
    }

    private Claims parseToken(String token) {
        try {
            // Verify RS256 signature and extract claims
            Claims claims = jwtParser
                    .parseSignedClaims(token)
                    .getPayload();

            // Extract and decrypt the encrypted claims
            String encryptedClaims = claims.get(CLAIM_ENCRYPTED, String.class);
            if (encryptedClaims == null) {
                throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED);
            }

            byte[] ivAndCiphertext = Base64.getDecoder().decode(encryptedClaims);
            if (ivAndCiphertext.length < GCM_IV_LENGTH) {
                throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED);
            }

            // Extract IV and ciphertext
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[ivAndCiphertext.length - GCM_IV_LENGTH];
            System.arraycopy(ivAndCiphertext, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(ivAndCiphertext, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            // Decrypt
            Cipher cipher = createDecryptCipher(encryptionKey, iv);
            byte[] decryptedBytes = cipher.doFinal(ciphertext);
            String claimsJson = new String(decryptedBytes);

            // Deserialize JSON to Map
            Map<String, Object> decryptedClaims = objectMapper.readValue(
                    claimsJson,
                    new TypeReference<Map<String, Object>>() {}
            );

            // Create new Claims combining decrypted custom claims with JWT standard claims
            return Jwts.claims()
                    .add(decryptedClaims)
                    .subject(claims.getSubject())
                    .issuedAt(claims.getIssuedAt())
                    .expiration(claims.getExpiration())
                    .build();

        } catch (javax.crypto.AEADBadTagException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED, e);
        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.EXPIRED, e);
        } catch (MalformedJwtException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED, e);
        } catch (SignatureException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.INVALID_SIGNATURE, e);
        } catch (JwtException e) {
            throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED, e);
        } catch (Exception e) {
            log.error("Unexpected token parsing failure: {}", e.getMessage(), e);
            throw new InvalidTokenException(InvalidTokenException.Reason.MALFORMED, e);
        }
    }

    private Cipher createEncryptCipher(byte[] key, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        return cipher;
    }

    private Cipher createDecryptCipher(byte[] key, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
        return cipher;
    }
}
