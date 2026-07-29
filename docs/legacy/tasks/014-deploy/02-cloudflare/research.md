# Cloudflare Deployment Research

## Clarifications Resolved

| Question | Decision | Rationale |
|----------|----------|-----------|
| OAuth callback timing | Add production URL now | Google Console allows multiple URIs (localhost + production) |
| Cookie domain | `.dante-planner.com` with `SameSite=Lax` | Subdomains share registrable domain = same-site |
| Upload endpoint | Defer to later | Not needed for Phase 2 core deployment |
| R2 error handling | Simple error response | Client can retry manually; no backend retry logic |

---

## Spec-to-Code Mapping

**Frontend Environment:**
- Create `frontend/.env.production` with `VITE_API_BASE_URL=https://api.dante-planner.com`
- Vite auto-loads based on NODE_ENV during build

**Backend CORS:**
- Modify `application-prod.properties`: uncomment `cors.allowed-origins=https://dante-planner.com`
- `CorsConfig.java` already reads from properties, splits on comma, enables credentials

**Backend Cookie Configuration:**
- Modify `application-prod.properties`: set `cookie.domain=.dante-planner.com`, `cookie.same-site=Lax`, `cookie.secure=true`
- `CookieUtils.java` already supports all three properties via `@Value` injection

**R2 Storage (Deferred):**
- `R2Config.java` - Not needed in Phase 2
- `StorageService.java` - Not needed in Phase 2
- AWS S3 SDK dependency - Not needed in Phase 2

**OAuth Configuration:**
- Update Google Cloud Console: add `https://dante-planner.com/auth/callback/google`
- Keep `http://localhost:5173/auth/callback/google` for development

---

## Spec-to-Pattern Mapping

- Cookie configuration: Follows existing `CookieUtils.java` property injection pattern
- CORS configuration: Follows existing `CorsConfig.java` `@Value` pattern
- Environment files: Follows Vite convention (`.env.{mode}`)

---

## Pattern Enforcement

| File to Modify | MUST Read First | Pattern Reference |
|----------------|-----------------|-------------------|
| `application-prod.properties` | `application.properties`, `application-dev.properties` | Property naming, comment style |
| `frontend/.env.production` | `frontend/.env` | Variable naming with VITE_ prefix |

---

## Existing Utilities (Verified)

| Category | Location | Status |
|----------|----------|--------|
| Cookie handling | `util/CookieUtils.java` | Exists, configurable via properties |
| CORS config | `config/CorsConfig.java` | Exists, reads from properties |
| Security config | `config/SecurityConfig.java` | Exists, no changes needed |

---

## Gap Analysis

**Currently Missing (Phase 2 Scope):**
- `frontend/.env.production` - Must create
- Production values in `application-prod.properties` - Must uncomment/update

**Deferred to Later:**
- R2Config.java
- StorageService.java
- AWS S3 SDK dependency
- Upload endpoint

**Can Reuse:**
- CookieUtils property injection
- CorsConfig property reading
- GlobalExceptionHandler for any future errors

---

## Testing Requirements

### Manual UI Tests
- Navigate to https://dante-planner.com → React frontend loads
- Check Network tab → API calls go to api.dante-planner.com
- Verify cf-ray header present (confirms Cloudflare proxy)
- Complete Google OAuth flow → redirect works, cookie set
- Navigate authenticated pages → API calls include cookie
- Test in incognito → public pages load without auth

### Automated Tests
- DNS resolution: `dig dante-planner.com` returns Cloudflare IPs
- SSL certificate: Valid HTTPS in browser
- CORS preflight: OPTIONS returns correct headers
- Cookie attributes: Domain, SameSite, Secure correct

---

## Technical Constraints

- Cloudflare nameserver propagation: 5 min to 48 hours
- SSL certificate provisioning: Automatic, 5-30 minutes
- Cookie domain must start with `.` for subdomain sharing
- `SameSite=Lax` sufficient for subdomain architecture (not None)
- HTTPS required in production (`cookie.secure=true`)

---

## Implementation Order

1. Create Cloudflare account, add site
2. Update Namecheap nameservers
3. Wait for "Active" status
4. Configure Cloudflare Pages (GitHub integration)
5. Set DNS records (CNAME for Pages, A for future API)
6. Create `frontend/.env.production`
7. Update `application-prod.properties` (CORS + cookies)
8. Update Google Cloud Console (add production redirect URI)
9. Deploy and verify
