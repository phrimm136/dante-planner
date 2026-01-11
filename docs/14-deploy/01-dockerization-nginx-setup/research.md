# Dockerization Research Findings

## Spec Ambiguities (BLOCKING)

**Critical decisions needed before implementation:**

1. **Device ID Integration with Rate Limiting**
   - DeviceIdArgumentResolver.java exists and extracts X-Device-ID header
   - But how should it integrate with ClientIpResolver for rate limiting?
   - Current: Rate limiting only uses IP from ClientIpResolver
   - Needed: Access to device ID when IP is private

2. **Trusted Proxy Default Values**
   - Current default: TRUSTED_PROXY_IPS=127.0.0.1
   - Docker nginx IP: 172.18.0.2 (bridge network)
   - Should default include Docker bridge network or require .env override?

3. **OAuth Redirect URI Strategy**
   - Current: http://localhost:5173/auth/callback/google (hardcoded)
   - Docker: http://localhost/auth/callback/google (port 80 via nginx)
   - Should this be environment-variable driven or change default?

4. **Rate Limit Bucket Key Prefix**
   - Spec suggests: "ip:192.168.1.1" or "device:abc123"
   - Current pattern: "auth:192.168.1.1" (mixed prefix strategy)
   - Desired format for consistency?

## Spec-to-Code Mapping

**Rate Limiting Infrastructure:**
- ClientIpResolver.java: Add resolveClientIdentifier() method
- Add isPrivateIp() for Docker NAT detection
- Add CF-Connecting-IP header support
- RateLimitConfig.java: Update bucket key format to use identifier

**Environment Configuration:**
- Create application-dev.properties (Docker defaults)
- Create application-prod.properties (production overrides)
- Update CORS_ALLOWED_ORIGINS to support Docker port 80

**Docker Infrastructure (New Files):**
- docker-compose.yml: Orchestrate nginx, backend, mysql
- backend/Dockerfile: Multi-stage Maven build
- frontend/Dockerfile: Multi-stage Vite build
- nginx/nginx.conf: Reverse proxy with header forwarding
- .env.example: Document all required variables

## Spec-to-Pattern Mapping

**Rate Limit Identifier Pattern:**
- Reference: RateLimitConfig.checkAuthLimit uses "auth:" + clientIp
- Generalize to: identifier + ":" + bucketType format
- All endpoints use unified identifier resolution

**IP Resolution Pattern:**
- Reference: ClientIpResolver.resolve() with trusted proxy validation
- Extend with: Header precedence (CF-Connecting-IP > X-Forwarded-For)
- Add: Private IP detection and device ID fallback

**Spring Profile Pattern:**
- Reference: SecurityProperties uses @ConfigurationProperties
- Apply to: Create environment-specific property files
- Pattern: application.properties (shared), application-{profile}.properties (overrides)

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| backend/Dockerfile | Spring Boot Docker docs | Multi-stage: Maven build → JRE slim runtime |
| frontend/Dockerfile | Vite Docker examples | Multi-stage: Node build → nginx serve |
| docker-compose.yml | Docker Compose docs | Health checks, depends_on conditions, volume mounts |
| nginx/nginx.conf | Nginx reverse proxy docs | proxy_set_header patterns, upstream config |
| application-dev.properties | application.properties | Environment variable override pattern |

| Modified File | MUST Read First | Pattern to Follow |
|---------------|-----------------|-------------------|
| ClientIpResolver.java | Current implementation | Extend existing resolve() logic, don't rewrite |
| RateLimitConfig.java | Current bucket strategies | Maintain Bucket4j pattern, change key format only |

## Cross-Reference Validation

| Layer | Reference Contract | Actual Usage | Match | Issue |
|-------|-------------------|--------------|-------|-------|
| IP Resolution | resolve(request, proxies): String | Used by RateLimitConfig | ✓ | None |
| Private IP Detection | MISSING | Spec requires 10.x, 172.16-31.x, 192.168.x ranges | ✗ | Must create |
| Device ID Extraction | DeviceIdArgumentResolver | Needs integration with ClientIpResolver | ? | Verify implementation |
| Rate Limit Bucket Key | "auth:" + ip format | Inconsistent with spec ("ip:"/"device:" prefix) | ✗ | Needs unification |
| Trusted Proxies | Default: 127.0.0.1 | Docker nginx: 172.18.0.2 | ✗ | Default won't work |
| OAuth Redirect | Hardcoded: :5173 | Docker needs: :80 | ✗ | Breaks OAuth |

## Existing Utilities

**Rate Limiting:**
- ✓ RateLimitConfig.java - Bucket4j wrapper (extend)
- ✓ ClientIpResolver.java - IP extraction (modify)
- ✓ SecurityProperties.java - Trusted proxy config (reuse)

**IP Detection:**
- ✗ Private IP range detection - CREATE NEW
- ✓ IPv4/IPv6 validation patterns - Reuse from ClientIpResolver

**Device Identification:**
- ✓ DeviceIdArgumentResolver.java - Extracts X-Device-ID header
- ? Integration with rate limiting - Needs verification

**Docker Configuration:**
- ✗ No existing Docker files
- ✗ No existing nginx configuration
- ✗ No .env.example

## Gap Analysis

