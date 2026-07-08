# Task: Harden JWT Token Security — RS256 Signing + AES-GCM Payload Encryption

## Description

The current JWT implementation uses HS256 (symmetric HMAC) signing with no payload encryption.
This means:
- The token payload (userId, email, role) is readable by anyone who Base64-decodes the cookie.
- A single shared secret controls both signing and verification. If it leaks, tokens are forgeable.

This task hardens the token layer on two axes:

1. **Payload encryption (AES-256-GCM):** Before signing, the JSON claims payload is encrypted
   with a dedicated AES-256 encryption key. The resulting JWT payload contains only an opaque
   ciphertext blob. Decoding the Base64 reveals nothing without the encryption key.

2. **Asymmetric signing (RS256):** Replace HS256 with RSA-2048. The private key signs tokens;
   the public key verifies them. Compromise of the public key does not allow forgery.

The change is fully contained within the token service layer. All interfaces (`TokenGenerator`,
`TokenValidator`), the `TokenClaims` record, `JwtAuthenticationFilter`, `TokenBlacklistService`,
and all controllers remain unchanged. The only files that change are:

- `application.properties` — remove `jwt.secret`, add RSA key paths + AES encryption key property
- `JwtProperties.java` — replace secret field with RSA key loading + encryption key validation
- `JwtTokenService.java` — encrypt payload before sign, decrypt after verify

Existing tokens (both access and refresh) will become invalid after deployment. This is expected
and safe because:
- Access tokens are 15 minutes. They expire naturally.
- Refresh tokens are 7 days. Users will need to re-login once.
- The `TokenBlacklistService` is unaffected — it hashes whatever string it receives.

## Research

Before implementation, confirm the following:

- **JJWT 0.12.5 RS256 support:** The library supports `SignatureAlgorithm.RS256` natively via
  `Keys.rsaKeyPairGenerator()`. No additional dependency needed. Confirm the API surface:
  `Jwts.builder().signWith(privateKey, SignatureAlgorithm.RS256)` and
  `Jwts.parser().verifyWith(publicKey)`.

- **AES-256-GCM in standard Java:** `javax.crypto.Cipher` with `AES/GCM/NoPadding` is available
  in Java 21 without external libs. The IV (initialization vector) must be 12 bytes, generated
  per-token via `SecureRandom`. The IV is prepended to the ciphertext so decryption can extract it.

- **Apple private key precedent:** The project already loads an RSA private key from a file path
  via `APPLE_PRIVATE_KEY_PATH`. Follow the same pattern for JWT keys. The file is PEM-encoded.

- **SSM deployment:** Production secrets are fetched from AWS SSM Parameter Store at deploy time
  (see `architecture-map.md`). The RSA private key and AES encryption key will need new SSM paths.
  The public key can be a file checked into the repo (it is not secret) or also in SSM — decide
  based on team convention.

- **Test setup:** `JwtTokenServiceTest` constructs `JwtProperties` directly and sets fields via
  setters. The new properties (key paths, encryption key) must be settable the same way so tests
  can inject in-memory generated keys without reading files from disk.

## Scope (Read for context)

- `backend/src/main/java/org/danteplanner/backend/service/token/JwtTokenService.java` — current implementation
- `backend/src/main/java/org/danteplanner/backend/config/JwtProperties.java` — current config
- `backend/src/main/resources/application.properties` — current properties
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenGenerator.java` — interface (unchanged)
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenValidator.java` — interface (unchanged)
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenClaims.java` — record (unchanged)
- `backend/src/main/java/org/danteplanner/backend/security/JwtAuthenticationFilter.java` — filter (unchanged)
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenBlacklistService.java` — blacklist (unchanged)
- `backend/src/test/java/org/danteplanner/backend/service/token/JwtTokenServiceTest.java` — existing tests (must pass)

## Target Code Area

### Modified files
- `backend/src/main/resources/application.properties`
  - Remove: `jwt.secret`
  - Add: `jwt.private-key-path`, `jwt.public-key-path`, `jwt.encryption-key`

- `backend/src/main/java/org/danteplanner/backend/config/JwtProperties.java`
  - Remove: `secret` field and placeholder validation
  - Add: `privateKeyPath`, `publicKeyPath`, `encryptionKey` fields
  - Add: `@PostConstruct` that loads PEM files into `PrivateKey` / `PublicKey` objects
  - Add: validation that `encryptionKey` is exactly 32 bytes (256 bits) when Base64-decoded
  - Expose: `getPrivateKey()`, `getPublicKey()`, `getEncryptionKey()` (returns raw `byte[]`)

- `backend/src/main/java/org/danteplanner/backend/service/token/JwtTokenService.java`
  - Constructor: initialize `SecretKey signingKey` → `PrivateKey privateKey` + `PublicKey publicKey`
  - `buildToken()`: serialize claims to JSON → encrypt with AES-GCM → put ciphertext as single
    claim in JWT → sign with RS256
  - `parseToken()`: verify signature with public key → extract ciphertext claim → decrypt with
    AES-GCM → deserialize JSON back to Claims map → reconstruct `Claims` object

