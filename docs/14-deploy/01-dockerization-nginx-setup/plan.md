# Dockerization Execution Plan

## Planning Gaps (STOP if any)

No blocking gaps. All critical decisions resolved in research.md:
- Rate limiting strategy: Device ID fallback for private IPs
- Trusted proxy configuration: Explicit .env override required
- OAuth redirect URI: Environment variable driven
- Rate limit bucket key format: Unified "identifier:bucketType" pattern

## Execution Overview

Dockerize the application by containerizing three services (nginx, backend, mysql) with Docker Compose orchestration. Core challenge is fixing rate limiting to work in containerized environments where Docker NAT causes all requests to appear from nginx's IP. Strategy involves detecting private IPs, falling back to device ID for rate limit buckets, and ensuring proper header forwarding through nginx reverse proxy.

Implementation follows four phases: (1) Fix backend rate limiting for Docker NAT, (2) Create Docker infrastructure files, (3) Configure environment management, (4) Test full stack integration.

## Dependency Analysis (Senior Thinking)

From instructions.md Impact Analysis and architecture-map.md:

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| ClientIpResolver.java | High | SecurityProperties.java | RateLimitConfig.java, all rate-limited controllers |
| RateLimitConfig.java | High | ClientIpResolver.java | AuthController, PlannerController, CommentController, PlannerSseService |
| application.properties | Medium | None | All Spring Boot components, SecurityProperties, RateLimitConfig |
| .gitignore | Low | None | Git version control |

### Ripple Effect Map

- If ClientIpResolver changes → All rate-limited endpoints must be tested (auth, planner CRUD, comments, SSE)
- If RateLimitConfig bucket key format changes → Existing in-memory buckets invalidated (acceptable, causes temporary rate limit reset)
- If application.properties CORS changes → Frontend API calls may fail with CORS errors
- If OAuth redirect URI changes → Google OAuth flow breaks unless Console config updated

### High-Risk Modifications

- **ClientIpResolver.java**: Incorrect private IP detection = all Docker users share one rate limit bucket (denial of service). Mitigation: Comprehensive unit tests for all private IP ranges.
- **RateLimitConfig.java**: Wrong identifier format = bucket collisions between IP and device IDs. Mitigation: Explicit prefixes ("ip:", "device:").
- **application.properties**: Wrong TRUSTED_PROXY_IPS = backend accepts spoofed headers. Mitigation: Require explicit .env configuration, no permissive defaults.

## Execution Order

### Phase 1: Backend Rate Limiting Fix (Docker NAT Support)

1. **ClientIpResolver.java - Add private IP detection**
   - Depends on: None
   - Enables: F1 (Private IP detection for 10.x, 172.16-31.x, 192.168.x ranges)
   - Add isPrivateIp() method with RFC 1918 ranges
   - Add CF-Connecting-IP header support

2. **ClientIpResolver.java - Add resolveClientIdentifier() method**
   - Depends on: Step 1
   - Enables: F2 (Device ID fallback), F3 (Unified identifier format)
   - Check CF-Connecting-IP → X-Forwarded-For → RemoteAddr
   - Return "ip:xxx" for public IPs, "device:xxx" for private IPs with device ID

3. **RateLimitConfig.java - Update bucket key format**
   - Depends on: Step 2
   - Enables: F4 (Consistent rate limiting across endpoints)
   - Change checkAuthLimit() to accept identifier instead of IP
   - Update bucket key format to "identifier:bucketType"

4. **Write unit tests for ClientIpResolver**
   - Depends on: Step 1, 2
   - Enables: Test coverage for F1, F2, F3
   - Test isPrivateIp() with all RFC 1918 ranges
   - Test resolveClientIdentifier() with Docker NAT scenarios

5. **Write unit tests for RateLimitConfig**
   - Depends on: Step 3
   - Enables: Test coverage for F4
   - Verify bucket isolation by identifier
   - Verify bucket key format consistency

### Phase 2: Docker Infrastructure Files

