package org.danteplanner.backend.shared.security;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * A token is {@code <nonce>.<issuedAtMs>.<mac>}, where the MAC covers the two fields ahead of it
 * under a key derived for this purpose alone.
 */
@Component
@Slf4j
public class CsrfTokenService {

    private static final String MAC_ALGORITHM = "HmacSHA256";
    private static final String KEY_LABEL = "danteplanner/csrf-token/v1";
    private static final int NONCE_BYTE_LENGTH = 16;
    private static final char FIELD_SEPARATOR = '.';

    static final long MAX_AGE_MS = 604800_000L;

    static final long CLOCK_SKEW_MS = 60_000L;

    private final byte[] signingKey;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public CsrfTokenService(JwtProperties jwtProperties) {
        this.signingKey = expand(jwtProperties.getEncryptionKeyBytes());
    }

    public String mint() {
        byte[] nonce = new byte[NONCE_BYTE_LENGTH];
        secureRandom.nextBytes(nonce);
        String payload = encoder.encodeToString(nonce) + FIELD_SEPARATOR + System.currentTimeMillis();
        return payload + FIELD_SEPARATOR + encoder.encodeToString(mac(payload));
    }

    public boolean isValid(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        int macSeparator = token.lastIndexOf(FIELD_SEPARATOR);
        if (macSeparator <= 0) {
            return false;
        }
        String payload = token.substring(0, macSeparator);
        byte[] presented;
        try {
            presented = Base64.getUrlDecoder().decode(token.substring(macSeparator + 1));
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!MessageDigest.isEqual(mac(payload), presented)) {
            return false;
        }
        return withinLifetime(payload);
    }

    private boolean withinLifetime(String payload) {
        int timeSeparator = payload.lastIndexOf(FIELD_SEPARATOR);
        if (timeSeparator < 0) {
            return false;
        }
        try {
            long issuedAt = Long.parseLong(payload.substring(timeSeparator + 1));
            long age = System.currentTimeMillis() - issuedAt;
            return age >= -CLOCK_SKEW_MS && age <= MAX_AGE_MS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private byte[] mac(String payload) {
        try {
            Mac hmac = Mac.getInstance(MAC_ALGORITHM);
            hmac.init(new SecretKeySpec(signingKey, MAC_ALGORITHM));
            return hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    /**
     * HKDF-Expand with a purpose label, taking the shared secret as an already-uniform
     * pseudorandom key.
     */
    private static byte[] expand(byte[] sharedSecret) {
        try {
            Mac hmac = Mac.getInstance(MAC_ALGORITHM);
            hmac.init(new SecretKeySpec(sharedSecret, MAC_ALGORITHM));
            hmac.update(KEY_LABEL.getBytes(StandardCharsets.UTF_8));
            hmac.update((byte) 0x01);
            return hmac.doFinal();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
