# Auth Revision Research

## Clarifications Resolved
- Token blacklist: In-memory (ConcurrentHashMap + TTL) - single server, no Redis
- Spring Boot version: 3.0 (not 4.0)

## Spec-to-Code Mapping

| Requirement | Current State | Target |
|-------------|---------------|--------|
| Interface Segregation | JwtService monolith | TokenGenerator + TokenValidator interfaces |
| Strategy Pattern | GoogleOAuthService hardcoded | OAuthProvider interface + Registry |
| Token Blacklist | Missing | TokenBlacklistService (ConcurrentHashMap) |
| Cookie DRY | 3x duplication | CookieUtils utility class |
| Config Validation | @Value (no validation) | JwtProperties (@ConfigurationProperties + @Validated) |
| HTTP Client Beans | `new RestTemplate()` inline | HttpClientConfig bean definitions |
| Facade Pattern | Logic in controller | AuthenticationFacade |
| Refresh Token Rotation | Not implemented | Add blacklist on refresh |
| Token Revocation Check | Missing in filter | Add blacklist check |
| Rate Limiting | Exists but not on auth | Apply to AuthController |
| UserNotFoundException | Exists, unused | Use in UserService.findById() |

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| JwtProperties.java | RateLimitConfig.java | @ConfigurationProperties, validation |
| CookieUtils.java | AuthController.java | setCookie, clearCookie, getTokenFromCookie (lines 139-168) |
| HttpClientConfig.java | SecurityConfig.java | @Configuration, @Bean |
| TokenBlacklistService.java | RateLimitConfig.java | ConcurrentHashMap pattern |
| TokenValidator.java | JwtService.java | validateToken, getUserIdFromToken |
| TokenGenerator.java | JwtService.java | generateAccessToken, generateRefreshToken |
| TokenClaims.java | JwtService.java | Claims extraction (userId, email, type) |
| JwtTokenService.java | JwtService.java | Full implementation |
| OAuthProvider.java | - | New strategy interface |
| GoogleOAuthProvider.java | GoogleOAuthService.java | exchangeCodeForToken, getUserInfo |
| OAuthProviderRegistry.java | RateLimitConfig.java | Map-based registry |
| AuthenticationFacade.java | AuthController.java | OAuth callback orchestration (lines 37-66) |

## Existing Utilities

| Category | Location | Found | Action |
|----------|----------|-------|--------|
| Cookie Utils | AuthController, JwtAuthFilter | Duplicated 3x | Extract to CookieUtils |
| Token Utils | JwtService | Monolithic | Split to interfaces |
| Constants | N/A | None | Use @ConfigurationProperties |
| Exception | UserNotFoundException | Exists unused | Wire to UserService |
| Rate Limit | RateLimitConfig | Pattern exists | Apply to auth endpoints |

## Gap Analysis

**Missing (Create):**
- TokenBlacklistService, TokenClaims, TokenValidator, TokenGenerator
- OAuthProvider, OAuthProviderRegistry, GoogleOAuthProvider
- CookieUtils, JwtProperties, HttpClientConfig, AuthenticationFacade
- TokenRevokedException, InvalidTokenException

**Modify:**
- JwtService → split into interfaces
- GoogleOAuthService → migrate to strategy
- AuthController → use facade, add rate limiting
- JwtAuthenticationFilter → add blacklist check
- UserService.findById() → use UserNotFoundException
- GlobalExceptionHandler → add auth exception handlers

**Reuse:**
- RateLimitConfig pattern (ConcurrentHashMap, @ConfigurationProperties)
- UserNotFoundException exception class
- GlobalExceptionHandler structure

## Testing Requirements

**Manual Verification:**
- OAuth flow: /google/callback, /refresh, /me, /logout
- Token revocation: blacklisted token returns 401
- Config validation: short JWT secret fails startup
- Rate limiting: auth endpoints enforce limits

**Unit Tests:**
- TokenBlacklistService: TTL expiration, concurrent access
- TokenValidator: invalid signature, expired token, malformed
- TokenGenerator: claim inclusion, expiration accuracy
- CookieUtils: security attributes (HttpOnly, Secure, SameSite)
- OAuthProviderRegistry: lookup, unknown provider error

**Integration Tests:**
- AuthenticationFacade: full OAuth → token flow
- JwtAuthenticationFilter: blacklist integration
- AuthController: endpoints with refactored services
- GlobalExceptionHandler: new exception formatting

**Edge Cases:**
- Expired access + valid refresh → refresh succeeds
- Both expired → 401, cookies cleared
- Concurrent refresh → one success
- Invalid signature → immediate 401
- Blacklisted token → 401 TOKEN_REVOKED
- Rate limit exceeded → 429

## Technical Constraints

| Constraint | Detail |
|------------|--------|
| Storage | In-memory ConcurrentHashMap (single server) |
| TTL | System.currentTimeMillis() + token expiry |
| JWT Library | jjwt 0.12+ (existing) |
| Spring | Boot 3.0, supports @ConfigurationProperties validation |
| Validation | Jakarta Validation (jakarta.validation) |
| Rate Limiting | Bucket4j (existing via RateLimitConfig) |
| Backward Compat | Existing token format must parse correctly |
| Error Format | GlobalExceptionHandler.ErrorResponse (code, message) |

## Implementation Order

1. **Phase 1** (30min): CookieUtils, JwtProperties, HttpClientConfig
2. **Phase 2** (1hr): Token interfaces, JwtTokenService, TokenBlacklistService
3. **Phase 3** (1hr): OAuth interfaces, GoogleOAuthProvider, Registry
4. **Phase 4** (30min): AuthenticationFacade, controller slimdown, UserService fix
5. **Phase 5** (30min): Testing and verification
