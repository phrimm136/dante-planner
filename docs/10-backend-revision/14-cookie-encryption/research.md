# Research: JWT Token Hardening (RS256 + AES-GCM)

## Clarifications Resolved

- Public key delivery: file path (jwt.public-key-path), symmetric with private key pattern
- Env var names: JWT_PRIVATE_KEY_PATH, JWT_PUBLIC_KEY_PATH, JWT_ENCRYPTION_KEY
- AES key in property: Base64-encoded string; decoded to byte[] at startup
- IV + ciphertext layout: raw bytes [12-byte IV][ciphertext + 16-byte GCM tag], then Base64-encoded into JWT claim
- GCM auth tag: Java Cipher appends it automatically to ciphertext output — no manual handling
- RSA key format: PKCS#8 private (openssl genrsa output), X.509 public (openssl rsa -pubout output)

## Spec-to-Code Mapping

- Remove jwt.secret from application.properties
- Add jwt.private-key-path, jwt.public-key-path, jwt.encryption-key to application.properties
- JwtProperties: remove secret field + placeholder validation
- JwtProperties: add privateKeyPath, publicKeyPath, encryptionKey string fields
- JwtProperties: @PostConstruct loads PEM files via KeyFactory, decodes Base64 encryption key, validates 32 bytes
- JwtProperties: expose getPrivateKey() → PrivateKey, getPublicKey() → PublicKey, getEncryptionKeyBytes() → byte[]
- JwtProperties: for test injection, add setPrivateKey(PrivateKey), setPublicKey(PublicKey) setters bypassing file load
- JwtTokenService constructor: replace SecretKey with PrivateKey + PublicKey from JwtProperties
- JwtTokenService.buildToken(): serialize claims Map → JSON bytes → AES-GCM encrypt (IV + ciphertext) → Base64 → single JWT claim "enc" → sign with RS256
- JwtTokenService.parseToken(): verify RS256 with public key → extract "enc" claim → Base64 decode → split IV at byte 12 → AES-GCM decrypt → deserialize JSON → reconstruct Claims
- JwtTokenServiceTest: @BeforeEach generates RSA-2048 KeyPair + 32-byte AES key programmatically, injects via setters
- JwtTokenServiceTest: all existing test assertions unchanged — encrypt/decrypt is transparent
- JwtTokenServiceTest: add payload opacity test + IV uniqueness test

## Spec-to-Pattern Mapping

- Property naming: follows ${ENV_VAR:default} pattern already used by jwt.secret, oauth.*, cookie.*
- Fail-fast validation: follows JwtProperties.validateSecretNotPlaceholder() → IllegalStateException pattern
- Key loading in @PostConstruct: no existing PEM loader in codebase — this establishes the pattern
- Test key injection: follows existing JwtTokenServiceTest setUp() — manual JwtProperties construction via setters, no Spring context
- Exception mapping in parseToken: unchanged — ExpiredJwtException, SignatureException, MalformedJwtException, JwtException all map to existing InvalidTokenException.Reason enum

## Pattern Enforcement

| Modified File | MUST Read First | Pattern to Copy |
|---|---|---|
| JwtProperties.java | Current JwtProperties.java (validation, @ConfigurationProperties, getters) | @PostConstruct validation structure, field + getter layout |
| JwtTokenService.java | Current JwtTokenService.java (buildToken, parseToken, constructor) | Claims map construction, exception handling in parseToken, method signatures |
| application.properties | Current application.properties lines 27-30 | Property naming convention, env var fallback syntax |
| JwtTokenServiceTest.java | Current JwtTokenServiceTest.java lines 19-35 | @BeforeEach setup pattern, nested class structure |

## Pattern Copy Deep Analysis

**Reference: JwtTokenService.java**
- Lines: 189
- Dependencies: io.jsonwebtoken.* (Jwts, Claims, Keys, SignatureAlgorithm), JwtProperties, UserRole, InvalidTokenException
- Public contracts: TokenGenerator (2 methods), TokenValidator (3 methods), getEmailFromToken, getTokenType
- Internal: buildToken(claims, subject, expiryMs), parseToken(token) → Claims
- Claim constants: CLAIM_USER_ID, CLAIM_EMAIL, CLAIM_TYPE, CLAIM_ROLE (static final String)

**Cross-Reference Validation:**

| Layer | Reference Contract | New Contract | Match? |
|---|---|---|---|
| TokenGenerator interface | generateAccessToken(Long, String, UserRole) → String | Same signature | Yes |
| TokenValidator interface | validateToken(String) → TokenClaims | Same signature | Yes |
| Claims map keys | userId, email, type, role | Same keys, now serialized to JSON before encrypt | Yes |
| Exception mapping | 4 catch blocks → InvalidTokenException.Reason | Same mapping; signature failure now RSA-based but same exception | Yes |
| JWT structure | header.payload.signature (3 segments) | Same 3 segments; payload is now opaque Base64 of encrypted blob | Yes |

**New dependency (no external lib needed):**
- javax.crypto.Cipher (AES/GCM/NoPadding) — standard Java
- java.security.KeyFactory, KeyPair, PrivateKey, PublicKey — standard Java
- java.security.SecureRandom — standard Java
- com.fasterxml.jackson.databind.ObjectMapper — already on classpath (used by Spring Boot)

## Gap Analysis

- Missing: PEM file loading logic (new, in JwtProperties @PostConstruct)
- Missing: AES-GCM encrypt/decrypt methods (new, private helpers in JwtTokenService)
- Missing: JSON serialization of claims before encrypt (ObjectMapper, already available)
- Needs modification: JwtProperties (field swap + new @PostConstruct)
- Needs modification: JwtTokenService (constructor + buildToken + parseToken)
- Needs modification: application.properties (property swap)
- Needs modification: JwtTokenServiceTest (setUp + 2 new tests)
- Can reuse: All interfaces, TokenClaims record, exception types, claim constants, all filter/blacklist code

## Testing Requirements

**Existing tests — no assertion changes needed:**
- All 12 existing test methods pass transparently — encryption is internal to generate+validate
- The "different secret" signature test becomes "different key pair" — same assertion (INVALID_SIGNATURE)
- Short-expiry tests unaffected — expiry is checked by JJWT after signature verification

**New tests to add:**
- Payload opacity: generate token, Base64-decode middle segment, assert it is NOT parseable as JSON
- IV uniqueness: generate 100 tokens with identical claims, extract encrypted payloads, assert all distinct
- (Optional) Tampered ciphertext: flip a byte in the encrypted payload, assert decryption fails with MALFORMED

**Test infrastructure:**
- KeyPairGenerator.getInstance("RSA").initialize(2048) for RSA pair
- SecureRandom 32 bytes for AES key
- Inject via setters on JwtProperties — @PostConstruct file-loading logic must not run in tests
- JwtProperties needs setter overloads: setPrivateKey(PrivateKey), setPublicKey(PublicKey) that skip file loading
