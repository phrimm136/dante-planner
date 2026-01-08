# SameSite Cookie Configuration - Security Analysis

This document explains the SameSite cookie attribute, its security implications, and how to choose the right configuration for your application.

---

## Table of Contents

1. [What is SameSite?](#what-is-samesite)
2. [The Three SameSite Values](#the-three-samesite-values)
3. [CSRF Attack Mechanics](#csrf-attack-mechanics)
4. [When Each Value is Appropriate](#when-each-value-is-appropriate)
5. [LimbusPlanner Analysis](#limbusplanner-analysis)
6. [Decision Framework](#decision-framework)
7. [Common Mistakes](#common-mistakes)

---

## What is SameSite?

The `SameSite` cookie attribute controls whether cookies are sent with cross-site requests. It's a defense mechanism against Cross-Site Request Forgery (CSRF) attacks.

**Cookie Header Example:**
```
Set-Cookie: access_token=abc123; HttpOnly; Secure; SameSite=Lax
```

**Key Concept:** A "cross-site" request is when the request originates from a different site than the cookie's domain. For example, if `evil.com` makes a request to `yourapp.com`, that's cross-site.

---

## The Three SameSite Values

### Comparison Table

| Request Type | `Strict` | `Lax` | `None` |
|--------------|----------|-------|--------|
| Link click from external site | Cookie NOT sent | Cookie sent | Cookie sent |
| Form GET from external site | Cookie NOT sent | Cookie sent | Cookie sent |
| Form POST from external site | Cookie NOT sent | Cookie NOT sent | Cookie sent |
| AJAX/fetch from external site | Cookie NOT sent | Cookie NOT sent | Cookie sent |
| iframe from external site | Cookie NOT sent | Cookie NOT sent | Cookie sent |
| Image/script tag from external site | Cookie NOT sent | Cookie NOT sent | Cookie sent |

### SameSite=Strict

**Behavior:** Cookie is NEVER sent on any cross-site request.

**Pros:**
- Maximum CSRF protection
- Simple mental model: "cookies only work on my site"

**Cons:**
- Users clicking links from email/Slack/Google arrive logged out
- Poor UX for sites that are linked to frequently

**Use when:** Highly sensitive applications (banking, admin panels) where UX trade-off is acceptable.

### SameSite=Lax

**Behavior:** Cookie is sent on "safe" top-level navigations (GET requests via links), but NOT on cross-site POST/PUT/DELETE or embedded requests.

**Pros:**
- Protects against CSRF for state-changing operations (POST/PUT/DELETE)
- Good UX: external links work normally
- Browser default since Chrome 80 (2020)

**Cons:**
- GET endpoints that modify state are vulnerable (but these violate REST anyway)

**Use when:** Most web applications. This is the recommended default.

### SameSite=None

**Behavior:** Cookie is sent on ALL requests, including cross-site.

**Requirements:**
- MUST also set `Secure` flag (HTTPS only)
- Without `Secure`, modern browsers reject the cookie

**Pros:**
- Required for legitimate cross-site scenarios (embedded widgets, OAuth providers)

**Cons:**
- No CSRF protection from SameSite
- Must implement other CSRF defenses (tokens, origin checking)

**Use when:** Third-party embeds, cross-domain SSO, widget providers.

---

## CSRF Attack Mechanics

### How CSRF Works

```
1. User logs into yourapp.com (gets session cookie)
2. User visits evil.com (while still logged into yourapp.com)
3. evil.com contains: <form action="https://yourapp.com/delete-account" method="POST">
4. JavaScript auto-submits the form
5. Browser sends request to yourapp.com WITH the session cookie
6. yourapp.com processes the request, thinking it's from the user
```

### How SameSite Prevents CSRF

With `SameSite=Lax` or `Strict`:
- Step 5 fails: Browser does NOT send the cookie for cross-site POST
- yourapp.com rejects the request (no valid session)

### The GET Endpoint Exception

With `SameSite=Lax`, GET requests from external sites DO include cookies. This is normally safe because GET should be idempotent (no side effects).

**Dangerous Pattern:**
```java
// NEVER DO THIS - GET that modifies state
@GetMapping("/transfer-money")
public void transfer(@RequestParam Long amount, @RequestParam Long toAccount) {
    accountService.transfer(getCurrentUser(), toAccount, amount);
}
```

An attacker could exploit this:
```html
<!-- On evil.com -->
<img src="https://bank.com/transfer-money?amount=10000&toAccount=attacker">
```

**Safe Pattern:**
```java
// Correct - POST for state changes
@PostMapping("/transfer-money")
public void transfer(@RequestBody TransferRequest request) {
    accountService.transfer(getCurrentUser(), request.getToAccount(), request.getAmount());
}
```

---

## When Each Value is Appropriate

### Use Strict When:

1. **High-security applications**
   - Banking/financial applications
   - Admin panels
   - Healthcare systems with PHI

2. **Internal tools**
   - Users don't typically arrive via external links
   - UX impact is minimal

3. **When you want defense-in-depth**
   - Even if GET endpoints are safe, extra protection doesn't hurt

### Use Lax When:

1. **Most consumer-facing applications**
   - Users share links on social media, email, Slack
   - Good balance of security and UX

2. **When following REST principles**
   - GET = read-only (safe with Lax)
   - POST/PUT/DELETE = state changes (protected by Lax)

3. **SEO-important sites**
   - Users arriving from search results should be logged in

### Use None When:

1. **Cross-domain authentication**
   - OAuth/SSO providers
   - Central auth service serving multiple domains

2. **Embedded widgets**
   - Chat widgets embedded on other sites
   - Payment forms in iframes

3. **Third-party APIs with cookie auth**
   - APIs called from customer domains
   - (Consider using token auth instead)

---

## LimbusPlanner Analysis

### Current Configuration

```java
// JwtService.java (assumed based on architecture)
ResponseCookie cookie = ResponseCookie.from(name, value)
    .httpOnly(true)
    .secure(true)
    .sameSite("Strict")  // Current setting
    .path("/")
    .maxAge(maxAge)
    .build();
```

### GET Endpoints Audit

| Endpoint | Purpose | Side Effects | Safe with Lax? |
|----------|---------|--------------|----------------|
| `GET /auth/me` | Get current user | None (read-only) | Yes |
| `GET /api/planner/md` | List user's planners | None (read-only) | Yes |
| `GET /api/planner/md/{id}` | Get single planner | None (read-only) | Yes |
| `GET /api/planner/md/config` | Get version config | None (read-only) | Yes |
| `GET /api/planner/md/published` | List published planners | None (read-only) | Yes |
| `GET /api/planner/md/recommended` | List recommended | None (read-only) | Yes |
| `GET /api/planner/md/events` | SSE stream | None (read-only) | Yes |
| `GET /api/user/associations` | List associations | None (read-only) | Yes |

### State-Changing Endpoints

All state-changing operations use appropriate HTTP methods:

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/auth/callback/google` | POST | OAuth callback |
| `/auth/refresh` | POST | Token refresh |
| `/auth/logout` | POST | Logout |
| `/api/planner/md` | POST | Create planner |
| `/api/planner/md/{id}` | PUT | Update planner |
| `/api/planner/md/{id}` | DELETE | Delete planner |
| `/api/planner/md/{id}/publish` | PUT | Toggle publish |
| `/api/planner/md/{id}/vote` | POST | Cast vote |
| `/api/planner/md/{id}/view` | POST | Record view |
| `/api/user/username-keyword` | PUT | Update username |
| `/api/user/account` | DELETE | Delete account |

### Recommendation

**Changing from `Strict` to `Lax` is safe** because:

1. All GET endpoints are read-only (no side effects)
2. All state-changing operations use POST/PUT/DELETE
3. REST principles are followed correctly

**Benefits of Lax:**
- Users clicking shared planner links arrive logged in
- Better UX for social sharing
- Links from Discord/Reddit/Twitter work seamlessly

---

## Decision Framework

### Flowchart

```
                    Start
                      │
                      ▼
        ┌─────────────────────────────┐
        │ Do you have GET endpoints   │
        │ that modify state?          │
        └─────────────────────────────┘
                      │
           ┌──────────┴──────────┐
           │                     │
          Yes                   No
           │                     │
           ▼                     ▼
    ┌─────────────┐    ┌─────────────────────────┐
    │ FIX THEM    │    │ Is this a high-security │
    │ first!      │    │ app (banking, medical)? │
    └─────────────┘    └─────────────────────────┘
                                 │
                      ┌──────────┴──────────┐
                      │                     │
                     Yes                   No
                      │                     │
                      ▼                     ▼
               ┌───────────┐         ┌───────────┐
               │ Use       │         │ Do users  │
               │ Strict    │         │ share     │
               └───────────┘         │ links?    │
                                     └───────────┘
                                           │
                                ┌──────────┴──────────┐
                                │                     │
                               Yes                   No
                                │                     │
                                ▼                     ▼
                         ┌───────────┐         ┌───────────┐
                         │ Use       │         │ Use       │
                         │ Lax       │         │ Strict    │
                         └───────────┘         └───────────┘
```

### Quick Reference

| Scenario | Recommendation |
|----------|---------------|
| Consumer app with social sharing | Lax |
| Internal admin panel | Strict |
| Banking/healthcare | Strict |
| Third-party embed/widget | None + CSRF tokens |
| OAuth provider | None + state parameter |
| General web app | Lax (browser default) |

---

## Common Mistakes

### Mistake 1: GET Endpoints That Modify State

```java
// BAD - violates REST, vulnerable with Lax
@GetMapping("/unsubscribe")
public void unsubscribe(@RequestParam String email) {
    userService.unsubscribe(email);
}

// GOOD - use POST for state changes
@PostMapping("/unsubscribe")
public void unsubscribe(@RequestBody UnsubscribeRequest request) {
    userService.unsubscribe(request.getEmail());
}
```

### Mistake 2: Assuming Strict Means "Fully Protected"

Even with `SameSite=Strict`, you still need:
- HTTPS (`Secure` flag)
- `HttpOnly` to prevent XSS stealing cookies
- Proper session management
- Input validation

### Mistake 3: Using None Without Secure

```java
// BROKEN - browsers reject this
.sameSite("None")
// Missing: .secure(true)

// CORRECT
.sameSite("None")
.secure(true)
```

### Mistake 4: Forgetting About Subdomains

SameSite treats `app.example.com` and `api.example.com` as same-site. If you need cookies shared across subdomains, set `Domain=.example.com`.

### Mistake 5: Not Testing Cross-Site Scenarios

Always test:
1. Clicking a link from a different domain
2. Submitting a form from a different domain
3. AJAX requests from a different domain

---

## Summary

| Attribute | Security Level | UX Impact | Best For |
|-----------|---------------|-----------|----------|
| Strict | Highest | Poor (external links broken) | High-security apps |
| Lax | High | Good (links work) | Most web apps |
| None | Low (needs other defenses) | Best | Cross-domain scenarios |

**Key Takeaway:** For most applications, `SameSite=Lax` provides excellent CSRF protection with good user experience, as long as you follow REST principles (GET = read-only, POST/PUT/DELETE = state changes).

---

## References

- [MDN: SameSite cookies](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie/SameSite)
- [OWASP: SameSite Cookie Attribute](https://owasp.org/www-community/SameSite)
- [Chromium: SameSite Updates](https://www.chromium.org/updates/same-site/)
- [RFC 6265bis: Cookies](https://datatracker.ietf.org/doc/html/draft-ietf-httpbis-rfc6265bis)
