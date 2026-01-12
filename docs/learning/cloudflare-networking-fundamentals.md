# Cloudflare Networking Fundamentals

Session: Cloudflare Deployment - Hands-On Walkthrough
Date: 2026-01-12

## Overview

This document covers practical networking concepts encountered during a real Cloudflare deployment. Unlike theoretical documentation, these are lessons learned from actual configuration and troubleshooting.

---

## 1. DNS Record Types in Practice

### The Records We Created

| Type | Name | Target | Purpose |
|------|------|--------|---------|
| CNAME | `@` (apex) | `project.pages.dev` | Points root domain to Pages |
| CNAME | `www` | `dante-planner.com` | Redirects www to apex |
| A | `api` | `EC2 Elastic IP` | Points API subdomain to backend |

### Why CNAME for Apex Works on Cloudflare

**Traditional DNS rule:** CNAME records cannot exist at the zone apex (root domain) because CNAME replaces ALL other records, including required SOA and NS records.

**Cloudflare's solution: CNAME Flattening**
```
User request: dante-planner.com
         │
         ▼
Cloudflare DNS receives query
         │
         ▼
Internally resolves CNAME → project.pages.dev → IP address
         │
         ▼
Returns A record (IP) to client, not CNAME
```

The client never sees the CNAME - they receive an A record. This is Cloudflare-specific behavior and won't work with other DNS providers.

### Proxy Status: Orange vs Gray Cloud

```
┌─────────────────────────────────────────────────────────────┐
│  PROXIED (Orange Cloud)                                      │
│                                                              │
│  Client ──────► Cloudflare Edge ──────► Origin Server        │
│                      │                                       │
│                      ├── DDoS protection                     │
│                      ├── SSL termination                     │
│                      ├── Caching                             │
│                      └── Hides origin IP                     │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  DNS ONLY (Gray Cloud)                                       │
│                                                              │
│  Client ──────────────────────────► Origin Server            │
│                                                              │
│  Cloudflare only provides DNS resolution                     │
│  No proxy benefits, origin IP exposed                        │
└─────────────────────────────────────────────────────────────┘
```

**When to use each:**
- **Orange (Proxied):** Default for HTTP/HTTPS traffic. Always use unless you have a reason not to.
- **Gray (DNS only):** Non-HTTP protocols, debugging, or when origin must see real client IP directly.

---

## 2. HTTP Status Codes from Cloudflare

### 522: Connection Timed Out

```
Client ────► Cloudflare ────X Origin
                       │
                       └── TCP connection failed
                           Origin didn't respond to SYN
```

**Causes we encountered:**
1. Origin server not running
2. Firewall blocking Cloudflare IPs
3. **Custom domain not linked to Pages project** (our case)

**Key insight:** For Cloudflare Pages, DNS alone isn't enough. The Pages project must explicitly know which domains it serves.

### Other Cloudflare-Specific Codes

| Code | Name | Meaning | Common Fix |
|------|------|---------|------------|
| 520 | Unknown Error | Origin returned empty/invalid response | Check origin logs |
| 521 | Web Server Down | Origin refused connection | Start origin server |
| 522 | Connection Timed Out | No TCP handshake | Firewall, server down |
| 523 | Origin Unreachable | DNS resolved but can't route | Check origin IP |
| 524 | A Timeout Occurred | TCP connected, HTTP timed out | Origin too slow (>100s) |
| 525 | SSL Handshake Failed | Origin SSL certificate issue | Fix origin SSL config |
| 526 | Invalid SSL Certificate | Strict mode, bad origin cert | Use valid cert or Origin CA |

---

## 3. Cookie Mechanics for Subdomain Architecture

### The Challenge

```
Frontend: dante-planner.com
Backend:  api.dante-planner.com

How does the auth cookie set by api.* reach dante-planner.com?
```

### Solution: Cookie Domain Attribute

```
Set-Cookie: token=xyz; Domain=.dante-planner.com; Path=/; Secure; HttpOnly
                            │
                            └── Leading dot = "this domain and all subdomains"
```

**Cookie scope rules:**

| Domain Attribute | Sent To |
|------------------|---------|
| Not specified | Only exact domain that set it |
| `.dante-planner.com` | dante-planner.com, api.dante-planner.com, www.dante-planner.com |
| `api.dante-planner.com` | Only api.dante-planner.com |

### SameSite Attribute: Lax vs None vs Strict

**Common misconception:** "Cross-subdomain needs SameSite=None"

**Reality:** Subdomains of the same registrable domain are **same-site**, not cross-site.

```
┌─────────────────────────────────────────────────────────────┐
│  SAME-SITE (SameSite=Lax works)                              │
│                                                              │
│  dante-planner.com ←→ api.dante-planner.com                  │
│  example.com ←→ app.example.com ←→ api.example.com           │
│                                                              │
│  Same "registrable domain" = same site                       │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  CROSS-SITE (requires SameSite=None)                         │
│                                                              │
│  example.com ←→ different-site.com                           │
│  myapp.com ←→ api.thirdparty.io                              │
│                                                              │
│  Different registrable domains = cross-site                  │
└─────────────────────────────────────────────────────────────┘
```

