package org.danteplanner.backend.service.token;

import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-256-GCM authenticated encryption helper.
 *
 * <p>Single source of the project's symmetric crypto: a fresh 96-bit IV is generated per
 * encryption and prepended to the ciphertext, so the same key never reuses an IV (GCM IV
 * reuse is catastrophic). Decryption fails closed — a tampered ciphertext or tag throws
 * {@link AEADBadTagException}. Reused by token and OAuth-transaction sealing.</p>
 *
 * @see <a href="https://owasp.org/www-community/Cryptographic_Storage_Cheat_Sheet">OWASP Cryptographic Storage</a>
 */
@Component
public class AesGcmCipher {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Encrypts plaintext under the given key, returning {@code IV || ciphertext+tag}.
     *
     * @param key       AES-256 key (32 bytes)
     * @param plaintext data to encrypt
     * @return IV-prefixed ciphertext
     * @throws GeneralSecurityException if encryption fails
     */
    public byte[] encrypt(byte[] key, byte[] plaintext) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);

        Cipher cipher = createCipher(Cipher.ENCRYPT_MODE, key, iv);
        byte[] ciphertext = cipher.doFinal(plaintext);

        byte[] result = new byte[IV_LENGTH + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, IV_LENGTH);
        System.arraycopy(ciphertext, 0, result, IV_LENGTH, ciphertext.length);
        return result;
    }

    /**
     * Decrypts an {@code IV || ciphertext+tag} payload produced by {@link #encrypt}.
     *
     * @param key     AES-256 key (32 bytes)
     * @param payload IV-prefixed ciphertext
     * @return the original plaintext
     * @throws GeneralSecurityException if the payload is malformed, tampered, or the tag fails
     */
    public byte[] decrypt(byte[] key, byte[] payload) throws GeneralSecurityException {
        if (payload.length <= IV_LENGTH) {
            throw new AEADBadTagException("Ciphertext too short to contain IV and tag");
        }
        byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(payload, IV_LENGTH, payload.length);

        Cipher cipher = createCipher(Cipher.DECRYPT_MODE, key, iv);
        return cipher.doFinal(ciphertext);
    }

    private Cipher createCipher(int mode, byte[] key, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return cipher;
    }
}
