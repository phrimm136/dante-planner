# Task: Backend Security Issues Documentation and Remediation

## Design Decisions (Clarified)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Deployment** | nginx reverse proxy | Configure nginx IP(s) as trusted proxies |
| **Token refresh IP validation** | Not implemented | Industry standard for consumer apps - IP changes (VPN, mobile) would hurt UX more than security gain. Existing HttpOnly + short-lived tokens are sufficient. |
| **Author info in public planners** | Show username (intentional) | Feature as designed, not a vulnerability |

## Description
Document backend security vulnerabilities discovered during architectural analysis and provide remediation guidance. The session identified:

1. **Critical: Rate Limit Bypass via X-Forwarded-For Spoofing**
   - The `getClientIp()` method in AuthController and PlannerController trusts the X-Forwarded-For header without validating the request came from a trusted proxy
   - Attackers can bypass rate limits (5 req/60s) by rotating the header value
   - Affects all rate-limited endpoints
   - **Fix**: Only trust X-Forwarded-For from nginx IP (configured in `application.properties`)

2. **High: JSON Injection in Error Responses**
   - JwtAuthenticationFilter uses string concatenation to build JSON error responses
   - Special characters in error messages could break JSON structure or enable XSS
   - **Fix**: Use Jackson ObjectMapper for proper JSON escaping

3. **Medium: Missing Security Headers**
   - HSTS header not configured
   - CORS allows wildcard headers
   - OAuth code parameter lacks length/format validation

Additionally, document the SameSite cookie configuration analysis:
- Current setting: `SameSite=Strict`
- Analysis conclusion: Safe to change to `Lax` because all GET endpoints are read-only
- Benefits: Better UX for users arriving via external links

## Intentionally Not Addressed

| Item | Reason |
|------|--------|
| Token refresh IP validation | Industry standard for consumer apps is to NOT validate IP on refresh. VPN/mobile users would be constantly logged out. Existing security (HttpOnly cookies, 15-min access tokens, HTTPS) is sufficient. |
| Author info in planner search | Intentional feature - users want attribution for their published planners |

## Research
- Review current `getClientIp()` implementation in both controllers
- Understand deployment topology (load balancer, reverse proxy setup)
- Study Spring's `ForwardedHeaderFilter` for proper proxy handling
- Research Bucket4j rate limiting patterns for user-based limiting
- Review Jackson ObjectMapper for safe JSON serialization
- Check OWASP guidelines for secure error handling

## Scope
Files to READ for context:
- `backend/src/main/java/org/danteplanner/backend/controller/AuthController.java`
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java`
- `backend/src/main/java/org/danteplanner/backend/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/org/danteplanner/backend/config/SecurityConfig.java`
- `backend/src/main/java/org/danteplanner/backend/config/RateLimitConfig.java`
- `backend/src/main/java/org/danteplanner/backend/config/CorsConfig.java`
- `backend/src/main/java/org/danteplanner/backend/service/JwtService.java`
- `backend/src/main/java/org/danteplanner/backend/dto/OAuthCallbackRequest.java`

## Target Code Area
Files to MODIFY:
- `backend/src/main/java/org/danteplanner/backend/controller/AuthController.java` - Fix getClientIp() for trusted proxy
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java` - Fix getClientIp() for trusted proxy
- `backend/src/main/java/org/danteplanner/backend/security/JwtAuthenticationFilter.java` - Use ObjectMapper for JSON error responses
- `backend/src/main/java/org/danteplanner/backend/config/SecurityConfig.java` - Add HSTS header
- `backend/src/main/java/org/danteplanner/backend/config/CorsConfig.java` - Restrict allowed headers to explicit list
- `backend/src/main/java/org/danteplanner/backend/dto/OAuthCallbackRequest.java` - Add @Size(max=512) validation
- `backend/src/main/java/org/danteplanner/backend/service/JwtService.java` - Change SameSite to Lax (optional UX improvement)
- `backend/src/main/resources/application.properties` - Add `security.trusted-proxy-ips` property

Files CREATED during session:
- `docs/learning/samesite-cookie-security.md` - SameSite cookie security guide

## System Context (Senior Thinking)
- **Feature domain**: Authentication, Security, Rate Limiting
- **Core files in this domain** (from architecture-map):
  - Authentication: `AuthController.java`, `JwtService.java`, `GoogleOAuthService.java`, `JwtAuthenticationFilter.java`
  - Configuration: `SecurityConfig.java`, `WebConfig.java`, `CorsConfig.java`, `RateLimitConfig.java`
  - Exception Handling: `GlobalExceptionHandler.java`
- **Cross-cutting concerns touched**:
  - Auth tokens (HttpOnly cookies managed by backend)
  - Rate limiting (Bucket4j)
  - Error handling (GlobalExceptionHandler)
  - Security headers (CSP, CORS, SameSite)

## Impact Analysis
- **Files being modified**:
  - `SecurityConfig.java` - HIGH impact (all authenticated requests)
  - `JwtService.java` - HIGH impact (all auth flows)
  - `AuthController.java` - MEDIUM impact (auth endpoints only)
  - `PlannerController.java` - MEDIUM impact (planner endpoints only)
  - `JwtAuthenticationFilter.java` - HIGH impact (all authenticated requests)
  - `CorsConfig.java` - HIGH impact (all cross-origin requests)
