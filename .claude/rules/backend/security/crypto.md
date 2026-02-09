---
paths:
  - "backend/**/service/token/**/*.java"
  - "backend/**/crypto/**/*.java"
  - "backend/**/security/*Cipher*.java"
---

# Cryptography Patterns

## Mandatory Rules

- **Extract cipher factory methods** - createEncryptCipher/createDecryptCipher eliminate duplication
- **Fail-fast on weak keys** - Validate RSA minimum 2048 bits at startup
- **ThreadLocal for concurrent crypto** - SecureRandom above 100 req/sec

## Cipher Factory Pattern

**Problem:** Cipher initialization duplicated between encrypt and decrypt methods with hardcoded algorithm/mode.

**Solution:** Extract createEncryptCipher(key, iv) and createDecryptCipher(key, iv) methods. Centralizes algorithm choice (AES/GCM/NoPadding), mode (128-bit tag), and initialization.

**Benefit:** Algorithm changes (AES-GCM → ChaCha20-Poly1305) require single-point update. Reduces test surface.

**Reference:** `JwtTokenService.java`

## IV Generation

**Problem:** GCM IV reuse with same key = catastrophic failure (authentication bypass + confidentiality loss).

**Solution:** Generate fresh 12-byte IV per encryption via SecureRandom. Use ThreadLocal instance above 100 req/sec to prevent contention.

**Note:** Pure random IV has birthday paradox collision at 2^48 tokens (281 trillion). Negligible risk below 1000 req/sec sustained.

**Reference:** `JwtTokenService.java`
