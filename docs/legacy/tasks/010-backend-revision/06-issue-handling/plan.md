# Security Issues Execution Plan

## Execution Overview

Remediate 6 security issues in priority order:
1. **Configuration first** - Add trusted proxy property before code depends on it
2. **Create shared utility** - Extract `ClientIpResolver` to avoid duplication
3. **Fix critical/high issues** - Rate limit bypass and JSON injection
4. **Harden medium issues** - HSTS, CORS, OAuth validation
5. **Optional UX improvement** - SameSite cookie change
6. **Tests throughout** - Write unit tests for each new/modified component

## Dependency Analysis

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `application.properties` | Medium | None | All @Value injected components |
| `ClientIpResolver.java` (NEW) | Medium | application.properties | AuthController, PlannerController |
| `AuthController.java` | Medium | ClientIpResolver | Auth endpoints, RateLimitConfig |
| `PlannerController.java` | Medium | ClientIpResolver | Planner endpoints, RateLimitConfig |
| `JwtAuthenticationFilter.java` | HIGH | Jackson ObjectMapper | ALL authenticated requests |
| `SecurityConfig.java` | HIGH | None | ALL HTTP requests |
| `CorsConfig.java` | HIGH | None | ALL cross-origin requests |
| `OAuthCallbackRequest.java` | Low | None | GoogleCallback endpoint only |
| `JwtService.java` | HIGH | None | All cookie operations |

### Ripple Effect Map

- If `ClientIpResolver` logic wrong → Rate limiting fails for all endpoints
- If `JwtAuthenticationFilter.writeErrorResponse()` breaks → ALL auth error responses fail
- If `SecurityConfig` HSTS wrong → Browser security warnings
- If `CorsConfig` too restrictive → Frontend API calls blocked

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| `JwtAuthenticationFilter.java` | Broken error responses | Unit test with special chars before deploy |
| `SecurityConfig.java` | Misconfigured HSTS | Use standard max-age, test in dev first |
| `CorsConfig.java` | Wrong headers break frontend | Document frontend headers, test all API calls |

## Execution Order

### Phase 1: Configuration

1. **`application.properties`**: Add `security.trusted-proxy-ips` property
   - Depends on: none
   - Enables: F1 (rate limit fix)
   - Value: `${TRUSTED_PROXY_IPS:127.0.0.1}`

### Phase 2: Shared Utility

2. **`ClientIpResolver.java` (NEW)**: Create IP resolution utility
   - Depends on: Step 1
   - Enables: F1 in both controllers
   - Pattern: Follow `ViewerHashUtil.java` static utility pattern

3. **`ClientIpResolverTest.java` (NEW)**: Unit tests for IP resolution
   - Depends on: Step 2
   - Tests: Trusted proxy, untrusted source, multiple IPs, edge cases

### Phase 3: Critical Security Fixes

4. **`AuthController.java`**: Replace `getClientIp()` with `ClientIpResolver`
   - Depends on: Step 2
   - Enables: F1 (rate limit bypass prevented)

5. **`PlannerController.java`**: Replace `getClientIp()` with `ClientIpResolver`
   - Depends on: Step 2
   - Enables: F1 (rate limit bypass prevented)

6. **`JwtAuthenticationFilter.java`**: Use ObjectMapper for JSON error responses
   - Depends on: none (independent)
   - Enables: F2 (JSON injection prevented)

7. **`JwtAuthenticationFilterTest.java`**: Add JSON injection tests
   - Depends on: Step 6
   - Tests: Special chars produce valid JSON

### Phase 4: Security Hardening

8. **`SecurityConfig.java`**: Add HSTS header
   - Depends on: none
   - Enables: F3 (HSTS header present)

9. **`CorsConfig.java`**: Replace wildcard with explicit header list
   - Depends on: none
   - Enables: F4 (CORS headers restricted)
   - Headers: `Content-Type`, `Authorization`, `X-Device-Id`, `Cache-Control`

10. **`OAuthCallbackRequest.java`**: Add @Size validation
    - Depends on: none
    - Enables: F5 (OAuth code validated)

### Phase 5: Optional UX Improvement

11. **`JwtService.java`**: Change SameSite to Lax (OPTIONAL)
    - Depends on: none
    - Enables: F6 (better UX from external links)

### Phase 6: Verification

12. **Manual verification**: Test all endpoints
    - Depends on: Steps 1-11
    - Verify: All features work, no regressions

## Verification Checkpoints

| After Step | Verify |
|------------|--------|
| 5 | F1: Rate limit bypass prevented (manual test with spoofed header) |
| 7 | F2: JSON injection prevented (test with `"` in error message) |
| 8 | F3: HSTS header in responses (curl -I) |
| 9 | F4: Frontend still works (test API calls) |
| 10 | F5: Oversized OAuth code rejected |
| 11 | F6: External links work while logged in |

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| Deployment NOT behind nginx | 2 | Falls back to getRemoteAddr() when untrusted |
| Multiple proxies in chain | 2 | Parse X-Forwarded-For correctly |
| CORS too restrictive | 9 | Grep frontend for custom headers first |

## Rollback Strategy

- **Steps 1-5**: Git revert. Rate limiting falls back to vulnerable behavior.
- **Steps 6-7**: Git revert. Error responses fall back to string concat.
- **Steps 8-11**: Git revert. Hardening removed, no user impact.

**Safe stopping points**: After Step 5 (critical fix), After Step 10 (all mandatory fixes)
