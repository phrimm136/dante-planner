# Auth Revision Execution Plan

## Planning Gaps

None identified. Research has mapped all requirements.

## Execution Overview

Refactor authentication from monolithic to SOLID-compliant architecture in 5 phases:
1. Extract shared utilities (CookieUtils) and add configuration validation (JwtProperties)
2. Split token handling into interfaces with blacklist support
3. Implement OAuth Strategy pattern for provider abstraction
4. Create AuthenticationFacade for flow orchestration
5. Comprehensive testing with unit, integration, and manual verification

**Strategy**: Bottom-up implementation (utilities first, then services, then orchestration)

## Execution Order

### Phase 1: Utilities and Configuration (30 min)

1. **CookieUtils.java** (NEW): Extract cookie operations
   - Depends on: none
   - Enables: F4 (Cookie Utils DRY)
   - Pattern: AuthController.java lines 139-168

2. **JwtProperties.java** (NEW): @ConfigurationProperties with validation
   - Depends on: none
   - Enables: F5 (Config Validation)
   - Pattern: RateLimitConfig.java

3. **HttpClientConfig.java** (NEW): RestTemplate, ObjectMapper beans
   - Depends on: none
   - Enables: DI improvements for OAuth

### Phase 2: Token Service Layer (1 hour)

4. **TokenClaims.java** (NEW): Value object for parsed claims
   - Depends on: none
   - Enables: F1

5. **TokenGenerator.java** (NEW): Interface for token creation
   - Depends on: none
   - Enables: F1

6. **TokenValidator.java** (NEW): Interface for token validation
   - Depends on: Step 4
   - Enables: F1

7. **TokenBlacklistService.java** (NEW): In-memory token revocation
   - Depends on: none
   - Enables: F2, S1

8. **JwtTokenService.java** (NEW): Implements TokenGenerator + TokenValidator
   - Depends on: Steps 2, 4, 5, 6, 7
   - Enables: F1, F7

9. **TokenRevokedException.java** (NEW): Exception for blacklisted tokens
   - Depends on: none
   - Enables: E2

10. **InvalidTokenException.java** (NEW): Exception for invalid/expired tokens
    - Depends on: none
    - Enables: E2

11. **JwtAuthenticationFilter.java** (MODIFY): Use TokenValidator, CookieUtils, blacklist
    - Depends on: Steps 1, 6, 7, 9, 10
    - Enables: S2

12. **GlobalExceptionHandler.java** (MODIFY): Add auth exception handlers
    - Depends on: Steps 9, 10
    - Enables: E2

### Phase 3: OAuth Strategy Pattern (1 hour)

13. **OAuthTokens.java** (NEW): Value object for OAuth tokens
    - Depends on: none
    - Enables: F3

14. **OAuthUserInfo.java** (NEW): Value object for user info
    - Depends on: none
    - Enables: F3

15. **OAuthProvider.java** (NEW): Strategy interface
    - Depends on: Steps 13, 14
    - Enables: F3

16. **GoogleOAuthProvider.java** (NEW): Google implementation
    - Depends on: Steps 3, 13, 14, 15
    - Enables: F3

17. **OAuthProviderRegistry.java** (NEW): Strategy registry
    - Depends on: Step 15
    - Enables: F3

### Phase 4: Facade and Controller (30 min)

18. **AuthenticationFacade.java** (NEW): Flow orchestration
    - Depends on: Steps 5, 6, 8, 17
    - Enables: F6

19. **AuthController.java** (MODIFY): Use facade, CookieUtils, rate limiting
    - Depends on: Steps 1, 18
    - Enables: F6, F8

20. **UserService.java** (MODIFY): Use UserNotFoundException
    - Depends on: none
    - Enables: E1

21. **application.properties** (MODIFY): Add auth rate limit config
    - Depends on: none
    - Enables: F8

22. **RateLimitConfig.java** (MODIFY): Add auth bucket config
    - Depends on: Step 21
    - Enables: F8

### Phase 5: Cleanup and Tests (30 min)

23. **JwtService.java** (DELETE): Replaced by JwtTokenService
    - Depends on: Steps 8, 11, 19

24. **GoogleOAuthService.java** (DELETE): Replaced by GoogleOAuthProvider
    - Depends on: Steps 16, 18

25. **TokenBlacklistServiceTest.java** (NEW): Unit tests
    - Tests: add, isBlacklisted, TTL expiration

26. **JwtTokenServiceTest.java** (NEW): Unit tests
    - Tests: generation, validation, expiration

27. **CookieUtilsTest.java** (NEW): Unit tests
    - Tests: set, get, clear, security attributes

28. **OAuthProviderRegistryTest.java** (NEW): Unit tests
    - Tests: lookup, unknown provider error

29. **AuthenticationFacadeTest.java** (NEW): Unit tests
    - Tests: OAuth flow, refresh flow, revocation

30. **AuthControllerIntegrationTest.java** (NEW): Integration tests
    - Tests: /google/callback, /refresh, /logout, /me, rate limiting

## Verification Checkpoints

| After Step | Verify | Method |
|------------|--------|--------|
| 3 | F5 Config Validation | Start app with short JWT secret, expect failure |
| 11 | S2 Blacklist check | Unit test: blacklisted token returns 401 |
| 12 | E2 401 responses | Unit test: exceptions return structured error |
| 17 | F3 OAuth Strategy | Unit test: registry returns correct provider |
| 19 | F8 Rate Limiting | Integration test: 429 after rapid requests |
| 22 | All features | Full manual OAuth flow test |
| 24 | No regressions | Run all existing tests |

## Rollback Strategy

**Safe Stopping Points:**
- After Phase 1: Additive changes only, no breaking changes
- After Phase 2: JwtTokenService can coexist with JwtService
- After Phase 3: OAuth providers additive until controller migrated

**If Step N Fails:**
- Steps 1-3: Delete new file, no impact
- Steps 4-10: Delete token package, no impact
- Step 11: Revert filter to use JwtService
- Steps 13-17: Delete oauth package, no impact
- Step 18: Delete facade, no impact
- Step 19: Revert controller to use direct services
- Steps 23-24: Do not delete until all tests pass

**Critical Checkpoints:**
- Before Step 11: Ensure JwtTokenService tests pass
- Before Step 19: Ensure AuthenticationFacade tests pass
- Before Step 23-24: Run full test suite
