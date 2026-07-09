# Security Issues Research

## Spec-to-Code Mapping

| Requirement | File | Modification |
|-------------|------|--------------|
| **Critical: X-Forwarded-For Spoofing** | `AuthController.java:159-166`, `PlannerController.java` | Replace `getClientIp()` with trusted proxy validation |
| **High: JSON Injection** | `JwtAuthenticationFilter.java:142-153` | Replace String.format() with ObjectMapper |
| **Medium: HSTS Header** | `SecurityConfig.java:64-70` | Add HSTS to security headers chain |
| **Medium: CORS Headers** | `CorsConfig.java:24` | Replace wildcard with explicit list |
| **Medium: OAuth Code Size** | `OAuthCallbackRequest.java` | Add `@Size(max=512)` |
| **Low: SameSite=Lax** | `JwtService.java` | Change cookie attribute |
| **Config: Trusted Proxies** | `application.properties` | Add `security.trusted-proxy-ips` |

## Spec-to-Pattern Mapping

| Requirement | Pattern Source | Notes |
|-------------|----------------|-------|
| IP validation | Spring ForwardedHeaderFilter | Validate proxy before trusting header |
| JSON error serialization | Jackson ObjectMapper | Proper escaping for special chars |
| Security headers | Spring Security DSL | Existing pattern in SecurityConfig |
| CORS restriction | Best practice | Explicit list, not wildcard |
| Request validation | Jakarta @Size | Matches existing @NotBlank pattern |

## Pattern Enforcement

| Read First | Pattern | Apply To |
|------------|---------|----------|
| `SecurityConfig.java:64-70` | Headers DSL chain | Add HSTS config |
| `CorsConfig.java:20-26` | Property injection | Modify header list |
| `AuthController.java:159-166` | IP extraction | Refactor with proxy validation |
| `JwtAuthenticationFilter.java:142-153` | Error response | Replace with ObjectMapper |

## Existing Utilities

| Category | Location | Status |
|----------|----------|--------|
| IP Validation | `AuthController.getClientIp()` | VULNERABLE - needs fix |
| Error Response | `JwtAuthenticationFilter.writeErrorResponse()` | VULNERABLE - needs fix |
| Security Headers | `SecurityConfig.headers()` | Missing HSTS only |
| CORS Config | `CorsConfig.corsConfigurationSource()` | Uses wildcard headers |
| Rate Limiting | `RateLimitConfig.checkAuthLimit()` | Correct pattern, depends on getClientIp() |
| Config Properties | `application.properties` | Missing trusted-proxy-ips |

## Gap Analysis

**Missing:**
- Trusted proxy IP property in application.properties
- HSTS security header in SecurityConfig
- Explicit CORS allowed headers list
- OAuth code @Size validation

**Needs Modification:**
- getClientIp() in AuthController and PlannerController
- writeErrorResponse() in JwtAuthenticationFilter
- allowedHeaders in CorsConfig

**Can Reuse:**
- Bucket4j rate limiting framework (already integrated)
- Spring Security headers DSL pattern
- Jakarta Bean Validation infrastructure

## Testing Requirements

### Manual Tests
1. Rate limit bypass: Verify spoofed X-Forwarded-For from untrusted IP is ignored
2. JSON injection: Trigger error with special chars, verify valid JSON response
3. HSTS: Check response header presence
4. CORS: Verify unlisted headers rejected
5. OAuth validation: Verify oversized code rejected

### Automated Tests
- [ ] getClientIp() falls back to remoteAddr for untrusted source
- [ ] getClientIp() parses X-Forwarded-For from trusted proxy
- [ ] writeErrorResponse() produces valid JSON with special chars
- [ ] SecurityConfig includes HSTS header
- [ ] CorsConfig rejects non-whitelisted headers
- [ ] OAuthCallbackRequest rejects code > 512 chars

### Edge Cases
- [ ] No X-Forwarded-For → use getRemoteAddr()
- [ ] Multiple IPs in header → take first after validation
- [ ] Empty header → use getRemoteAddr()
- [ ] Malformed header → handle gracefully

## Technical Constraints

| Constraint | Handling |
|------------|----------|
| nginx proxy IP | Configurable via `security.trusted-proxy-ips` |
| Spring Security 7 | PathPattern syntax already used |
| Bucket4j | Keyed by IP, fix getClientIp() fixes buckets |
| Jackson ObjectMapper | Auto-included by Spring Boot |
| Spring Boot 4.0.0 | HSTS API compatible |

## Implementation Order

1. Add `security.trusted-proxy-ips` to application.properties
2. Fix `getClientIp()` in AuthController (extract to shared utility)
3. Fix `getClientIp()` in PlannerController (use same utility)
4. Fix `writeErrorResponse()` in JwtAuthenticationFilter
5. Add HSTS to SecurityConfig
6. Restrict CORS headers in CorsConfig
7. Add @Size to OAuthCallbackRequest
8. (Optional) Change SameSite to Lax in JwtService