- **What depends on these files**:
  - All authenticated API calls depend on JwtAuthenticationFilter
  - All CORS preflight requests depend on CorsConfig
  - All rate-limited endpoints depend on getClientIp()
- **Potential ripple effects**:
  - Changing IP resolution could affect rate limit accuracy
  - Changing SameSite could affect external link behavior
  - Restricting CORS headers could break legitimate frontend requests
- **High-impact files to watch**: SecurityConfig.java, JwtService.java, JwtAuthenticationFilter.java

## Risk Assessment
- **Edge cases not yet defined**:
  - What if deployment is NOT behind nginx? (getClientIp should fall back to getRemoteAddr)
  - What if multiple proxies exist? (X-Forwarded-For contains comma-separated list - take rightmost trusted)
- **Performance concerns**:
  - IP validation adds minimal overhead
  - ObjectMapper serialization is slightly slower than string concat (negligible)
- **Backward compatibility**:
  - SameSite=Lax change: Users with strict cookies will seamlessly upgrade
  - Rate limit IP change: No backward compatibility issues
- **Security considerations**:
  - Critical: Rate limit bypass allows brute-force attacks
  - High: JSON injection could enable XSS if frontend doesn't sanitize
  - Medium: Missing HSTS allows SSL stripping on first request

## Testing Guidelines

### Manual Security Testing
1. **Rate Limit Bypass Test (Before Fix)**:
   - Send 5 requests to `/auth/callback/google` with same IP
   - Verify rate limit kicks in (429 response)
   - Send request with `X-Forwarded-For: 1.2.3.4` header
   - Verify rate limit is bypassed (this is the vulnerability)

2. **Rate Limit Bypass Test (After Fix)**:
   - Configure trusted proxy IPs in application.properties
   - Send request with spoofed `X-Forwarded-For` from non-proxy IP
   - Verify `getRemoteAddr()` is used instead (header ignored)
   - Send request through actual proxy
   - Verify `X-Forwarded-For` is correctly parsed

3. **JSON Injection Test**:
   - Trigger auth error with message containing `"` character
   - Verify response is valid JSON (not broken)
   - Check browser console for JSON parse errors

4. **SameSite Cookie Test (If Changed)**:
   - Open external site (e.g., `example.com`)
   - Add link to your app: `<a href="https://yourapp.com/planner">Link</a>`
   - Click the link
   - Verify user arrives logged in (cookie was sent)

5. **HSTS Test (After Fix)**:
   - Open browser dev tools, Network tab
   - Navigate to any page
   - Check response headers for `Strict-Transport-Security`
   - Verify `max-age=31536000` and `includeSubDomains`

### Automated Functional Verification
- [ ] Rate limit applies correctly when X-Forwarded-For is spoofed from untrusted source
- [ ] Rate limit applies correctly when X-Forwarded-For is from trusted proxy
- [ ] JSON error responses are valid JSON with special characters
- [ ] HSTS header is present on all responses
- [ ] CORS only allows explicitly listed headers
- [ ] OAuth code validation rejects codes > 512 characters

### Edge Cases
- [ ] No X-Forwarded-For header: Falls back to getRemoteAddr()
- [ ] Multiple IPs in X-Forwarded-For: Takes first IP after proxy validation
- [ ] Empty X-Forwarded-For: Falls back to getRemoteAddr()
- [ ] Error message with quotes: JSON escapes properly
- [ ] Error message with newlines: JSON escapes properly
- [ ] OAuth code with special characters: Validated and rejected if invalid format

### Integration Points
- [ ] Frontend CORS: Verify all frontend API calls still work after header restriction
- [ ] External links: If SameSite changed, verify links from Discord/Reddit work
- [ ] Rate limiting: Verify legitimate users aren't affected by IP changes

## Remediation Priority

| Priority | Issue | Effort | Risk if Unfixed |
|----------|-------|--------|-----------------|
| 1 (Critical) | X-Forwarded-For Spoofing | Medium | Brute-force attacks possible |
| 2 (High) | JSON Injection | Low | XSS if frontend doesn't sanitize |
| 3 (Medium) | HSTS Header | Low | SSL stripping on first visit |
| 4 (Medium) | OAuth Code Validation | Low | Memory exhaustion with large codes |
| 5 (Medium) | CORS Header Restriction | Low | Header injection attacks |
| 6 (Low) | SameSite Lax Change | Low | UX improvement only |

## Positive Security Patterns (Keep These)

The analysis also identified strong security patterns that should be preserved:

- **Parameterized Queries**: All `@Query` uses `@Param` - no SQL injection
- **Secure Cookies**: HttpOnly + Secure + SameSite on all auth cookies
- **JWT Validation**: Proper signature, expiry, type checking
- **Token Blacklist**: SHA-256 hashed tokens (doesn't store raw)
- **Authorization Checks**: PlannerForbiddenException for ownership
- **PKCE Support**: OAuth2 code verifier validation
- **Soft Delete**: 30-day grace period for account recovery
- **Atomic Operations**: Vote/view updates prevent race conditions
