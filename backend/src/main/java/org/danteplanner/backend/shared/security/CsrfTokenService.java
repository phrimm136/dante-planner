package org.danteplanner.backend.shared.security;

import lombok.extern.slf4j.Slf4j;
import org.danteplanner.backend.shared.config.JwtProperties;
import org.danteplanner.backend.shared.failure.CommandOutcome;
import org.danteplanner.backend.shared.failure.Parsed;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
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
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    public CsrfTokenService(JwtProperties jwtProperties, Clock clock) {
        this.signingKey = expand(jwtProperties.getEncryptionKeyBytes());
        this.clock = clock;
    }

    public String mint() {
        byte[] nonce = new byte[NONCE_BYTE_LENGTH];
        secureRandom.nextBytes(nonce);
        String payload = encoder.encodeToString(nonce) + FIELD_SEPARATOR + clock.millis();
        return payload + FIELD_SEPARATOR + encoder.encodeToString(mac(payload));
    }

    public sealed interface TokenOutcome extends CommandOutcome {

        @Parsed
        record ServerIssued(String value) implements TokenOutcome {

            private static TokenOutcome of(String token, Fields.Signed signed, byte[] expectedMac, long nowMs) {
                return switch (Standing.of(signed, expectedMac, nowMs)) {
                    case CURRENT -> new ServerIssued(token);
                    case FORGED, OUTSIDE_LIFETIME -> new Unrecognized();
                };
            }
        }

        record Unrecognized() implements TokenOutcome {
        }

        record Absent() implements TokenOutcome {
        }
    }

    public TokenOutcome parse(String token) {
        return switch (Fields.of(token)) {
            case Fields.Malformed malformed -> new TokenOutcome.Unrecognized();
            case Fields.Signed signed ->
                    TokenOutcome.ServerIssued.of(token, signed, mac(signed.payload()), clock.millis());
        };
    }

    private sealed interface Fields {

        record Signed(String payload, byte[] presentedMac) implements Fields {
        }

        record Malformed() implements Fields {
        }

        static Fields of(String token) {
            int macSeparator = token.lastIndexOf(FIELD_SEPARATOR);
            if (macSeparator <= 0) {
                return new Malformed();
            }
            try {
                return new Signed(token.substring(0, macSeparator),
                        Base64.getUrlDecoder().decode(token.substring(macSeparator + 1)));
            } catch (IllegalArgumentException e) {
                return new Malformed();
            }
        }
    }

    private enum Standing {
        CURRENT,
        FORGED,
        OUTSIDE_LIFETIME;

        static Standing of(Fields.Signed signed, byte[] expectedMac, long nowMs) {
            if (!MessageDigest.isEqual(expectedMac, signed.presentedMac())) {
                return FORGED;
            }
            return ofGenuine(signed.payload(), nowMs);
        }

        private static Standing ofGenuine(String payload, long nowMs) {
            int timeSeparator = payload.lastIndexOf(FIELD_SEPARATOR);
            if (timeSeparator < 0) {
                return OUTSIDE_LIFETIME;
            }
            try {
                long age = nowMs - Long.parseLong(payload.substring(timeSeparator + 1));
                return age >= -CLOCK_SKEW_MS && age <= MAX_AGE_MS ? CURRENT : OUTSIDE_LIFETIME;
            } catch (NumberFormatException e) {
                return OUTSIDE_LIFETIME;
            }
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
