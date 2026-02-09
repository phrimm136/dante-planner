package org.danteplanner.backend.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * JWT configuration properties with startup validation.
 * Loads RSA keys from PEM files and AES encryption key at startup.
 *
 * Properties bound from application.properties:
 * - jwt.private-key-path: Path to RSA private key (PKCS#8 PEM format)
 * - jwt.public-key-path: Path to RSA public key (X.509 PEM format)
 * - jwt.encryption-key: Base64-encoded AES-256 key (32 bytes)
 * - jwt.access-token-expiry: Access token lifetime in milliseconds
 * - jwt.refresh-token-expiry: Refresh token lifetime in milliseconds
 */
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Validated
@Getter
@Setter
@Slf4j
public class JwtProperties {

    private String privateKeyPath;
    private String publicKeyPath;
    private String encryptionKey;

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private byte[] encryptionKeyBytes;

    /**
     * Access token expiry time in milliseconds.
     * Default: 900000 (15 minutes)
     */
    @Min(value = 1, message = "Access token expiry must be positive")
    private Long accessTokenExpiry = 900000L;

    /**
     * Refresh token expiry time in milliseconds.
     * Default: 604800000 (7 days)
     */
    @Min(value = 1, message = "Refresh token expiry must be positive")
    private Long refreshTokenExpiry = 604800000L;

    /**
     * Returns access token expiry in seconds (for cookie max-age).
     */
    public int getAccessTokenExpirySeconds() {
        return (int) (accessTokenExpiry / 1000);
    }

    /**
     * Returns refresh token expiry in seconds (for cookie max-age).
     */
    public int getRefreshTokenExpirySeconds() {
        return (int) (refreshTokenExpiry / 1000);
    }

    /**
     * Returns cookie expiry in seconds for both access and refresh token cookies.
     * Uses refresh token lifetime (7 days) so cookies survive for refresh flow.
     * JWT tokens expire independently; server validates actual token expiry.
     */
    public int getCookieExpirySeconds() {
        return (int) (refreshTokenExpiry / 1000);
    }

    /**
     * Loads RSA keys from PEM files and validates encryption key at startup.
     * Fails fast if any key is missing or invalid.
     */
    @PostConstruct
    public void loadKeys() {
        if (privateKeyPath == null || privateKeyPath.isBlank()) {
            throw new IllegalStateException("JWT private key path is required. Set JWT_PRIVATE_KEY_PATH environment variable.");
        }
        if (publicKeyPath == null || publicKeyPath.isBlank()) {
            throw new IllegalStateException("JWT public key path is required. Set JWT_PUBLIC_KEY_PATH environment variable.");
        }
        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException("JWT encryption key is required. Set JWT_ENCRYPTION_KEY environment variable.");
        }

        try {
            this.privateKey = loadPrivateKey(privateKeyPath);
            this.publicKey = loadPublicKey(publicKeyPath);
            this.encryptionKeyBytes = Base64.getDecoder().decode(encryptionKey);

            if (encryptionKeyBytes.length != 32) {
                throw new IllegalStateException(
                    "JWT encryption key must be exactly 32 bytes (256 bits) for AES-256. Got " + encryptionKeyBytes.length + " bytes."
                );
            }

            log.info("JWT keys loaded successfully");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load JWT keys from files", e);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to parse JWT keys", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Failed to decode encryption key (invalid Base64)", e);
        }
    }

    private PrivateKey loadPrivateKey(String path) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String pemContent = new String(Files.readAllBytes(Paths.get(path)));
        String base64Key = pemContent
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

        if (privateKey instanceof RSAPrivateKey rsaKey) {
            int keySize = rsaKey.getModulus().bitLength();
            if (keySize < 2048) {
                throw new IllegalStateException(
                    "RSA private key must be at least 2048 bits. Got " + keySize + " bits."
                );
            }
            log.info("Loaded RSA private key ({} bits)", keySize);
        }

        return privateKey;
    }

    private PublicKey loadPublicKey(String path) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String pemContent = new String(Files.readAllBytes(Paths.get(path)));
        String base64Key = pemContent
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(keySpec);

        if (publicKey instanceof RSAPublicKey rsaKey) {
            int keySize = rsaKey.getModulus().bitLength();
            if (keySize < 2048) {
                throw new IllegalStateException(
                    "RSA public key must be at least 2048 bits. Got " + keySize + " bits."
                );
            }
        }

        return publicKey;
    }
}
