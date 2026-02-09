# TODO

## Legal Documents Review

**Status:** Adequate for a hobby/fan project, but not legally robust.

### Current Assessment

| Aspect | Assessment |
|--------|------------|
| Completeness | Covers basics (data collection, user conduct, disclaimers) |
| Legal enforceability | Weak - no jurisdiction clause, no dispute resolution |
| GDPR compliance | Partial - missing data retention period, DPO contact, right to portability |
| CCPA compliance | Missing - no "Do Not Sell" mention, no CA-specific rights |
| Cookie policy | Too vague - should list specific cookies used |

### Missing Elements

1. **Age restriction** - No minimum age requirement (COPPA requires 13+)
2. **Jurisdiction** - Which country's law applies?
3. **Liability cap** - No limitation of liability amount
4. **Indemnification** - Users should indemnify you for their content
5. **Data retention** - How long do you keep data after deletion?
6. **Third-party services** - Should list Google OAuth, Cloudflare, hosting provider

### Recommended Additions

- Age requirement: "You must be 13+ to use this service"
- Jurisdiction: "These terms are governed by the laws of [your country]"
- Data retention: "Data is deleted within 30 days of account deletion request"

### When to Prioritize

For a free fan project with no monetization, current state is acceptable. Revisit if:
- Monetizing (ads, donations, premium features)
- Handling EU users seriously
- Scaling to significant traffic

Consider using a legal template generator (Termly, iubenda) or consulting a lawyer.

## Code Quality Integration

### SonarQube Cloud Setup

**Goal:** Integrate SonarQube Cloud with GitHub for automated PR analysis and quality gates.

**Current State:**
- ✅ Local SonarQube 9.9.8 LTS running (Docker)
- ✅ OWASP Dependency-Check 12.2.0 configured
- ✅ JaCoCo code coverage configured
- ✅ Maven integration working
- ❌ Not integrated with GitHub PRs

**Setup Steps:**

1. **Sign up for SonarQube Cloud**
   - URL: https://sonarcloud.io
   - Login with GitHub account
   - Free for public repositories

2. **Import Repository**
   - Connect LimbusPlanner repository
   - Generate project token
   - Note organization key

3. **Configure GitHub Actions**
   - Create `.github/workflows/code-quality.yml`
   - Add SonarQube Cloud token to GitHub Secrets
   - Configure workflow to run on push/PR

4. **Setup Quality Gates**
   - Configure passing thresholds
   - Enable PR decoration
   - Block merges on quality gate failures

5. **Dependency-Check Strategy**

   **Option A: Scheduled Scans (Recommended)**
   - Run nightly via GitHub Actions scheduled workflow
   - Avoids slowing down PR checks (first run: 10-20 min)
   - NVD database cached between runs
   - Results uploaded to GitHub Security tab

   **Option B: On Every PR (Slow)**
   - Adds 10-20 minutes to first PR build
   - Requires NVD cache setup in Actions
   - Better for high-security projects

   **Option C: Local Only**
   - Run manually before releases
   - Fastest CI/CD, but misses early detection

   **Recommendation:** Option A (nightly) balances security and speed

**Benefits:**
- Automatic code quality checks on every PR
- Quality gate status visible in GitHub
- Team-wide visibility of code metrics
- Historical quality trends
- Security vulnerability tracking

**Priority:** Medium - Local setup works, but cloud integration enables team collaboration and automated PR checks.

---

## Security: Anti-CSRF Token Implementation

**Status:** PENDING
**Priority:** Medium (SameSite=Lax provides baseline protection)
**Effort:** 4-6 hours

### Current State

**Protection Mechanism:** SameSite=Lax cookies (configured in `CookieUtils.java`)
- Blocks cross-site POST requests (CSRF attacks)
- Allows top-level navigation (clicking links works)
- Adequate for modern browsers (>95% coverage)

**Limitation:** Legacy browsers (IE11, old Android WebView) don't respect SameSite attribute.

### Proposed Enhancement: Double Submit Cookie Pattern

