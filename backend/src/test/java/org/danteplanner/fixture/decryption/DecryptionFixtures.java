package org.danteplanner.fixture.decryption;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class DecryptionFixtures {

    private static final int TAG_BITS = 128;

    private DecryptionFixtures() {
    }

    public sealed interface Opening {

        record Opened(byte[] plaintext) implements Opening {
        }

        record Refused() implements Opening {
        }
    }

    public static final class Decrypter {

        public Opening decide(byte[] key, byte[] iv, byte[] ciphertext) {
            try {
                if (ciphertext.length == 0) {
                    throw new AEADBadTagException("empty");
                }
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
                return new Opening.Opened(cipher.doFinal(ciphertext));
            } catch (GeneralSecurityException e) {
                return new Opening.Refused();
            }
        }
    }

    public static final class RandomDrawer {

        public Opening decide(byte[] key, byte[] ciphertext) {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            return new Decrypter().decide(key, iv, ciphertext);
        }
    }

    public static final class IvDrawingEncrypter {

        public Opening decide(byte[] key, byte[] plaintext) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
                return new Opening.Opened(cipher.doFinal(plaintext));
            } catch (GeneralSecurityException e) {
                return new Opening.Refused();
            }
        }
    }
}
