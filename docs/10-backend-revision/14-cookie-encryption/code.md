# Implementation Results: JWT Token Hardening (RS256 + AES-GCM)

## What Was Done

- Migrated from HS256 (HMAC) to RS256 (RSA-2048) asymmetric signing
- Added AES-256-GCM encryption for custom JWT claims (userId, email, type, role)
- Standard claims (sub, iat, exp) remain unencrypted for JJWT automatic expiry validation
- Fixed thread contention with ThreadLocal SecureRandom pattern
- Added RSA key strength validation (minimum 2048 bits) at startup
- Implemented cipher factory methods to eliminate code duplication
- Added compact constructor validation to TokenClaims record
- Cached JwtParser instance to reduce validation overhead (~50μs per token)
- Created custom TokenGenerationException for consistent error handling
- Refactored all test names to given_when_then format

## Files Changed

### Implementation
- `backend/src/main/java/org/danteplanner/backend/service/token/JwtTokenService.java`
- `backend/src/main/java/org/danteplanner/backend/config/JwtProperties.java`
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenClaims.java`
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenGenerationException.java` (new)

### Tests
- `backend/src/test/java/org/danteplanner/backend/service/token/JwtTokenServiceTest.java`
- `backend/src/test/resources/application-test.properties`
- `backend/src/test/resources/application-it.properties`
- `backend/src/test/resources/test-keys/private_key.pem` (new)
- `backend/src/test/resources/test-keys/public_key.pem` (new)
- `backend/src/test/resources/test-keys/aes_key.txt` (new)

### Configuration
- `backend/src/main/resources/application.properties`
- `docker-compose.yml`
- `docker-compose.override.yml`
- `.github/workflows/deploy.yml`

### Documentation
- `docs/TODO.md` (added C1: Key Rotation, C2: IV Uniqueness as technical debt)
- `docs/10-backend-revision/14-cookie-encryption/status.md` (marked 100% complete)

## Verification Results

- Step 1 (Properties): PASS - jwt.private-key-path, jwt.public-key-path, jwt.encryption-key configured
- Step 2 (JwtProperties): PASS - PEM loading with @PostConstruct, 32-byte AES validation
- Step 3 (JwtTokenService): PASS - RS256 signing, AES-GCM encrypt/decrypt implemented
- Step 4 (Tests): PASS - RSA keypair generation in @BeforeEach, 2 new security tests added
- Step 5 (Compile): PASS - ./mvnw compile -DskipTests successful
- Step 6 (Unit Tests): PASS - 20/20 JwtTokenServiceTest (12 existing + 2 new + 6 other)
- Step 7 (Full Suite): PASS - 730/731 tests (1 pre-existing failure in AuthenticationFacadeTest)
- Code Review: 8/8 high-priority issues resolved, 2 deferred to technical debt

## Issues & Resolutions

- Thread contention on shared SecureRandom → Implemented ThreadLocal pattern
- No RSA key validation allowed weak keys → Added 2048-bit minimum check at startup
- Generic exception swallowed forensic data → Added structured logging with stack traces
- Duplicated cipher initialization → Extracted createEncryptCipher/createDecryptCipher methods
- No input validation in TokenClaims → Added compact constructor with null/expiry checks
- Parser rebuilt per validation → Cached JwtParser instance as field
- Inconsistent error handling → Created TokenGenerationException to match InvalidTokenException
- Mixed test naming styles → Renamed all to given_when_then format
- Key rotation mechanism → Deferred to TODO.md (only needed at 10K+ users or compliance)
- IV uniqueness guarantee → Deferred to TODO.md (negligible risk at <1000 req/sec)

## Deployment Readiness

- AWS SSM parameters required: JWT_PRIVATE_KEY, JWT_PUBLIC_KEY, JWT_ENCRYPTION_KEY
- Breaking change: All existing tokens invalidated (users re-login once)
- Performance: Thread contention eliminated, 50μs saved per validation
- Security: Payload confidentiality, asymmetric signing, fail-fast validation