Add explicit CSRF tokens for defense-in-depth on moderation endpoints.

**Implementation Steps:**

#### 1. Backend: CSRF Filter (2 hours)

Create `CsrfTokenFilter.java`:
```java
@Component
public class CsrfTokenFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
        // Generate CSRF token on first request
        String csrfToken = (String) request.getSession().getAttribute("CSRF_TOKEN");
        if (csrfToken == null) {
            csrfToken = UUID.randomUUID().toString();
            request.getSession().setAttribute("CSRF_TOKEN", csrfToken);
        }

        // Set token in response cookie (readable by JS)
        Cookie csrfCookie = new Cookie("XSRF-TOKEN", csrfToken);
        csrfCookie.setPath("/");
        csrfCookie.setHttpOnly(false); // JS needs to read this
        csrfCookie.setSecure(true);
        csrfCookie.setAttribute("SameSite", "Lax");
        response.addCookie(csrfCookie);

        // Validate token on POST/PUT/DELETE requests
        if (Arrays.asList("POST", "PUT", "DELETE").contains(request.getMethod())) {
            String headerToken = request.getHeader("X-XSRF-TOKEN");
            if (headerToken == null || !headerToken.equals(csrfToken)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
```

**Register in SecurityConfig:**
```java
http.addFilterBefore(csrfTokenFilter, UsernamePasswordAuthenticationFilter.class);
```

#### 2. Frontend: Auto-Include CSRF Header (1 hour)

Update `ApiClient` in `frontend/src/lib/api.ts`:
```typescript
private async request(url: string, options: RequestInit = {}): Promise<Response> {
  // Read CSRF token from cookie
  const csrfToken = document.cookie
    .split('; ')
    .find(row => row.startsWith('XSRF-TOKEN='))
    ?.split('=')[1]

  const headers = {
    'Content-Type': 'application/json',
    ...(csrfToken && { 'X-XSRF-TOKEN': csrfToken }), // Auto-include on all requests
    ...options.headers,
  }

  // ... rest of method
}
```

#### 3. Configuration (30 min)

Add to `application.properties`:
```properties
# CSRF Token Configuration
csrf.enabled=true
csrf.cookie-name=XSRF-TOKEN
csrf.header-name=X-XSRF-TOKEN
```

#### 4. Testing (2 hours)

**Unit Tests:**
- POST without CSRF token → 403 Forbidden
- POST with invalid token → 403 Forbidden
- POST with valid token → 200 OK
- GET requests work without token

**Integration Tests:**
```java
@Test
void moderationAction_withoutCsrfToken_returns403() {
    mockMvc.perform(post("/api/moderation/user/1234/ban")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\": \"Test\"}"))
            .andExpect(status().isForbidden());
}

@Test
void moderationAction_withValidCsrfToken_returns200() {
    String csrfToken = getCsrfToken(); // Helper to extract from session
    mockMvc.perform(post("/api/moderation/user/1234/ban")
            .header("X-XSRF-TOKEN", csrfToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\": \"Test\"}"))
            .andExpect(status().isOk());
}
```

### Alternative: Spring Security CSRF

Use Spring's built-in CSRF protection:

**SecurityConfig.java:**
```java
http.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
    .requireCsrfProtectionMatcher(new AntPathRequestMatcher("/api/**", "POST"))
);
```

**Pros:** Built-in, well-tested, integrates with Spring ecosystem
**Cons:** More configuration, harder to customize for SPA architecture

### Decision Required

**Option 1 (Current):** Keep SameSite=Lax only
- Simpler architecture
- Adequate for 95%+ of users
- No JS changes needed

**Option 2:** Add Double Submit Cookie pattern
- Better defense-in-depth
- Supports legacy browsers
- Requires frontend changes

**Option 3:** Use Spring Security CSRF
- Standard Spring approach
- Most comprehensive
- Requires both backend and frontend changes

### References

- OWASP CSRF Prevention: https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html
- SameSite Cookie Spec: https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis-03#section-5.3.7
- Spring Security CSRF: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html

---

