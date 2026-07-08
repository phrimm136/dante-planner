# Execution Plan: JWT Token Hardening (RS256 + AES-GCM)

## Confirmed Decisions

- No PEM-loading precedent exists. APPLE_PRIVATE_KEY_PATH is declared but never consumed. JwtProperties @PostConstruct will be the first PEM loader in the codebase.
- Encryption boundary: sub/iat/exp stay OUTSIDE encryption. JJWT enforces exp during parseSignedClaims() before the app receives Claims. Only custom claims (userId, email, type, role) go into the enc blob.
- Test bypass: @PostConstruct fires only when Spring instantiates. Tests use new JwtProperties() — @PostConstruct never runs. Tests call setPrivateKey/setPublicKey/setEncryptionKeyBytes which write to the same internal fields @PostConstruct would populate.
- Three test methods construct secondary JwtTokenService instances inline. All three currently call setSecret. After this change all three use the direct key setters. The invalid-signature test specifically needs a DIFFERENT keypair.
- ObjectMapper is already on classpath via Spring Boot. Used for JSON serialization of custom claims before encryption and deserialization after decryption.

## Dependency Analysis

### Files Being Modified

| File | Impact | Depends On | Used By |
|------|--------|------------|---------|
| application.properties | Medium | None | Spring property binding |
| JwtProperties.java | Medium | application.properties property names | JwtTokenService only |
| JwtTokenService.java | High | JwtProperties API (getPrivateKey, getPublicKey, getEncryptionKeyBytes) | JwtAuthenticationFilter, AuthController (via interfaces) |
| JwtTokenServiceTest.java | Low | All three above | Nothing (test-only) |

### Files Confirmed Unchanged

| File | Why |
|------|-----|
| TokenGenerator.java | Interface signatures untouched |
| TokenValidator.java | Interface signatures untouched |
| TokenClaims.java | Record fields untouched |
| JwtAuthenticationFilter.java | Depends only on TokenValidator/TokenGenerator interfaces |
| TokenBlacklistService.java | Hashes the token string; content is opaque to it |
| InvalidTokenException.java | All four Reason enum values still map correctly |
| AuthController.java | Uses interfaces only |

### Ripple Effect Map

- application.properties property names change → JwtProperties field names must match exactly (Spring @ConfigurationProperties binding is name-based)
- JwtProperties internal API changes → JwtTokenService constructor must consume new getters
- JwtTokenService internal behavior changes → Test setup must supply keys via new setters; all 12 existing assertions remain valid because encrypt/decrypt is transparent to round-trip correctness

## Execution Order

### Phase 1: Properties (Step 1)

1. **application.properties** — Remove jwt.secret. Add three new properties.
   - Remove: jwt.secret=${JWT_SECRET:...} (line 28)
   - Add: jwt.private-key-path=${JWT_PRIVATE_KEY_PATH:}
   - Add: jwt.public-key-path=${JWT_PUBLIC_KEY_PATH:}
   - Add: jwt.encryption-key=${JWT_ENCRYPTION_KEY:}
   - Position: lines 28-30, replacing the single jwt.secret line. jwt.access-token-expiry and jwt.refresh-token-expiry remain unchanged immediately after.
   - Depends on: none
   - Enables: Step 2

### Phase 2: JwtProperties (Step 2)

2. **JwtProperties.java** — Replace credential section. Keep @Configuration, @ConfigurationProperties(prefix="jwt"), @Validated, Lombok annotations, and all expiry fields/methods untouched.
   - Remove: secret field, @NotBlank/@Size on it, PLACEHOLDER_PATTERNS array, validateSecretNotPlaceholder() method
   - Add Spring-bound string property fields: privateKeyPath, publicKeyPath, encryptionKey
   - Add internal key fields (not Spring-bound): privateKey (PrivateKey), publicKey (PublicKey), encryptionKeyBytes (byte[])
   - Add @PostConstruct that:
     - Validates privateKeyPath, publicKeyPath, encryptionKey are not blank (IllegalStateException if any are)
     - Reads PEM files via Files.readAllBytes, strips PEM headers/footers, Base64-decodes body
     - Loads PrivateKey via KeyFactory("RSA") + PKCS8EncodedKeySpec
     - Loads PublicKey via KeyFactory("RSA") + X509EncodedKeySpec
     - Base64-decodes encryptionKey string to byte[]
     - Validates decoded byte[] is exactly 32 bytes (IllegalStateException if not)
   - Add direct setters for test injection: setPrivateKey(PrivateKey), setPublicKey(PublicKey), setEncryptionKeyBytes(byte[])
   - Add getters: getPrivateKey(), getPublicKey(), getEncryptionKeyBytes()
   - Depends on: Step 1
   - Enables: Step 3

### Phase 3: JwtTokenService (Step 3)

