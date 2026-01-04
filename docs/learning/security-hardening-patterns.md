# Security Hardening Patterns

> **Source**: Backend security issue handling (2026-01-04)
> **Scope**: Rate limiting, header validation, cookie security, input validation

---

## 1. Trusted Proxy Validation Pattern

### Problem
When behind a reverse proxy (nginx), the client's real IP is in `X-Forwarded-For` header. But attackers can spoof this header to bypass IP-based rate limiting.

### Anti-Pattern
```
// VULNERABLE: Blindly trusts X-Forwarded-For
String clientIp = request.getHeader("X-Forwarded-For");
if (clientIp == null) {
    clientIp = request.getRemoteAddr();
}
```

An attacker can send:
```bash
curl -H "X-Forwarded-For: fake.ip.1" https://api.example.com/login
curl -H "X-Forwarded-For: fake.ip.2" https://api.example.com/login
# Each request appears from different IP, bypassing rate limits
```

### Solution Pattern
Only trust `X-Forwarded-For` when the direct connection (`getRemoteAddr()`) is from a known proxy IP.

**Configuration** (`application.properties`):
```properties
security.trusted-proxy-ips=${TRUSTED_PROXY_IPS:127.0.0.1}
```

**Utility Class** (`ClientIpResolver.java`):
- Check if `getRemoteAddr()` is in trusted proxy set
- If trusted: parse first IP from `X-Forwarded-For`
- If untrusted: ignore header, use `getRemoteAddr()`
- Validate IP format (IPv4/IPv6 regex) to prevent injection

**Key Insight**: The leftmost IP in `X-Forwarded-For` is the original client. Format: `client, proxy1, proxy2`.

---

## 2. JSON Error Response Serialization

### Problem
String concatenation in JSON responses allows injection attacks.

### Anti-Pattern
```java
// VULNERABLE: String concatenation
String json = String.format("{\"error\": \"%s\", \"message\": \"%s\"}", code, message);
```

If `message` contains quotes: `Token "expired"` → broken JSON:
```json
{"error": "X", "message": "Token "expired""}
```

### Solution Pattern
Use Jackson ObjectMapper for automatic escaping:

```java
private final ObjectMapper objectMapper;

response.getWriter().write(
    objectMapper.writeValueAsString(Map.of("error", code, "message", message))
);
```

**Key Insight**: Never concatenate JSON strings. Let the serialization framework handle escaping.

---

## 3. Security Headers Configuration

### HSTS (HTTP Strict Transport Security)
Forces browsers to use HTTPS for all future requests.

```java
.httpStrictTransportSecurity(hsts -> hsts
    .maxAgeInSeconds(31536000)  // 1 year
    .includeSubDomains(true)
)
```

**Key values**:
- `maxAgeInSeconds`: 31536000 (1 year) is industry standard
- `includeSubDomains`: Protects all subdomains
- `preload`: Optional, for browser preload list submission

### CSP, X-Frame-Options, XSS Protection
Already configured in SecurityConfig - see file for patterns.

---

## 4. CORS Header Whitelisting

### Problem
Wildcard `*` in allowed headers is overly permissive.

### Anti-Pattern
```java
configuration.setAllowedHeaders(List.of("*"));
```

### Solution Pattern
Explicit whitelist of required headers:

```java
configuration.setAllowedHeaders(List.of(
    "Content-Type",    // Required for JSON API
    "Authorization",   // Standard auth header
    "Cache-Control"    // Client cache control
));
```

**Investigation Required**: Check frontend code for custom headers before restricting.

---

## 5. SameSite Cookie Security

### Values Explained

| Value | Behavior | Use Case |
|-------|----------|----------|
| **Strict** | Never sent cross-site | Maximum security, breaks external links |
| **Lax** | Sent on top-level navigation (link clicks), blocked for embedded/POST | Balanced security + UX |
| **None** | Always sent (requires Secure) | Cross-site embedding |