## JWT Token Security - Deferred Issues

### C1: Encryption Key Rotation Mechanism
**Status:** Deferred to future sprint
**Priority:** HIGH (not blocking for current scale)
**Effort:** 2-3 days
**Review Date:** 2026-02-09

**Problem:**
Single static AES-256 key with no rotation capability. If compromised, ALL historical tokens are decryptable.

**Current Mitigation:**
- Key stored in AWS SSM SecureString (encrypted at rest)
- Access logged via CloudTrail
- Rotation only needed if breach detected (acceptable to log out all users in that scenario)

**When to Fix:**
- User base grows to 10,000+ (support cost justifies graceful rotation)
- Enterprise customers require compliance (SOC2, PCI-DSS)
- Funding round / security audit requirement
- Detected breach (emergency response)

**Implementation Plan:**
```java
// JwtProperties.java
private Map<String, byte[]> encryptionKeys = new HashMap<>();
private String currentKeyId;

@PostConstruct
public void loadKeys() {
    // Load multiple versioned keys from SSM
    encryptionKeys.put("v1", fetchFromSSM("JWT_ENCRYPTION_KEY_V1"));
    encryptionKeys.put("v2", fetchFromSSM("JWT_ENCRYPTION_KEY_V2"));
    currentKeyId = "v2"; // Use newest for new tokens
}

// JwtTokenService.java - buildToken()
.claim("kid", jwtProperties.getCurrentKeyId())

// JwtTokenService.java - parseToken()
String keyId = claims.get("kid", String.class);
byte[] key = jwtProperties.getEncryptionKey(keyId);
```

**Rotation Process:**
1. Generate new key v2, add to SSM
2. Deploy - new tokens use v2, old tokens validated with v1
3. Wait 7 days (refresh token max lifetime)
4. Remove v1 from SSM

---

### C2: GCM IV Reuse Risk (Birthday Paradox)
**Status:** Deferred to future sprint
**Priority:** MEDIUM (negligible risk at current scale)
**Effort:** 1 day
**Review Date:** 2026-02-09

**Problem:**
Pure random 12-byte IV generation. Birthday paradox collision risk at ~281 trillion tokens (2^48).

**Current Risk Assessment:**
- At 100 tokens/sec: 89 million years to collision
- At 1000 tokens/sec: 8.9 million years to collision
- Current traffic: <50 req/sec (decades to risk zone)

**When to Fix:**
- Sustained 1000+ req/sec traffic
- Multi-datacenter deployment (10+ servers × 100 req/sec = collision risk)
- Enterprise SLA requirements for cryptographic guarantees

**Implementation Plan:**
```java
// Add to JwtTokenService
private final AtomicInteger ivCounter = new AtomicInteger(0);

private byte[] generateIV() {
    byte[] iv = new byte[12];

    // First 4 bytes: monotonic counter (prevents reuse)
    int counter = ivCounter.incrementAndGet();
    ByteBuffer.wrap(iv).putInt(counter);

    // Last 8 bytes: random (unpredictability)
    secureRandom.nextBytes(iv, 4, 8);

    return iv;
}
```

**Alternative (Timestamp-based):**
```java
// First 8 bytes: nanosecond timestamp
ByteBuffer.wrap(iv).putLong(System.nanoTime());
// Last 4 bytes: random
secureRandom.nextBytes(iv, 8, 4);
```

**Test Coverage:**
```java
@Test
void ivUniqueness_1MillionTokens() {
    Set<String> ivs = new HashSet<>();
    for (int i = 0; i < 1_000_000; i++) {
        String token = tokenService.generateAccessToken(...);
        String iv = extractIV(token);
        assertTrue(ivs.add(iv), "Duplicate IV at iteration " + i);
    }
}
```

---

**Revisit Triggers:**
- User growth milestone: 10,000+ active users
- Traffic milestone: 500+ req/sec sustained
- Compliance requirement: SOC2, PCI-DSS, HIPAA
- Funding round: Investor security audit
- Detected breach: Emergency key rotation needed