**Practical implications:**

| SameSite | Behavior | Use Case |
|----------|----------|----------|
| **Strict** | Cookie only sent on same-site requests from same origin | High security, breaks OAuth redirects |
| **Lax** | Sent on same-site + top-level navigations (links, form GET) | **Our choice** - balances security and usability |
| **None** | Always sent (requires Secure flag) | Third-party integrations, embeds |

### Why We Chose Lax

```java
// application-prod.properties
cookie.domain=.dante-planner.com
cookie.same-site=Lax
cookie.secure=true
```

- **Lax** allows the cookie on API calls from frontend (same-site)
- **Lax** allows OAuth redirects to include the cookie
- **Lax** blocks CSRF from external sites clicking our API links
- **None** would work but provides weaker CSRF protection unnecessarily

---

## 4. SSL/TLS Modes Explained

### The Four Modes

```
┌────────────────────────────────────────────────────────────────┐
│  OFF: No encryption anywhere                                    │
│  Client ═══════════════════════════════════════════► Origin    │
│          HTTP                              HTTP                 │
│  ⚠️ Never use in production                                     │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│  FLEXIBLE: Encrypted to edge only                               │
│  Client ═══════╗                    ╔═══════════► Origin       │
│          HTTPS ║  Cloudflare Edge   ║ HTTP                     │
│                ╚════════════════════╝                          │
│  ⚠️ Origin traffic unencrypted - avoid if possible             │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│  FULL: End-to-end, self-signed OK                               │
│  Client ═══════╗                    ╔═══════════► Origin       │
│          HTTPS ║  Cloudflare Edge   ║ HTTPS (any cert)        │
│                ╚════════════════════╝                          │
│  ✓ Encrypted, but MITM possible if cert compromised            │
└────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│  FULL (STRICT): End-to-end, valid cert required                 │
│  Client ═══════╗                    ╔═══════════► Origin       │
│          HTTPS ║  Cloudflare Edge   ║ HTTPS (valid cert)      │
│                ╚════════════════════╝                          │
│  ✓✓ Best security - use Cloudflare Origin CA (free)            │
└────────────────────────────────────────────────────────────────┘
```

### Our Configuration

- **Pages (frontend):** Automatic - Cloudflare handles everything
- **API subdomain:** Full (Strict) eventually, requires Origin CA cert on EC2

### Cloudflare Origin CA vs Let's Encrypt

| Feature | Origin CA | Let's Encrypt |
|---------|-----------|---------------|
| Validity | 15 years | 90 days |
| Renewal | Manual (once per 15 years) | Auto-renew required |
| Trust | Only Cloudflare trusts it | Universally trusted |
| Use case | Origin behind Cloudflare proxy | Direct public access |

**Key insight:** Origin CA certificates are **only valid when traffic goes through Cloudflare**. If someone bypasses Cloudflare and hits your EC2 directly, the certificate will show as invalid. This is actually a feature - it ensures all traffic must go through Cloudflare.

---

## 5. Cloudflare Pages: The Two-Way Binding

### What We Learned the Hard Way

**Setup we did:**
1. DNS: CNAME `@` → `project.pages.dev` ✓
2. Pages project deployed ✓
3. Result: HTTP 522 error ✗

**Missing step:**
```
Pages Project → Settings → Custom Domains → Add "dante-planner.com"
```

### Why Both Are Required

```
┌─────────────────────────────────────────────────────────────┐
│  DNS Record (pointing TO Pages)                              │
│                                                              │
│  "When someone asks for dante-planner.com,                   │
│   tell them to go to project.pages.dev"                      │
│                                                              │
│  DNS knows where to send traffic.                            │
│  But Pages doesn't know it should accept it.                 │
└─────────────────────────────────────────────────────────────┘
                          +
┌─────────────────────────────────────────────────────────────┐
│  Custom Domain in Pages (accepting FROM domain)              │
│                                                              │
│  "I am project.pages.dev, and I will serve                   │
│   content for dante-planner.com requests"                    │
│                                                              │
│  Now Pages knows to accept and serve the request.            │
└─────────────────────────────────────────────────────────────┘
                          =
                    Working site
```

This is similar to nginx `server_name` or Apache `ServerName` - the server needs to know which hostnames it should respond to.

---

## 6. Environment Variables: Build-Time vs Runtime

### Vite (Frontend) - Build-Time

```bash
# .env.production
VITE_API_BASE_URL=https://api.dante-planner.com
```

**Behavior:**
- Variables are replaced at build time
- Bundled into JavaScript as string literals
- Cannot change without rebuilding
- Only `VITE_` prefixed vars are exposed (security feature)