### When Lax is Safe
- All GET endpoints are read-only (no state changes)
- State-changing operations use POST/DELETE
- CSRF tokens not required because Lax blocks cross-site POSTs

### Pattern
```java
cookie.setAttribute("SameSite", "Lax");
cookie.setHttpOnly(true);
cookie.setSecure(true);  // HTTPS only
```

**UX Benefit**: Users clicking links from Discord/Reddit stay logged in.

---

## 6. Input Size Validation

### Problem
Oversized payloads can cause memory exhaustion.

### Solution Pattern
Add `@Size` constraints to DTOs:

```java
@NotBlank
@Size(max = 512, message = "Authorization code exceeds maximum length")
private String code;

@NotBlank
@Size(max = 32, message = "Provider exceeds maximum length")
private String provider;
```

**Guideline**: Set limits based on realistic max values:
- OAuth codes: ~512 chars
- Provider names: ~32 chars
- PKCE verifiers: 128 chars (per spec)

---

## 7. Configuration Properties Pattern

### Why Not @Value Field Injection

**Anti-Pattern** (violates backend guidelines):
```java
@Value("${security.trusted-proxy-ips}")
private String trustedProxyIps;  // Field injection
```

**Issues**:
- Untestable without reflection
- Mutable (not `final`)
- Can hide circular dependencies

**Solution Pattern**:
```java
@Configuration
@ConfigurationProperties(prefix = "security")
@Getter
public class SecurityProperties {
    private String trustedProxyIps = "127.0.0.1";
    private Set<String> trustedProxyIpSet;

    @PostConstruct
    public void parseTrustedProxyIps() {
        // Parse and validate at startup
        trustedProxyIpSet = Arrays.stream(trustedProxyIps.split(","))
            .map(String::trim)
            .filter(ip -> !ip.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }
}
```

**Benefits**:
- Validated at startup (fail fast)
- Immutable after initialization
- Testable via constructor
- Type-safe configuration

---

## 8. Logging Security Data

### Problem
Logging sensitive data at INFO level exposes internal network topology.

### Anti-Pattern
```java
log.info("Trusted proxy IPs: {}", trustedProxyIpSet);  // Exposes internal IPs
```

### Solution Pattern
```java
log.info("Configured {} trusted proxy IP(s)", trustedProxyIpSet.size());
log.debug("Trusted proxy IPs: {}", trustedProxyIpSet);  // Only visible in debug
```

**Guideline**: Log counts at INFO, details at DEBUG.

---

## Quick Reference: Security Checklist

### Rate Limiting
- [ ] X-Forwarded-For only trusted from proxy IPs
- [ ] IP format validated (prevent injection)
- [ ] Fallback to getRemoteAddr() when untrusted

### Headers
- [ ] HSTS enabled (1 year, includeSubDomains)
- [ ] CSP configured (default-src 'self')
- [ ] X-Frame-Options: DENY
- [ ] CORS uses explicit header whitelist

### Cookies
- [ ] HttpOnly: true (prevent XSS)
- [ ] Secure: true (HTTPS only)
- [ ] SameSite: Lax or Strict

### Input Validation
- [ ] @Size on all string fields
- [ ] @Valid on @RequestBody
- [ ] ObjectMapper for JSON serialization

### Configuration
- [ ] @ConfigurationProperties for grouped config
- [ ] @PostConstruct for validation
- [ ] Sensitive data at DEBUG level only

---

## Files Reference

| File | Purpose |
|------|---------|
| `util/ClientIpResolver.java` | Trusted proxy IP validation |
| `config/SecurityProperties.java` | Trusted proxy IP configuration |
| `config/SecurityConfig.java` | HSTS, CSP, X-Frame-Options |
| `config/CorsConfig.java` | CORS header whitelist |
| `util/CookieUtils.java` | SameSite, HttpOnly, Secure cookies |
| `security/JwtAuthenticationFilter.java` | ObjectMapper for error responses |
| `dto/OAuthCallbackRequest.java` | @Size validation example |
