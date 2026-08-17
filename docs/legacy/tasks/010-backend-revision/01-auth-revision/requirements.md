# Task: Backend Authentication Architecture Revision (8/10 → 10/10)

## Description

Refactor the backend authentication system to achieve perfect SOLID compliance and industry-standard security practices. The current implementation scores 8/10 on architecture but has critical gaps that must be addressed.

### Architectural Improvements Required

**Interface Segregation (SOLID-I):**
- Split `JwtService` (currently handling generation + validation + parsing) into separate interfaces:
  - `TokenGenerator`: Responsible for creating access and refresh tokens
  - `TokenValidator`: Responsible for validating tokens and extracting claims
- Create `TokenClaims` value object to encapsulate parsed token data

**Strategy Pattern for OAuth Providers (SOLID-O, SOLID-L):**
- Abstract OAuth provider logic into `OAuthProvider` interface
- Implement `GoogleOAuthProvider` and `AppleOAuthProvider` as concrete strategies
- Create `OAuthProviderRegistry` to manage and select providers dynamically
- Convert current hardcoded `/google/callback` and `/apple/callback` into unified `/{provider}/callback` endpoint

**Dependency Injection Improvements (SOLID-D):**
- Remove direct instantiation of `RestTemplate` and `ObjectMapper` in `GoogleOAuthService`
- Define beans in configuration and inject via constructor
- All external dependencies must be injected, not created in-class

**DRY Violation Fix:**
- Extract duplicate cookie handling logic from `AuthController` and `JwtAuthenticationFilter` into shared `CookieUtils` utility class
- Cookie operations: get, set, clear with consistent security attributes

**Configuration Validation (Fail-Fast):**
- Replace `@Value` injection with `@ConfigurationProperties` + `@Validated`
- Validate JWT secret length (minimum 32 characters for 256-bit security) at startup
- Fail application startup if critical security configuration is missing or invalid

**Facade Pattern:**
- Create `AuthenticationFacade` to orchestrate complex authentication flows
- Move OAuth code exchange → user lookup/creation → token generation logic from controller to facade
- Controller should only handle HTTP concerns (request/response, cookies)

### Security Improvements Required (CRITICAL)

**Token Revocation Mechanism:**
- Implement token blacklist service (in-memory or Redis-backed)
- Check blacklist in `JwtAuthenticationFilter` before accepting tokens
- Add token to blacklist on logout
- Blacklist entries should expire when token would naturally expire

**Refresh Token Rotation:**
- Generate new refresh token on each refresh operation
- Invalidate old refresh token when new one is issued
- Track token version/ID in claims for revocation

**Rate Limiting on Auth Endpoints:**
- Add rate limiting to `/api/auth/*` endpoints
- Suggested: 5 requests per minute per IP for callback endpoints

### Error Handling Improvements

- Replace `RuntimeException("User not found")` in `UserService.findById()` with existing `UserNotFoundException`
- Ensure all 401 responses include structured error body with error code
- Consistent error response format across all auth endpoints

## Research

Before implementing, investigate:

1. **Existing Patterns:**
   - Read `backend/CLAUDE.md` for backend development guidelines
   - Read `.claude/skills/backend-dev-guidelines/resources/security-guide.md` for security patterns
   - Check existing exception handling in `GlobalExceptionHandler.java`

2. **Token Blacklist Options:**
   - Evaluate in-memory (ConcurrentHashMap with TTL) vs Redis
   - Consider existing rate limiting implementation (`RateLimitConfig.java`) for pattern reference

3. **Spring Security Integration:**
   - How `JwtAuthenticationFilter` integrates with security filter chain
   - Best practices for custom authentication filters

4. **Configuration Properties:**
   - Spring Boot `@ConfigurationProperties` validation patterns
   - Bean validation annotations for config classes

## Scope

Files to READ for context:
- `backend/src/main/java/org/danteplanner/backend/service/JwtService.java`
- `backend/src/main/java/org/danteplanner/backend/service/GoogleOAuthService.java`
- `backend/src/main/java/org/danteplanner/backend/controller/AuthController.java`
- `backend/src/main/java/org/danteplanner/backend/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/org/danteplanner/backend/service/UserService.java`
- `backend/src/main/java/org/danteplanner/backend/config/SecurityConfig.java`
- `backend/src/main/java/org/danteplanner/backend/config/RateLimitConfig.java` (for pattern reference)
- `backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/org/danteplanner/backend/exception/UserNotFoundException.java`

## Target Code Area

### New Files to Create

```
backend/src/main/java/org/danteplanner/backend/
├── config/
│   ├── HttpClientConfig.java        # RestTemplate, ObjectMapper beans
│   └── JwtProperties.java           # @ConfigurationProperties with validation
├── facade/
│   └── AuthenticationFacade.java    # Authentication flow orchestration
├── service/
│   ├── token/
│   │   ├── TokenGenerator.java      # Interface for token creation
│   │   ├── TokenValidator.java      # Interface for token validation
│   │   ├── TokenClaims.java         # Value object for parsed claims
│   │   ├── JwtTokenService.java     # Implements both interfaces
│   │   └── TokenBlacklistService.java  # Token revocation
│   └── oauth/
│       ├── OAuthProvider.java       # Strategy interface
│       ├── OAuthProviderRegistry.java  # Strategy registry
│       ├── OAuthTokens.java         # Value object for OAuth tokens
│       ├── OAuthUserInfo.java       # Value object for user info
│       └── GoogleOAuthProvider.java # Google implementation
└── util/
    └── CookieUtils.java             # Shared cookie operations
```