6. **Create docker-compose.yml**
   - Depends on: None (infrastructure setup)
   - Enables: F5 (Multi-service orchestration), F6 (Health checks)
   - Define mysql, backend, nginx services
   - Configure depends_on with health checks
   - Set up named volumes for MySQL data persistence

7. **Create backend/Dockerfile**
   - Depends on: None
   - Enables: F7 (Multi-stage build for backend)
   - Stage 1: Maven build with maven:3.9-eclipse-temurin-17
   - Stage 2: JRE runtime with eclipse-temurin:17-jre-alpine
   - Copy only built JAR to minimize image size

8. **Create frontend/Dockerfile**
   - Depends on: None
   - Enables: F8 (Multi-stage build for frontend)
   - Stage 1: Node build with node:20-alpine
   - Stage 2: nginx:alpine serve static files
   - Copy dist/ and static/ to nginx html directory

9. **Create nginx/nginx.conf**
   - Depends on: None
   - Enables: F9 (Reverse proxy), F10 (Header forwarding)
   - Proxy /api/* to backend:8080
   - Forward X-Forwarded-For, X-Forwarded-Proto, CF-Connecting-IP
   - Serve static files from /usr/share/nginx/html

10. **Create nginx/Dockerfile**
    - Depends on: Step 9
    - Enables: F9 (Custom nginx config)
    - FROM nginx:alpine
    - Copy nginx.conf to /etc/nginx/nginx.conf

11. **Create .dockerignore files**
    - Depends on: None
    - Enables: F11 (Faster builds, smaller context)
    - Root: Exclude node_modules, target, .git, logs
    - Backend: Exclude target, .mvn
    - Frontend: Exclude node_modules, dist

### Phase 3: Environment Configuration

12. **Create .env.example**
    - Depends on: None
    - Enables: F12 (Documentation), F13 (Developer onboarding)
    - Document all required variables (JWT_SECRET, MYSQL_*, GOOGLE_OAUTH_*, TRUSTED_PROXY_IPS)
    - Provide example values (NOT production secrets)

13. **Create backend/src/main/resources/application-dev.properties**
    - Depends on: None
    - Enables: F14 (Docker development defaults)
    - Set MYSQL_HOST=mysql (Docker hostname)
    - Set CORS_ALLOWED_ORIGINS=http://localhost

14. **Create backend/src/main/resources/application-prod.properties**
    - Depends on: None
    - Enables: F15 (Production overrides)
    - Placeholder for production-specific configs
    - Document need for HTTPS CORS origins

15. **Update .gitignore**
    - Depends on: Step 12
    - Enables: F16 (Secret protection)
    - Add .env to gitignore
    - Add docker-compose.override.yml
    - Add .dockerignore patterns

### Phase 4: Documentation

16. **Create docs/14-deploy/environment-setup.md**
    - Depends on: Steps 12-15
    - Enables: F17 (Operations guide)
    - Document environment variable meanings
    - Explain Docker network architecture
    - Troubleshooting common issues

17. **Create README-DOCKER.md**
    - Depends on: Steps 6-11
    - Enables: F18 (Quick start guide)
    - Step-by-step setup instructions
    - Local development workflow
    - Production deployment checklist

## Test Steps (MANDATORY)

### Unit Tests (Part of Phase 1)

- **Step 4**: Write ClientIpResolverTest.java
  - Test isPrivateIp() for all RFC 1918 ranges (10.x, 172.16-31.x, 192.168.x)
  - Test resolveClientIdentifier() with public IP → returns "ip:xxx"
  - Test resolveClientIdentifier() with private IP + device ID → returns "device:xxx"
  - Test CF-Connecting-IP header precedence over X-Forwarded-For

- **Step 5**: Write RateLimitConfigTest.java
  - Test bucket isolation: different identifiers create separate buckets
  - Test bucket key format: verify "identifier:bucketType" pattern
  - Test bucket reuse: same identifier reuses bucket across requests

### Integration Tests (After Phase 2)

- **After Step 11**: Docker Compose Integration Tests
  - Verify mysql container health check passes
  - Verify backend connects to mysql:3306
  - Verify nginx forwards headers to backend
  - Verify Flyway migrations run exactly once

### Manual Verification (After Phase 4)

- **After Step 17**: Full Stack Manual Tests
  - Execute all tests from instructions.md "Testing Guidelines"
  - Verify rate limiting with Docker NAT IP uses device ID
  - Verify OAuth flow works with new redirect URI
  - Verify data persists across docker-compose down/up

## Verification Checkpoints

- After Step 3: Verify F1-F4 - Rate limiting works with device ID fallback
- After Step 5: Verify test coverage >80% for ClientIpResolver, RateLimitConfig
- After Step 11: Verify F5-F11 - Docker stack starts successfully
- After Step 15: Verify F12-F16 - Environment variables documented and protected
- After Step 17: Verify F17-F18 - Documentation complete and accurate

## Risk Mitigation (from instructions.md Risk Assessment)

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| Private IP detection wrong | Step 1 | Comprehensive unit tests for all RFC 1918 ranges + edge cases |
| Device ID not extracted | Step 2 | Verify DeviceIdArgumentResolver integration, test fallback to "unknown" |
| Bucket key collisions | Step 3 | Explicit "ip:" and "device:" prefixes prevent collisions |
| OAuth callback breaks | Step 13 | Document Google Console update requirement in README-DOCKER.md |
| CORS errors | Step 13 | Test CORS with curl before frontend integration |
| MySQL startup race | Step 6 | depends_on with health check condition ensures MySQL ready first |
| Secrets leaked | Step 15 | Add .env to .gitignore BEFORE creating .env.example |
| Rate limit bypass | Steps 1-3 | Require TRUSTED_PROXY_IPS in .env (no permissive default) |

## Pre-Implementation Validation Gate

**BEFORE Step 1 execution, verify research completed:**

| Validation Category | Check | Blocker if Missing |
|---------------------|-------|-------------------|
| **Reference Completeness** | Read ClientIpResolver.java, RateLimitConfig.java, SecurityProperties.java | YES |
| **Contract Alignment** | Understand resolve() signature, bucket key format, trusted proxy validation | YES |
| **Dependency Resolution** | DeviceIdArgumentResolver exists and extracts X-Device-ID header | YES |
| **Structure Documentation** | Private IP ranges documented (10.x, 172.16-31.x, 192.168.x) | YES |
| **Difference Justification** | Changing bucket key format from "auth:ip" to "identifier:bucketType" justified | NO (documented in research.md) |

All checks passed during research phase.

## Dependency Verification Steps

After modifying shared files, verify consumers still work:

- After Step 3: Test all rate-limited endpoints (auth, planner, comment, SSE)
  - curl -H "X-Forwarded-For: 172.18.0.2" -H "X-Device-ID: test-device" http://localhost/api/planner/md/published
  - Verify 200 OK (device ID used for rate limiting)
  - Verify separate buckets for different device IDs

- After Step 13: Test frontend API calls
  - Verify CORS allows http://localhost origin
  - Verify no duplicate CORS headers in response
  - Test OAuth flow with http://localhost/auth/callback/google

- After Step 11: Verify Docker network communication
  - Backend logs show "Connected to mysql:3306"
  - Nginx access logs show forwarded headers
  - Backend actuator/health returns UP

## Rollback Strategy

- **Step 1-3 fails**: Revert ClientIpResolver.java and RateLimitConfig.java to HEAD
  - Safe stopping point: Rate limiting works with current IP-only logic
  - No data loss, no breaking changes

- **Step 6-11 fails**: Remove Docker files, continue local development
  - Safe stopping point: Developers use ./mvnw spring-boot:run as before
  - No impact on existing workflows

- **Step 12-15 fails**: Delete invalid .env files, use application.properties defaults
  - Safe stopping point: Application runs with localhost defaults
  - No secrets compromised