### Test file update
- `backend/src/test/java/org/danteplanner/backend/service/token/JwtTokenServiceTest.java`
  - Generate an RSA key pair in `@BeforeEach` via `KeyPairGenerator`
  - Generate a 32-byte AES key via `SecureRandom`
  - Inject keys into `JwtProperties` programmatically (no file I/O in tests)
  - All existing test cases must pass without modification to their assertions
  - Add: one new test that verifies the JWT payload is NOT Base64-decodable to readable JSON

## System Context

- Feature domain: Authentication / Token Security
- Core files: `JwtTokenService.java`, `JwtProperties.java`, `JwtAuthenticationFilter.java`
- Cross-cutting concerns: All authenticated requests pass through the token layer. The change must
  be zero-overhead from the perspective of every consumer of `TokenGenerator` and `TokenValidator`.

## Impact Analysis

- `JwtTokenService.java` (High impact — called on every authenticated request)
  - Dependents: `JwtAuthenticationFilter`, `AuthController` (via `TokenGenerator`/`TokenValidator` interfaces)
  - The interfaces do not change, so no ripple effects beyond this file.
- `JwtProperties.java` (Medium impact — Spring config bean, injected into `JwtTokenService`)
  - Only `JwtTokenService` depends on it directly.
- `application.properties` (Medium impact — property names change)
  - All environments (dev, prod) must have the new properties set.
  - The old `jwt.secret` property should be removed to avoid confusion.
- Existing tokens break (Expected — see Description).

## Risk Assessment

- **Key rotation:** After deployment, there is no way to verify tokens signed with the old HS256
  secret. This is a hard cutover. All users are logged out simultaneously. If a graceful transition
  is needed, a two-phase approach (accept both old and new signatures for one access-token cycle)
  would be required — but given the 15-minute access token lifetime, a hard cutover is acceptable.

- **Performance:** AES-GCM encryption adds ~microseconds per token operation. RS256 signing is
  ~10x slower than HS256 (~1ms vs ~0.1ms). At the scale of this application (cookie-based, one
  token validation per request), this is not measurable.

- **IV reuse:** AES-GCM is catastrophic if the IV is reused with the same key. The implementation
  must generate a fresh 12-byte IV via `SecureRandom` for every token. This is the single most
  critical correctness requirement.

- **Key file security:** The RSA private key and AES encryption key must not be committed to the
  repository. They must be injected via environment variables or fetched from SSM at deploy time.
  The public key is not secret and can be a checked-in file or also environment-injected.

- **Backward compatibility:** None needed. Old tokens will fail signature verification and trigger
  the existing `InvalidTokenException(INVALID_SIGNATURE)` path, which correctly clears the
  security context.

## Testing Guidelines

### Manual Verification
1. Generate an RSA-2048 key pair (e.g., `openssl genrsa -out jwt_private.pem 2048`)
2. Extract the public key (`openssl rsa -in jwt_private.pem -pubout -out jwt_public.pem`)
3. Generate a 32-byte Base64-encoded AES key (e.g., `openssl rand -base64 32`)
4. Set the three new properties in `application-dev.properties` or via environment variables
5. Start the backend. Verify it starts without errors (key loading validation passes).
6. Log in via Google OAuth. Inspect the `accessToken` cookie in DevTools.
7. Copy the cookie value. Paste the middle segment (between the two dots) into a Base64 decoder.
8. Verify the decoded output is NOT readable JSON — it should be opaque binary (the encrypted payload).
9. Verify the application functions normally: protected endpoints return data, refresh works.

### Automated Functional Verification
- [ ] Token generation: `generateAccessToken` and `generateRefreshToken` produce non-null strings
- [ ] Token validation: `validateToken` returns correct `TokenClaims` for freshly generated tokens
- [ ] Payload opacity: The JWT payload segment, when Base64-decoded, is not valid JSON
- [ ] Signature rejection: A token signed with a different private key is rejected with `INVALID_SIGNATURE`
- [ ] Expiry: Tokens with 1ms expiry are correctly detected as expired
- [ ] Claims integrity: userId, email, type, role survive the encrypt→sign→verify→decrypt round-trip
- [ ] Role handling: ADMIN, MODERATOR, NORMAL roles all round-trip correctly
- [ ] Refresh token: Does not contain role claim; role is null in parsed claims

### Edge Cases
- [ ] Empty or null encryption key at startup: Application fails to start with clear error
- [ ] Encryption key that is not exactly 32 bytes when Base64-decoded: Application fails to start
- [ ] Missing private key file: Application fails to start with clear error
- [ ] Missing public key file: Application fails to start with clear error
- [ ] Token tampered after signing (any byte changed): Signature verification fails
- [ ] IV reuse prevention: Each generated token has a unique IV (verify by generating 100 tokens
  and confirming no two payloads are identical, even with same claims)