### Files to Modify

- `backend/src/main/java/org/danteplanner/backend/controller/AuthController.java` - Slim down, use facade
- `backend/src/main/java/org/danteplanner/backend/security/JwtAuthenticationFilter.java` - Use CookieUtils, TokenValidator, blacklist check
- `backend/src/main/java/org/danteplanner/backend/service/UserService.java` - Use UserNotFoundException
- `backend/src/main/java/org/danteplanner/backend/config/SecurityConfig.java` - Update filter references
- `backend/src/main/resources/application.properties` - Update property structure if needed

### Files to Delete (after migration)

- `backend/src/main/java/org/danteplanner/backend/service/JwtService.java` - Replaced by token package
- `backend/src/main/java/org/danteplanner/backend/service/GoogleOAuthService.java` - Replaced by oauth package

## Testing Guidelines

### Manual API Testing

1. **OAuth Login Flow:**
   ```bash
   # 1. Initiate Google OAuth from frontend
   # 2. Complete Google authentication
   # 3. POST to /api/auth/google/callback with code + code_verifier
   curl -X POST http://localhost:8080/api/auth/google/callback \
     -H "Content-Type: application/json" \
     -d '{"code": "auth_code", "codeVerifier": "verifier"}'
   # 4. Verify cookies set in response (accessToken, refreshToken)
   ```

2. **Token Refresh:**
   ```bash
   # 1. With valid refreshToken cookie
   curl -X POST http://localhost:8080/api/auth/refresh \
     --cookie "refreshToken=<token>"
   # 2. Verify new tokens issued
   # 3. Verify old refresh token is invalidated (if rotation implemented)
   ```

3. **Logout with Token Revocation:**
   ```bash
   # 1. Login and get tokens
   # 2. Logout
   curl -X POST http://localhost:8080/api/auth/logout \
     --cookie "accessToken=<token>; refreshToken=<token>"
   # 3. Try to use old access token
   curl http://localhost:8080/api/auth/me \
     --cookie "accessToken=<old_token>"
   # 4. Verify 401 response (token revoked)
   ```

4. **Configuration Validation:**
   ```bash
   # 1. Set JWT_SECRET to short value (< 32 chars)
   # 2. Start application
   # 3. Verify startup fails with clear error message
   ```

### Automated Functional Verification

- [ ] Token Generation: Access token contains userId, email, type="access"
- [ ] Token Generation: Refresh token contains userId, type="refresh"
- [ ] Token Validation: Expired tokens return 401
- [ ] Token Validation: Malformed tokens return 401
- [ ] Token Revocation: Blacklisted tokens return 401
- [ ] Token Refresh: Issues new access token with valid refresh token
- [ ] Token Refresh: Issues new refresh token (rotation)
- [ ] OAuth Strategy: `/google/callback` routes to GoogleOAuthProvider
- [ ] OAuth Strategy: Unknown provider returns 400 with error message
- [ ] Cookie Utils: All cookies set with HttpOnly, Secure, SameSite=Strict
- [ ] Config Validation: Missing JWT secret fails startup
- [ ] Config Validation: Short JWT secret (< 32 chars) fails startup
- [ ] Error Handling: UserNotFoundException returns 404 (not 500)
- [ ] Error Handling: All 401 responses include error body

### Edge Cases

- [ ] Expired access token + valid refresh token: Refresh succeeds
- [ ] Expired access token + expired refresh token: Returns 401, clears cookies
- [ ] Concurrent token refresh: Only one request succeeds, others get new token or 401
- [ ] Token with invalid signature: Returns 401 immediately
- [ ] Token from blacklist: Returns 401 with "TOKEN_REVOKED" code
- [ ] Rate limit exceeded on auth endpoints: Returns 429
- [ ] OAuth code reuse: Returns error (codes are single-use)
- [ ] OAuth with invalid code_verifier: Returns 401

### Integration Points

- [ ] JwtAuthenticationFilter: Works with new TokenValidator interface
- [ ] SecurityConfig: Filter chain properly configured with new components
- [ ] PlannerController: Authenticated endpoints still work after refactor
- [ ] GlobalExceptionHandler: Catches and formats new auth exceptions
- [ ] Rate limiting: Auth endpoints respect rate limits

## Implementation Phases

### Phase 1: Utilities and Configuration (30 min)
- Create `CookieUtils.java`
- Create `JwtProperties.java` with validation
- Create `HttpClientConfig.java`

### Phase 2: Token Service Refactor (1 hour)
- Create token interfaces and value objects
- Create `JwtTokenService` implementation
- Create `TokenBlacklistService`
- Update `JwtAuthenticationFilter`

### Phase 3: OAuth Strategy Pattern (1 hour)
- Create OAuth interfaces and value objects
- Create `OAuthProviderRegistry`
- Migrate `GoogleOAuthService` to `GoogleOAuthProvider`
- Update `AuthController` to use dynamic routing

### Phase 4: Facade and Cleanup (30 min)
- Create `AuthenticationFacade`
- Slim down `AuthController`
- Fix `UserService` exception handling
- Delete old service files

### Phase 5: Testing and Verification (30 min)
- Run all existing tests
- Manual API testing
- Verify all edge cases
