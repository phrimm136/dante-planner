package org.danteplanner.backend.shared.config;

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
import java.nio.charset.StandardCharsets;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Validated
@Getter
@Setter
@Slf4j
public class JwtProperties {

    private static final int MIN_RSA_KEY_BITS = 2048;
    private static final int AES_256_KEY_BYTES = 32;

    private String privateKeyPath;
    private String publicKeyPath;
    private String encryptionKey;

    private PrivateKey privateKey;
    private PublicKey publicKey;
    private byte[] encryptionKeyBytes;

    @Min(value = 1, message = "Access token expiry must be positive")
    private long accessTokenExpiry = 900000L;

    @Min(value = 1, message = "Refresh token expiry must be positive")
    private long refreshTokenExpiry = 604800000L;

    public int getAccessTokenExpirySeconds() {
        return (int) (accessTokenExpiry / 1000);
    }

    public int getRefreshTokenExpirySeconds() {
        return (int) (refreshTokenExpiry / 1000);
    }

    public int getCookieExpirySeconds() {
        return (int) (refreshTokenExpiry / 1000);
    }

    @PostConstruct
    public void loadKeys() {
        requireConfigured(privateKeyPath, "private key path", "JWT_PRIVATE_KEY_PATH");
        requireConfigured(publicKeyPath, "public key path", "JWT_PUBLIC_KEY_PATH");
        requireConfigured(encryptionKey, "encryption key", "JWT_ENCRYPTION_KEY");

        try {
            this.privateKey = loadPrivateKey(privateKeyPath);
            this.publicKey = loadPublicKey(publicKeyPath);
            this.encryptionKeyBytes = Base64.getDecoder().decode(encryptionKey);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load JWT keys from files", e);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to parse JWT keys", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Failed to decode encryption key (invalid Base64)", e);
        }

        if (encryptionKeyBytes.length != AES_256_KEY_BYTES) {
            throw new IllegalStateException("JWT encryption key must be exactly " + AES_256_KEY_BYTES
                    + " bytes (256 bits) for AES-256. Got " + encryptionKeyBytes.length + " bytes.");
        }

        log.info("JWT keys loaded successfully");
    }

    private static void requireConfigured(String value, String what, String envVar) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "JWT " + what + " is required. Set " + envVar + " environment variable.");
        }
    }

    private PrivateKey loadPrivateKey(String path) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = decodePem(path, "PRIVATE KEY");
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));

        if (key instanceof RSAPrivateKey rsaKey) {
            log.info("Loaded RSA private key ({} bits)", requireStrongEnough(rsaKey.getModulus().bitLength(), "private"));
        }
        return key;
    }

    private PublicKey loadPublicKey(String path) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = decodePem(path, "PUBLIC KEY");
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));

        if (key instanceof RSAPublicKey rsaKey) {
            requireStrongEnough(rsaKey.getModulus().bitLength(), "public");
        }
        return key;
    }

    private static byte[] decodePem(String path, String label) throws IOException {
        String pemContent = Files.readString(Paths.get(path), StandardCharsets.UTF_8);
        String base64Key = pemContent
            .replace("-----BEGIN " + label + "-----", "")
            .replace("-----END " + label + "-----", "")
            .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64Key);
    }

    private static int requireStrongEnough(int keySizeBits, String which) {
        if (keySizeBits < MIN_RSA_KEY_BITS) {
            throw new IllegalStateException("RSA " + which + " key must be at least "
                    + MIN_RSA_KEY_BITS + " bits. Got " + keySizeBits + " bits.");
        }
        return keySizeBits;
    }
}