3. **JwtTokenService.java** — Replace signing and add encrypt/decrypt layer.
   - Constructor:
     - Remove: SecretKey signingKey field and Keys.hmacShaKeyFor() init
     - Add: PrivateKey privateKey from jwtProperties.getPrivateKey()
     - Add: PublicKey publicKey from jwtProperties.getPublicKey()
     - Add: byte[] encryptionKey from jwtProperties.getEncryptionKeyBytes()
     - Add: ObjectMapper objectMapper injected via constructor parameter
   - buildToken():
     - Serialize claims map to JSON string via objectMapper.writeValueAsString()
     - Encrypt: generate 12-byte IV via SecureRandom, init Cipher(AES/GCM/NoPadding) with GCMParameterSpec(128, iv), encrypt, prepend IV to ciphertext bytes, Base64-encode concatenation
     - Replace .claims(claims) with .claim("enc", base64Ciphertext)
     - Replace .signWith(signingKey) with .signWith(privateKey, SignatureAlgorithm.RS256)
     - Keep .subject(subject), .issuedAt(now), .expiration(expiration) unchanged
   - parseToken():
     - Replace .verifyWith(signingKey) with .verifyWith(publicKey)
     - After parseSignedClaims().getPayload(): extract "enc" claim string
     - Base64-decode to bytes; first 12 bytes = IV, remainder = ciphertext+tag
     - Decrypt via Cipher(AES/GCM/NoPadding) with GCMParameterSpec(128, iv)
     - Deserialize JSON to Map<String, Object> via objectMapper
     - Reconstruct Claims: merge decrypted map with sub/iat/exp from original Claims
     - Existing four catch blocks remain unchanged in structure and mapping
     - Add catch for decryption failures (AEADBadTagException, general decrypt-path Exception) → InvalidTokenException.Reason.MALFORMED
   - validateToken(): no changes
   - Claim constants: unchanged
   - Depends on: Step 2
   - Enables: Step 4

### Phase 4: Tests (Step 4)

4. **JwtTokenServiceTest.java** — Update setup, adapt secondary instances, add two new tests.
   - @BeforeEach setUp():
     - Remove: TEST_SECRET constant and jwtProperties.setSecret(TEST_SECRET)
     - Add: KeyPairGenerator("RSA").initialize(2048) to produce RSA keypair
     - Add: SecureRandom to produce 32 bytes for AES key
     - Replace: jwtProperties.setPrivateKey(), setPublicKey(), setEncryptionKeyBytes()
   - Three inline secondary JwtProperties constructions:
     - Expired-token test (line 182): use SAME keypair + AES key, only change expiry
     - Invalid-signature test (line 209): generate SECOND independent RSA keypair; same AES key. Token signed with keypair-2 fails verification against keypair-1. Assertion stays INVALID_SIGNATURE.
     - Expired-via-isTokenExpired test (line 307): same as expired-token test treatment
   - All 12 existing test method bodies and assertions: untouched
   - New test — Payload opacity:
     - Generate access token
     - Split on ".", take middle segment, Base64-decode
     - Assert parsing as JSON throws exception (encrypted binary, not readable JSON)
   - New test — IV uniqueness:
     - Generate 100 access tokens with identical claims
     - Extract enc claim or payload from each
     - Assert all 100 are distinct
   - Depends on: Steps 1-3
   - Enables: Steps 5-7

### Phase 5: Verification (Steps 5-7)

5. **Compile check**: ./mvnw compile -DskipTests
   - Depends on: Steps 1-3
   - Confirms: no compilation errors

6. **Unit tests**: ./mvnw test -pl backend -Dtest=JwtTokenServiceTest
   - Depends on: Step 4
   - Confirms: all 14 tests pass (12 existing + 2 new)

7. **Full test suite**: ./mvnw test
   - Depends on: Step 6
   - Confirms: no regressions in AuthControllerTest, SecurityIntegrationTest, or any other test that exercises the token path

## Verification Checkpoints

| After Step | Checkpoint |
|------------|------------|
| 1 | application.properties has no jwt.secret, has three new jwt.* lines |
| 2 | JwtProperties compiles; getPrivateKey/getPublicKey/getEncryptionKeyBytes exist; secret is gone |
| 3 | JwtTokenService compiles; buildToken uses RS256 + encrypt; parseToken uses publicKey + decrypt |
| 4 | All 14 test methods compile |
| 5 | mvnw compile -DskipTests exits 0 |
| 6 | 14/14 JwtTokenServiceTest tests pass |
| 7 | Full suite passes |

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| IV reuse → GCM catastrophic failure | 3 | Fresh SecureRandom per buildToken call; IV uniqueness test in Step 4 |
| JJWT cannot check exp on encrypted token | 3 | sub/iat/exp stay outside enc claim |
| @PostConstruct fires in test context | 2 | Tests use new JwtProperties(); @PostConstruct only fires under Spring |
| Secondary test instances missing key setup | 4 | All three inline constructions updated; invalid-signature test uses independent keypair |
| Decryption error swallowed silently | 3 | AEADBadTagException maps to MALFORMED; token rejected |
| Production missing new env vars | 1 | Empty defaults cause @PostConstruct blank-check → IllegalStateException at startup |
| AES key wrong length in production | 2 | @PostConstruct validates exactly 32 bytes; fails fast |

## Rollback Strategy

- Hard cutover by design. Old HS256 tokens fail INVALID_SIGNATURE. Users re-login once.
- Safe stopping points:
  - After Step 1 only: app won't start (JwtProperties secret binding broken). Revert Step 1.
  - After Step 2 only: JwtTokenService won't compile (still references getSecret). Revert Steps 1-2 together.
  - After Steps 1-3: fully functional. Revert all four files atomically if tests fail.
- Rollback: git checkout the four modified files.