```javascript
// After build, in bundle.js:
const API = "https://api.dante-planner.com"; // Literally replaced
```

### Spring Boot (Backend) - Runtime

```properties
# application-prod.properties
cors.allowed-origins=${CORS_ALLOWED_ORIGINS:https://dante-planner.com}
```

**Behavior:**
- Can be overridden at runtime via environment variables
- Same JAR/image works in multiple environments
- `${VAR:default}` syntax provides fallbacks

```bash
# Override at runtime
docker run -e CORS_ALLOWED_ORIGINS=https://staging.dante-planner.com app
```

### Comparison

| Aspect | Vite (Frontend) | Spring Boot (Backend) |
|--------|-----------------|----------------------|
| When resolved | Build time | Runtime |
| Change requires | Rebuild + redeploy | Restart with new env var |
| Security | VITE_ prefix whitelist | All vars accessible |
| Use case | Public config only | Can include secrets |

---

## 7. CORS for Subdomain Architecture

### What CORS Protects Against

```
┌─────────────────────────────────────────────────────────────┐
│  WITHOUT CORS                                                │
│                                                              │
│  evil-site.com                                               │
│       │                                                      │
│       └── JavaScript: fetch("https://api.dante-planner.com") │
│                              │                               │
│                              ▼                               │
│                   API returns user's data                    │
│                   (because browser sends their cookie)       │
│                              │                               │
│                              ▼                               │
│                   evil-site.com steals data                  │
└─────────────────────────────────────────────────────────────┘
```

### How CORS Works

```
Browser on dante-planner.com
       │
       ├── 1. Preflight: OPTIONS /api/user
       │         Origin: https://dante-planner.com
       │
       ▼
API (api.dante-planner.com)
       │
       ├── 2. Check: Is origin allowed?
       │         cors.allowed-origins=https://dante-planner.com
       │         Yes → Continue
       │
       └── 3. Response headers:
                 Access-Control-Allow-Origin: https://dante-planner.com
                 Access-Control-Allow-Credentials: true
                 Access-Control-Allow-Methods: GET, POST, PUT, DELETE
       │
       ▼
Browser
       │
       └── 4. Headers match → Allow JavaScript to read response
```

### Our Configuration

```java
// CorsConfig.java
registry.addMapping("/api/**")
    .allowedOrigins("https://dante-planner.com")  // Exact match
    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
    .allowCredentials(true)  // Required for cookies
    .allowedHeaders("*");
```

**Critical points:**
- `allowCredentials(true)` is required for cookies to be sent
- Cannot use `allowedOrigins("*")` with `allowCredentials(true)` - security restriction
- Origin must match exactly (no trailing slash)

---

## 8. Debugging DNS and Connectivity

### Tools for Verification

```bash
# Check DNS resolution
dig dante-planner.com +short
# Expected: Cloudflare IP (104.x.x.x or 172.x.x.x)

# Check if site is reachable
curl -I https://dante-planner.com
# Look for: HTTP/2 200 and cf-ray header

# Check SSL certificate
openssl s_client -connect dante-planner.com:443 -servername dante-planner.com
# Look for: Certificate chain and validity

# Check from Cloudflare's perspective
# Dashboard → DNS → Records → should show green checkmark
```

### Common Issues and Solutions

| Symptom | Likely Cause | Solution |
|---------|--------------|----------|
| `cf-ray` header present, 522 error | Origin unreachable | Check origin server, firewall |
| No `cf-ray` header | DNS not through Cloudflare | Check nameservers |
| `ERR_SSL_VERSION_OR_CIPHER_MISMATCH` | SSL mode mismatch | Match SSL mode to origin cert |
| CORS error in console | Origin not in allowed list | Update `cors.allowed-origins` |
| Cookie not sent | Wrong domain/SameSite | Check cookie attributes |

---

## Summary Table

| Concept | What We Configured | Why |
|---------|-------------------|-----|
| DNS CNAME at apex | `@` → `project.pages.dev` | Cloudflare flattens to A record |
| Cookie domain | `.dante-planner.com` | Leading dot for subdomain sharing |
| SameSite | `Lax` | Same registrable domain = same-site |
| SSL mode | Full (Strict) | End-to-end encryption |
| CORS origin | `https://dante-planner.com` | Exact origin match, credentials enabled |
| Pages custom domain | Added in dashboard | Two-way binding required |

---

## References

- [Cloudflare HTTP Status Codes](https://developers.cloudflare.com/support/troubleshooting/cloudflare-errors/troubleshooting-cloudflare-5xx-errors/)
- [SameSite Cookie Explained](https://web.dev/samesite-cookies-explained/)
- [CORS in Depth](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS)
- [Cloudflare Origin CA](https://developers.cloudflare.com/ssl/origin-configuration/origin-ca/)