**Currently Missing:**
- Private IP detection utility
- resolveClientIdentifier() method in ClientIpResolver
- Unified rate limit bucket key strategy
- Docker Compose orchestration files
- Nginx reverse proxy configuration
- Environment variable documentation
- Docker-specific Spring profiles

**Needs Modification:**
- ClientIpResolver: Add identifier resolution with device ID fallback
- RateLimitConfig: Update to use identifiers instead of raw IPs
- application.properties: Add Docker defaults for TRUSTED_PROXY_IPS
- CORS configuration: Include http://localhost for Docker

**Can Reuse:**
- Existing IP parsing logic in ClientIpResolver
- Bucket4j rate limiting infrastructure
- Spring Actuator health endpoints
- Flyway migration system
- Environment variable substitution pattern

## Testing Requirements

### Unit Tests

**ClientIpResolverTest.java:**
- isPrivateIp("10.0.0.1") → true
- isPrivateIp("172.18.0.2") → true
- isPrivateIp("192.168.1.1") → true
- isPrivateIp("203.0.113.1") → false
- resolveClientIdentifier() with private IP + device ID → "device:abc123"
- resolveClientIdentifier() with public IP → "ip:203.0.113.1"
- resolveClientIdentifier() with CF-Connecting-IP → prefers that header

**RateLimitConfigTest.java:**
- Bucket created with identifier "ip:192.168.1.1"
- Different identifiers create separate buckets
- Same identifier reuses bucket across requests

### Integration Tests

**Docker Network Communication:**
- Backend connects to mysql:3306 hostname
- Nginx forwards X-Forwarded-For to backend
- Backend receives correct headers from nginx

**Rate Limiting in Docker:**
- Request with Docker NAT IP uses device ID fallback
- Request with public IP uses IP-based limiting
- Multiple devices get separate rate limit buckets

**Data Persistence:**
- MySQL data survives docker-compose down/up cycle
- Flyway migrations run exactly once on first startup

## Technical Constraints

**Docker Networking:**
- Default bridge network: 172.18.0.0/16
- Container hostnames: mysql, backend, nginx
- Internal ports: mysql:3306, backend:8080
- External port: nginx:80 → host:80

**Spring Boot:**
- Version 4.0.0-SNAPSHOT
- Java 17 runtime required
- Embedded Tomcat on port 8080
- Spring profiles for environment separation

**MySQL:**
- Version 8.0
- Default charset: utf8mb4
- Collation: utf8mb4_unicode_ci
- Health check: mysqladmin ping

**Vite Frontend:**
- Build output: frontend/dist/
- Static assets from: ../static (publicDir)
- Environment variables prefixed: VITE_*
- Build time: 1-2 minutes

**Nginx:**
- Alpine-based image (minimal)
- Reverse proxy for /api/*
- Static file server for frontend
- Header forwarding required

## Implementation Priority

**Phase 1: Core Rate Limiting Fix (BLOCKING)**
1. Add private IP detection to ClientIpResolver
2. Add resolveClientIdentifier() method
3. Update RateLimitConfig to use identifiers
4. Write unit tests for new logic

**Phase 2: Docker Configuration**
1. Create docker-compose.yml with health checks
2. Write backend Dockerfile (multi-stage)
3. Write frontend Dockerfile (multi-stage)
4. Configure nginx.conf with proxy headers

**Phase 3: Environment Management**
1. Create application-dev.properties
2. Create application-prod.properties
3. Create .env.example
4. Update .gitignore

**Phase 4: Testing and Validation**
1. Test full Docker stack locally
2. Verify rate limiting with curl
3. Test OAuth flow
4. Verify data persistence

## Risks Identified

**High Risk:**
- Rate limiting completely broken if IP detection wrong
- All users share one bucket (denial of service)
- OAuth breaks if callback URL not updated

**Medium Risk:**
- CORS errors if nginx duplicates headers
- MySQL startup race condition
- Secrets leaked if .env committed

**Low Risk:**
- Log disk space (mitigated by rotation)
- Container restart delay (<30s acceptable)
- Image size bloat (multi-stage builds minimize this)

---

## Clarifications Resolved

**User Decisions (2026-01-11):**

1. **Rate Limiting Strategy:**
   - DECISION: Fallback to X-Device-ID header when IP is private
   - Implementation: resolveClientIdentifier() detects private IPs → returns "device:xxx"
   - Bucket isolation: Each device gets separate bucket even behind Docker NAT

2. **Trusted Proxy Configuration:**
   - DECISION: Require .env override (explicit security)
   - Implementation: TRUSTED_PROXY_IPS must include 172.18.0.0/16 in .env
   - Benefit: Developers learn proxy trust concept, better production awareness

3. **OAuth Callback URL:**
   - DECISION: Environment variable driven
   - Implementation: GOOGLE_OAUTH_REDIRECT_URI in .env
   - Values: Dev: http://localhost/auth/callback/google, Prod: https://yourdomain.com/auth/callback/google

4. **Rate Limit Bucket Key Format:**
   - DECISION: Unified format: identifier + ":" + bucketType
   - Examples: "ip:203.0.113.1:auth", "device:abc123:comment"
   - Consistency: All rate limit endpoints use same pattern

**Implementation Ready:** All ambiguities resolved, proceed to coding phase.
