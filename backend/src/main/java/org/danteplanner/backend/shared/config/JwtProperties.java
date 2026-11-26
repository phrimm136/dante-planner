package org.danteplanner.backend.shared.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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

@Getter
@Slf4j
public class JwtProperties {

    private static final int MIN_RSA_KEY_BITS = 2048;
    private static final int AES_256_KEY_BYTES = 32;

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final byte[] encryptionKeyBytes;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public static JwtProperties load(JwtSettings settings) {
        JwtProperties properties;
        try {
            properties = new JwtProperties(loadPrivateKey(settings.privateKeyPath()),
                    loadPublicKey(settings.publicKeyPath()), decodeEncryptionKey(settings.encryptionKey()),
                    settings.accessTokenExpiry(), settings.refreshTokenExpiry());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load JWT keys from files", e);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to parse JWT keys", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Failed to decode encryption key (invalid Base64)", e);
        }
        log.info("JWT keys loaded successfully");
        return properties;
    }

    public JwtProperties(PrivateKey privateKey, PublicKey publicKey, byte[] encryptionKeyBytes,
            long accessTokenExpiry, long refreshTokenExpiry) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.encryptionKeyBytes = encryptionKeyBytes;
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    private static byte[] decodeEncryptionKey(String encryptionKey) {
        byte[] key = Base64.getDecoder().decode(encryptionKey);
        if (key.length != AES_256_KEY_BYTES) {
            throw new IllegalStateException("JWT encryption key must be exactly " + AES_256_KEY_BYTES
                    + " bytes (256 bits) for AES-256. Got " + key.length + " bytes.");
        }
        return key;
    }

    public int getAccessTokenExpirySeconds() {
        return (int) (accessTokenExpiry / 1000);
    }

    public int getRefreshTokenExpirySeconds() {
        return (int) (refreshTokenExpiry / 1000);
    }

    public int getCookieExpirySeconds() {
        return (int) (refreshTokenExpiry / 1000);
    }

    private static PrivateKey loadPrivateKey(String path) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] keyBytes = decodePem(path, "PRIVATE KEY");
        PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));

        if (key instanceof RSAPrivateKey rsaKey) {
            log.info("Loaded RSA private key ({} bits)", requireStrongEnough(rsaKey.getModulus().bitLength(), "private"));
        }
        return key;
    }

    private static PublicKey loadPublicKey(String path) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
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
