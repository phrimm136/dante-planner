# Cloudflare Deployment Execution Plan

## Planning Gaps

None. Research complete. R2 storage explicitly deferred.

---

## Execution Overview

Configure Cloudflare as DNS provider and frontend hosting for dante-planner.com:
- Phase 1: External Setup (Cloudflare Dashboard, Namecheap)
- Phase 2: Code Changes (properties, env files)
- Phase 3: OAuth Configuration (Google Console)
- Phase 4: Verification

Architecture:
- `dante-planner.com` → Cloudflare Pages (frontend)
- `api.dante-planner.com` → EC2 via Cloudflare proxy (backend)
- Cookies shared via `.dante-planner.com` domain

---

## Dependency Analysis

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `frontend/.env.production` (CREATE) | Low | None | Vite build, `lib/env.ts` |
| `application-prod.properties` | Medium | `application.properties` | `CorsConfig.java`, `CookieUtils.java` |

### Ripple Effect Map

- `application-prod.properties` CORS changes → All cross-origin API calls affected
- `application-prod.properties` cookie changes → All authentication flows affected
- `frontend/.env.production` changes → Frontend API routing (build-time only)
- Google OAuth callback unchanged → OAuth flow breaks in production

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| `application-prod.properties` | CORS misconfiguration blocks API | Test preflight manually |
| `application-prod.properties` | Cookie domain wrong prevents auth | Verify leading dot in `.dante-planner.com` |
| Google Console | Wrong callback URL breaks login | Add production URL, keep localhost |

---

## Execution Order

### Phase 1: External Setup (Manual)

**Step 1.1: Create Cloudflare Account**
- Sign up at dash.cloudflare.com
- Add site: dante-planner.com
- Select Free plan
- Note assigned nameservers

**Step 1.2: Migrate DNS from Namecheap**
- Namecheap → Domain List → Manage → Custom DNS
- Enter Cloudflare nameservers
- Wait for propagation (5 min - 48 hours)
- Verify "Active" status in Cloudflare

**Step 1.3: Configure Cloudflare Pages**
- Dashboard → Pages → Create project
- Connect GitHub → LimbusPlanner repository
- Build command: `cd frontend && yarn install --frozen-lockfile && yarn build`
- Output directory: `frontend/dist`
- Environment variables:
  - `YARN_VERSION=1` (pins Yarn Classic; avoids Berry/lockfile incompatibility)
  - `VITE_API_BASE_URL=https://api.dante-planner.com`

**Step 1.4: DNS Records**
- CNAME @ → [project].pages.dev (Proxied)
- CNAME www → dante-planner.com (Proxied)
- A api → [EC2 Elastic IP] (Proxied)

**Step 1.5: SSL/TLS Configuration**
- SSL mode: Full (Strict)
- Enable "Always Use HTTPS"
- Minimum TLS: 1.2

### Phase 2: Code Changes

**Step 2.1: Create `frontend/.env.production`**
- VITE_API_BASE_URL=https://api.dante-planner.com
- VITE_GOOGLE_CLIENT_ID=[existing client ID]
- Pattern source: `frontend/.env`

**Step 2.2: Update `application-prod.properties`**
- cors.allowed-origins=https://dante-planner.com
- cookie.domain=.dante-planner.com
- cookie.same-site=Lax
- cookie.secure=true
- oauth.google.redirect-uri=https://dante-planner.com/auth/callback/google
- Pattern source: `application-dev.properties`

### Phase 3: OAuth Configuration

**Step 3.1: Update Google Cloud Console**
- APIs & Services → Credentials → OAuth 2.0 Client
- Add: https://dante-planner.com/auth/callback/google
- Keep: http://localhost:5173/auth/callback/google

### Phase 4: Verification

**Step 4.1: DNS Verification**
- `dig dante-planner.com` → Cloudflare IPs
- `dig api.dante-planner.com` → Cloudflare IPs

**Step 4.2: Frontend Verification**
- https://dante-planner.com loads React app
- Network tab shows `cf-ray` header

**Step 4.3: API Verification**
- Requests go to api.dante-planner.com
- No CORS errors in console

**Step 4.4: OAuth Verification**
- Google Sign In completes
- Cookie set with `.dante-planner.com` domain

---

## Verification Checkpoints

| Checkpoint | When | How |
|------------|------|-----|
| DNS Active | After 1.2 | Cloudflare shows "Active" |
| Pages Deployed | After 1.3 | Visit [project].pages.dev |
| Custom Domain | After 1.4 | Visit dante-planner.com |
| SSL Working | After 1.5 | Browser HTTPS lock |
| API Reachable | After 2.2 deploy | curl api endpoint |
| CORS Working | After 2.2 deploy | No console errors |
| OAuth Working | After 3.1 | Login succeeds |

---

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| DNS propagation delay | 1.2 | Allow 48 hours; check with `dig` |
| Yarn Berry lockfile error | 1.3 | Set `YARN_VERSION=1` env var |
| Pages build fails | 1.3 | Test locally: `yarn build` |
| CORS blocks API | 2.2 | Match exact URL (no trailing slash) |
| Cookie not shared | 2.2 | Leading dot in `.dante-planner.com` |
| OAuth mismatch | 3.1 | Add HTTPS before removing HTTP |
| EC2 blocks Cloudflare | 1.4 | Allow Cloudflare IP ranges |

---

## Rollback Strategy

| If Fails | Action |
|----------|--------|
| 1.2 DNS migration | Revert Namecheap to "BasicDNS" |
| 1.3 Pages build | No rollback needed |
| 2.2 Backend config | Revert properties; redeploy |
| 3.1 OAuth | Remove production URL from Console |

---

## Not In Scope (Deferred)

- R2 bucket creation
- R2Config.java / StorageService.java
- AWS S3 SDK dependency
- uploads.dante-planner.com subdomain
