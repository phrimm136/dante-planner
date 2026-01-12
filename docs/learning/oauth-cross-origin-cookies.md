# OAuth and Cross-Origin Cookie Debugging

> **Date:** 2026-01-12
> **Context:** Debugging Google OAuth 400 error after nginx deployment, then solving cross-origin cookie issues for Cloudflare frontend deployment

---

## Problem Summary

Two sequential issues encountered after infrastructure changes:

1. **OAuth `redirect_uri_mismatch`** - After adding nginx reverse proxy
2. **Cross-origin cookie failure** - Frontend on Cloudflare, backend on separate server

---

## Issue 1: OAuth redirect_uri_mismatch

### Symptoms

```
POST /api/auth/google/callback → 400
Response: {"error": "redirect_uri_mismatch", "error_description": "Bad Request"}
```

### Root Cause

Google OAuth requires the `redirect_uri` to match **exactly** in three places:

| Location | Value |
|----------|-------|
| Frontend (authorization request) | `window.location.origin + '/auth/callback/google'` |
| Backend (token exchange) | `oauth.google.redirect-uri` from config |
| Google Cloud Console | Whitelisted redirect URIs |

The mismatch occurred because:
- Frontend used dynamic origin: `http://localhost:5173/auth/callback/google`
- Backend env var was set to: `http://localhost/auth/callback/google` (port 80)

### Debugging Steps

1. **Check browser Network tab** - Confirmed request payload was valid JSON with all required fields
2. **Check response body** - Generic error, needed more detail
3. **Add debug logging to OAuth provider:**

```java
} catch (HttpStatusCodeException e) {
    // Log Google's actual error response
    log.error("Google OAuth failed. Status: {}, Response: {}",
            e.getStatusCode(), e.getResponseBodyAsString());
    throw new OAuthException(...);
} catch (RestClientException e) {
    // Generic fallback
}
```

4. **Check Docker environment:**
```bash
docker exec danteplanner-backend printenv | grep GOOGLE_OAUTH_REDIRECT_URI
```

### Solution

Use Spring Profiles to set environment-specific redirect URIs:

**application-dev.properties:**
```properties
oauth.google.redirect-uri=http://localhost:5173/auth/callback/google
```

**Key Learning:** Environment variables override profile properties. Remove the env var from Docker Compose to let profile config apply.

---

## Issue 2: Cross-Origin Cookie Failure

### Symptoms

- Login succeeds (cookies set)
- Logout fails silently (cookies not sent with POST request)
- Other POST/PUT/DELETE requests fail authentication

### Root Cause

Different origins between frontend and backend:

| Component | Origin |
|-----------|--------|
| Frontend (Vite dev) | `http://localhost:5173` |
| Backend (nginx) | `http://localhost` (port 80) |

With `SameSite=Lax`:
- **GET requests**: Cookies sent (top-level navigation)
- **POST/PUT/DELETE**: Cookies **NOT sent** (cross-origin)

### Cookie Behavior Reference

| SameSite | Same-Origin | Cross-Origin GET | Cross-Origin POST |
|----------|-------------|------------------|-------------------|
| `Strict` | Sent | Not sent | Not sent |
| `Lax` | Sent | Sent (navigation) | Not sent |
| `None` | Sent | Sent | Sent (requires Secure) |

### Solutions

#### Development: Vite Proxy (Same-Origin)

Make API calls appear same-origin by proxying through Vite:

**vite.config.ts:**
```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://localhost',
      changeOrigin: true,
    },
  },
},
```

Now frontend at `:5173` proxies `/api/*` to nginx, so browser sees same-origin.

#### Production: Cross-Origin Cookies (Cloudflare + Separate Backend)

For Cloudflare frontend with separate backend server:

1. **Same parent domain required:**
   ```
   Frontend: app.limbusplanner.com (Cloudflare)
   Backend:  api.limbusplanner.com (your server)
   ```

2. **Cookie configuration:**
   ```properties
   cookie.domain=.limbusplanner.com  # Shared across subdomains
   cookie.same-site=None             # Allow cross-origin
   cookie.secure=true                # Required for SameSite=None
   ```

3. **CORS must allow credentials:**
   ```properties
   cors.allowed-origins=https://app.limbusplanner.com
   ```

### Implementation

Made `CookieUtils` configurable:

```java
public CookieUtils(
        @Value("${cookie.secure:true}") boolean secureCookies,
        @Value("${cookie.domain:}") String cookieDomain,
        @Value("${cookie.same-site:Lax}") String sameSite) {
    // ...
}

public void setCookie(...) {
    cookie.setAttribute("SameSite", sameSite);
    if (cookieDomain != null && !cookieDomain.isEmpty()) {
        cookie.setDomain(cookieDomain);
    }
    // ...
}
```

---

## Spring Boot Configuration Priority

Understanding priority is critical for debugging config issues:

```
1. Command line arguments (highest)
2. Environment variables (GOOGLE_OAUTH_REDIRECT_URI)
3. Profile-specific properties (application-dev.properties)
4. Base properties (application.properties)
5. Default values in @Value annotations (lowest)
```

**Gotcha:** If an env var is set (even to wrong value), it overrides profile properties. Remove the env var to let profiles work.

---

## Docker Considerations

### Build vs Runtime Configuration

| Config Type | Example | Rebuild Required? |
|-------------|---------|-------------------|
| `.env` file | `GOOGLE_OAUTH_REDIRECT_URI` | No (runtime) |
| `application.properties` | `oauth.google.redirect-uri` | Yes (baked into image) |
| Source code | `CookieUtils.java` | Yes |

### Checking Container Environment

```bash
# Check what the container sees
docker exec container-name printenv | grep PATTERN

# Check logs for specific patterns
docker logs container-name 2>&1 | grep -E "oauth|cookie"
```

---

## Debugging Checklist

### OAuth Failures

1. Check browser Network tab for request/response details
2. Add logging to capture OAuth provider's actual error response
3. Verify redirect_uri matches in: frontend, backend config, Google Console
4. Check environment variables in container: `docker exec ... printenv`

### Cookie Failures

1. Check browser DevTools → Application → Cookies
2. Verify cookie attributes: Domain, Path, SameSite, Secure, HttpOnly
3. Check if request includes cookies: Network tab → Request Headers → Cookie
4. For cross-origin: ensure SameSite=None + Secure=true + same parent domain

---

## Key Takeaways

1. **OAuth redirect_uri must match exactly** - Including protocol, domain, port, and path
2. **SameSite=Lax blocks cross-origin POST cookies** - Use proxy for dev, SameSite=None for prod
3. **SameSite=None requires Secure=true** - Which requires HTTPS
4. **Cross-origin cookies need same parent domain** - `cookie.domain=.parent.com`
5. **Env vars override Spring profiles** - Remove env vars to let profiles work
6. **Always log OAuth provider responses** - Generic errors hide the real cause
7. **Vite proxy solves dev cross-origin issues** - Makes API calls same-origin from browser perspective
