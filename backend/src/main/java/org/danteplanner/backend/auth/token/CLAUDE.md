# Tokens & Crypto

- All symmetric crypto goes through `AesGcmCipher`, which exposes a single `createCipher(mode, key, iv)` — never open-code `Cipher.getInstance` elsewhere or add a second cipher factory.
- `JwtTokenService` is RS256 JWT only — no symmetric JWT signing paths.
- A shared `SecureRandom` is the convention — do not introduce ThreadLocal SecureRandom instances.
- Fresh random 12-byte IV per GCM encryption; IV reuse under the same key breaks both authentication and confidentiality.
