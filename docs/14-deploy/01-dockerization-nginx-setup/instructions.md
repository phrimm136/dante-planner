# Task: Dockerize Application with Nginx Reverse Proxy for Production Deployment

## Description

Containerize the LimbusPlanner application using Docker Compose to enable consistent deployment across development and production environments. The system should orchestrate three services: nginx (reverse proxy), Spring Boot backend, and MySQL database.

**Key Requirements:**

1. **Multi-Service Orchestration:**
   - Nginx serves as reverse proxy and static file server
   - Spring Boot backend runs in isolated container
   - MySQL database persists data across container restarts

2. **Rate Limiting Support:**
   - Backend must correctly identify client IPs in containerized environment
   - Docker NAT causes all requests to appear from nginx container IP
   - System must fall back to device ID when X-Forwarded-For contains private IPs
   - Support for future Cloudflare CF-Connecting-IP header

3. **Data Persistence:**
   - MySQL data survives `docker-compose down && docker-compose up`
   - Use named volumes for local development
   - Support bind mounts to EBS volumes for production

4. **Health Checks and Startup Order:**
   - MySQL must be fully ready before backend starts
   - Backend must pass health check before nginx proxies traffic
   - Graceful handling of service restarts

5. **Environment Configuration:**
   - Separate configs for development and production
   - Secrets externalized via environment variables
   - Never commit `.env` file to version control
   - Clear documentation for required variables

6. **Proxy Header Forwarding:**
   - Nginx must forward X-Forwarded-For, X-Forwarded-Proto headers
   - Pass through CF-Connecting-IP for Cloudflare integration
   - No duplicate CORS headers (backend sets them, nginx passes through)

7. **Log Management:**
   - Configure log rotation to prevent disk space exhaustion
   - Logs accessible via `docker-compose logs`
   - Support for future CloudWatch integration

8. **Multi-Stage Builds:**
   - Backend: Maven build stage → minimal JRE runtime
   - Frontend: npm build stage → nginx serve
   - Minimize final image sizes (<500MB total)

## Research

**Before Implementation:**

1. **Existing Rate Limiting Logic:**
   - Read `backend/src/main/java/org/danteplanner/backend/util/ClientIpResolver.java`
   - Understand current X-Forwarded-For validation
   - Review `backend/src/main/java/org/danteplanner/backend/config/RateLimitConfig.java`
   - Check how Bucket4j creates rate limit buckets (per IP or per identifier)

2. **Spring Boot Configuration:**
   - Review `backend/src/main/resources/application.properties`
   - Understand environment variable substitution pattern
   - Check if application-dev.properties and application-prod.properties exist

3. **Frontend Build Output:**
   - Verify Vite builds to `frontend/dist/` directory
   - Check if static assets are in `/static` and copied during build
   - Confirm frontend uses environment variables (VITE_API_BASE_URL)

4. **Current Database Setup:**
   - Check if Flyway migrations are in `backend/src/main/resources/db/migration/`
   - Verify Spring Boot auto-runs Flyway on startup
   - Understand MySQL default charset/collation requirements

5. **OAuth Callback URLs:**
   - Review how Google OAuth redirect URI is configured
   - Ensure docker-compose setup won't break OAuth flow
   - Check if CORS configuration allows localhost:80

## Scope

**Files to READ for Context:**

Backend:
- `backend/src/main/java/org/danteplanner/backend/util/ClientIpResolver.java`
- `backend/src/main/java/org/danteplanner/backend/config/RateLimitConfig.java`
- `backend/src/main/java/org/danteplanner/backend/config/SecurityConfig.java`
- `backend/src/main/java/org/danteplanner/backend/config/SecurityProperties.java`
- `backend/src/main/resources/application.properties`
- `backend/pom.xml` (for Spring Boot version)

Frontend:
- `frontend/vite.config.ts`
- `frontend/package.json`
- `frontend/src/lib/api.ts` (check API base URL configuration)

Documentation:
- `docs/architecture-map.md` (cross-cutting concerns, high-impact files)
- `backend/CLAUDE.md` (backend patterns and rules)

## Target Code Area

**Files to CREATE:**

Docker Infrastructure:
- `docker-compose.yml` (root directory)
- `backend/Dockerfile` (multi-stage build)
- `frontend/Dockerfile` (multi-stage build)
- `nginx/nginx.conf`
- `nginx/Dockerfile`
- `.dockerignore` (root + backend + frontend)
- `.env.example`

Documentation:
- `docs/14-deploy/environment-setup.md`
- `README-DOCKER.md` (quick start guide)

**Files to MODIFY:**

Backend:
- `backend/src/main/java/org/danteplanner/backend/util/ClientIpResolver.java`
  - Add `resolveClientIdentifier()` method
  - Detect private IP ranges (Docker NAT)
  - Fall back to device ID for containerized environments
- `backend/src/main/java/org/danteplanner/backend/config/RateLimitConfig.java`
  - Update to use identifier instead of raw IP
- `backend/src/main/resources/application-dev.properties` (create if missing)
- `backend/src/main/resources/application-prod.properties` (create if missing)

Configuration:
- `.gitignore` (ensure `.env` is ignored)

## System Context (Senior Thinking)

**Feature Domain:** Infrastructure / Deployment
- Not a feature domain per se, but affects all domains
- Cross-cutting concern that enables production deployment

**Core Files in This Domain:**
- `ClientIpResolver.java` - High impact (used by all rate-limited endpoints)
- `SecurityConfig.java` - High impact (all authenticated requests)
- `application.properties` - Medium impact (configuration changes affect entire backend)

**Cross-Cutting Concerns Touched:**
- **Rate Limiting:** All public endpoints (RateLimitConfig.java)
- **Security:** CORS, trusted proxies, IP validation (SecurityConfig.java, SecurityProperties.java)
- **Configuration:** Environment variables (application.properties)
- **Database:** Connection pooling, Flyway migrations (MySQL container startup)
- **Logging:** Docker log drivers, log rotation

**From architecture-map.md:**
> **High-Impact Files (Modify with Care):**
> - `util/ClientIpResolver.java` - High impact (all rate-limited endpoints depend on correct IP resolution)
> - `config/RateLimitConfig.java` - High impact (all rate-limited endpoints)
> - `config/SecurityConfig.java` - High impact (all authenticated requests)
> - `config/SecurityProperties.java` - High impact (ClientIpResolver, rate limiting)

## Impact Analysis

**Files Being Modified:**

1. **ClientIpResolver.java** (HIGH IMPACT)
   - Used by: All rate-limited endpoints (auth, planner CRUD, comments, SSE)
   - What depends on it: RateLimitConfig.java, all controllers with @RateLimit
   - Ripple effects: Incorrect IP resolution = shared rate limit bucket for all users
   - Risk: Breaking rate limiting entirely if implementation is wrong

2. **RateLimitConfig.java** (HIGH IMPACT)
   - Used by: All rate-limited endpoints
   - What depends on it: Bucket4j rate limiting infrastructure
   - Ripple effects: Changes affect all users' rate limit quotas
   - Risk: Too strict = legitimate users blocked, too loose = abuse possible

3. **application.properties** (MEDIUM IMPACT)
   - Used by: Entire Spring Boot application
   - What depends on it: Database connection, CORS, JWT, OAuth, all services
   - Ripple effects: Misconfiguration can break app startup
   - Risk: Wrong database URL = no persistence, wrong CORS = frontend blocked

**Potential Ripple Effects:**

1. **Rate Limiting Bucket Keys:**
   - Current: Bucket per IP address
   - After: Bucket per identifier (ip:xxx or device:xxx)
   - Risk: Existing buckets invalidated, users might get new quotas

2. **OAuth Callback URLs:**
   - Current: http://localhost:5173/auth/callback/google
   - After: http://localhost/auth/callback/google (port 80 via nginx)
   - Risk: Google OAuth Console must be updated or OAuth breaks

3. **CORS Configuration:**
   - Current: Allows http://localhost:4173
   - After: Must allow http://localhost (nginx serves frontend)
   - Risk: Misconfig = CORS errors prevent frontend from calling API

4. **Database Connection:**
   - Current: localhost:3306
   - After: mysql:3306 (Docker container hostname)
   - Risk: Wrong hostname = backend can't connect to database

**High-Impact Files to Watch:**

From architecture-map.md:
- `config/SecurityConfig.java` - If CORS changes, must update here
- `service/PlannerSseService.java` - SSE depends on correct IP for rate limiting
- `controller/*Controller.java` - All use rate limiting, must test thoroughly

## Risk Assessment

**Edge Cases Not Yet Defined:**

1. **Docker Network Failure:**
   - What happens if nginx can't reach backend container?
   - Should nginx return 502 Bad Gateway or 503 Service Unavailable?
   - Should there be retry logic?

2. **MySQL Startup Race Condition:**
   - What if backend connects before MySQL finishes initializing?
   - Flyway might fail if database isn't ready
   - Need health check strategy

3. **Multiple Frontend Origins:**
   - Dev environment uses http://localhost:80
   - Production will use https://yourdomain.com
   - Need to support both without hardcoding

4. **Rate Limiting with Multiple Containers:**
   - What if we scale to 2+ backend containers?
   - Bucket4j in-memory buckets aren't shared across instances
   - Future problem, but worth documenting

**Performance Concerns:**

1. **Docker Networking Overhead:**
   - Extra network hop: nginx → backend container
   - Expect ~1-2ms latency increase
   - Acceptable for current traffic (10k MAU)

2. **MySQL Volume Performance:**
   - Named volumes are slower than native filesystem
   - For production, EBS gp3 volume has 125 MB/s baseline
   - Should be sufficient for 10k MAU

3. **Log Volume Growth:**
   - Without rotation, logs can fill disk
   - 10MB per service × 3 files = 30MB max per service
   - Monitor disk usage in production

**Backward Compatibility:**

1. **Local Development Workflow:**
   - Developers currently run backend with `./mvnw spring-boot:run`
   - After dockerization, they'll use `docker-compose up`
   - Must document migration path

2. **Existing .env Files:**
   - Some developers might have custom .env files
   - Need migration guide: which variables changed names?

3. **Database Schema:**
   - Flyway migrations must work in Docker same as locally
   - No breaking changes to migration scripts

**Security Considerations:**

1. **Secrets in Environment Variables:**
   - JWT_SECRET must be generated securely (not "changeme")
   - MYSQL_ROOT_PASSWORD should be strong (32+ chars)
   - Never commit .env file to git

2. **Trusted Proxy Configuration:**
   - TRUSTED_PROXY_IPS must include Docker bridge network (172.18.0.0/16)
   - Also include Cloudflare IP ranges for production
   - Wrong config = backend accepts spoofed X-Forwarded-For headers

3. **Container Isolation:**
   - MySQL should NOT be exposed on 0.0.0.0:3306
   - Only backend container should access MySQL
   - Nginx should NOT have direct access to MySQL

4. **Docker Image Security:**
   - Use official base images (openjdk:17-jre-slim, mysql:8.0, nginx:alpine)
   - Scan for vulnerabilities with `docker scout` or Snyk
   - Keep base images updated

## Testing Guidelines

### Manual UI Testing

**Local Development Environment:**

1. **Initial Setup:**
   ```bash
   # Clone repo
   cd LimbusPlanner

   # Copy environment template
   cp .env.example .env

   # Generate secure secrets
   JWT_SECRET=$(openssl rand -base64 48)
   MYSQL_ROOT_PASSWORD=$(openssl rand -base64 32)

   # Edit .env file with generated secrets
   nano .env
   ```

2. **Start Docker Compose:**
   ```bash
   docker-compose up
   # Wait for logs: "Started BackendApplication in X.XXX seconds"
   ```

3. **Verify MySQL Initialization:**
   ```bash
   docker-compose logs mysql | grep "ready for connections"
   # Should see: "mysqld: ready for connections. Version: '8.0.x'"
   ```

4. **Verify Flyway Migrations:**
   ```bash
   docker-compose logs backend | grep "Flyway"
   # Should see: "Successfully applied X migrations"
   ```

5. **Test Frontend Loading:**
   - Open browser: http://localhost
   - Verify: React app loads without errors
   - Check browser console: No CORS errors

6. **Test API Health Endpoint:**
   - Navigate to: http://localhost/api/actuator/health
   - Verify: Returns `{"status":"UP"}`

7. **Test Rate Limiting with Device ID:**
   ```bash
   # Simulate Docker NAT IP with device ID
   curl -H "X-Forwarded-For: 172.18.0.2" \
        -H "X-Device-ID: test-device-123" \
        http://localhost/api/planner/md/published
   # Should return 200 OK (uses device ID for rate limiting)
   ```

8. **Test Rate Limiting with Public IP:**
   ```bash
   # Simulate public IP
   curl -H "X-Forwarded-For: 203.0.113.1" \
        http://localhost/api/planner/md/published
   # Should return 200 OK (uses IP for rate limiting)
   ```

9. **Test OAuth Flow:**
   - Click "Sign in with Google" button
   - Complete OAuth flow
   - Verify: Redirected back to http://localhost/auth/callback/google
   - Verify: User logged in successfully

10. **Test Data Persistence:**
    ```bash
    # Stop containers
    docker-compose down

    # Restart containers
    docker-compose up -d

    # Verify: User data still exists (check database)
    docker-compose exec mysql mysql -u root -p${MYSQL_ROOT_PASSWORD} -e "USE danteplanner; SELECT COUNT(*) FROM users;"
    ```

11. **Test Container Restart:**
    ```bash
    # Restart backend only
    docker-compose restart backend

    # Wait 10 seconds
    sleep 10

    # Verify: API still accessible
    curl http://localhost/api/actuator/health
    ```

### Automated Functional Verification

**Docker Infrastructure:**
- [ ] `docker-compose up` starts all services without errors
- [ ] MySQL container passes health check within 30 seconds
- [ ] Backend container starts only after MySQL is healthy
- [ ] Nginx container starts and listens on port 80
- [ ] Frontend static files accessible at http://localhost/
- [ ] Backend API accessible at http://localhost/api/

**Rate Limiting:**
- [ ] Requests with Docker NAT IPs (172.18.x.x) fall back to device ID
- [ ] Requests with public IPs use IP-based rate limiting
- [ ] Requests with CF-Connecting-IP header prefer that over X-Forwarded-For
- [ ] Rate limit buckets are isolated per identifier (ip:xxx or device:xxx)

**Environment Configuration:**
- [ ] Backend fails fast if JWT_SECRET missing
- [ ] Backend fails fast if MYSQL_PASSWORD missing
- [ ] Spring profiles (dev/prod) load correct properties
- [ ] Environment variables override default values

**Data Persistence:**
- [ ] MySQL data survives `docker-compose down && docker-compose up`
- [ ] Named volume retains data between restarts
- [ ] Flyway migrations run exactly once on first startup

**CORS and Proxying:**
- [ ] Frontend can make API requests without CORS errors
- [ ] CORS headers set by backend, not nginx (no duplicates)
- [ ] X-Forwarded-For header passed from nginx to backend
- [ ] X-Forwarded-Proto header passed correctly

**Log Management:**
- [ ] Logs accessible via `docker-compose logs backend`
- [ ] Log rotation prevents runaway disk usage (max 30MB per service)
- [ ] Logs include request IDs for tracing

### Edge Cases

**Startup Failures:**
- [ ] MySQL fails to start: Backend retries connection, shows clear error
- [ ] Backend fails health check: Nginx returns 503 Service Unavailable
- [ ] Flyway migration fails: Backend stops, logs error, doesn't start

**Network Issues:**
- [ ] nginx can't reach backend: Returns 502 Bad Gateway
- [ ] Backend can't reach MySQL: Logs connection error, retries
- [ ] Frontend requests timeout: Client sees network error (not silent failure)

**Configuration Errors:**
- [ ] Missing .env file: Docker Compose shows clear error
- [ ] Invalid JWT_SECRET (too short): Backend fails fast with validation error
- [ ] Wrong MYSQL_HOST: Backend logs "Unknown MySQL server host"

**Resource Limits:**
- [ ] MySQL fills disk: Logs "No space left on device", stops gracefully
- [ ] Backend OOM: Container restarts, Docker logs show OOM kill
- [ ] Nginx worker crash: Nginx master spawns new worker

**Rate Limiting Edge Cases:**
- [ ] Missing X-Forwarded-For header: Uses RemoteAddr (nginx container IP)
- [ ] Spoofed X-Forwarded-For: Rejected if not from trusted proxy
- [ ] Missing X-Device-ID header: Uses IP (or "unknown" if no IP available)

**OAuth Edge Cases:**
- [ ] Callback URL mismatch: Google shows error, user not logged in
- [ ] State parameter mismatch: Backend rejects with 400 Bad Request
- [ ] Expired authorization code: Backend returns clear error message

### Integration Points

**Backend ↔ MySQL:**
- [ ] Backend connects to MySQL via `mysql:3306` hostname
- [ ] Connection pool (HikariCP) doesn't exhaust connections
- [ ] Flyway migrations applied in correct order (V001, V002, ...)

**Nginx ↔ Backend:**
- [ ] Nginx proxies /api/* to backend:8080
- [ ] Proxy headers forwarded correctly
- [ ] Backend receives correct client IP for rate limiting
- [ ] Proxy timeouts configured (60s connect, send, read)

**Frontend ↔ Backend:**
- [ ] Frontend API requests work without CORS errors
- [ ] OAuth callback returns to correct frontend URL
- [ ] Frontend uses correct API base URL (http://localhost/api)

**Docker Compose ↔ Host:**
- [ ] Port 80 accessible from host browser
- [ ] Named volumes visible in `docker volume ls`
- [ ] Container logs accessible via `docker-compose logs`
- [ ] Health checks visible in `docker-compose ps`

**Future Cloudflare Integration:**
- [ ] CF-Connecting-IP header passed through nginx
- [ ] Backend prefers CF-Connecting-IP over X-Forwarded-For
- [ ] TRUSTED_PROXY_IPS includes Cloudflare IP ranges

## Operational Semantics

**Concurrency:**
- Multiple requests from same Docker NAT IP → isolated by device ID
- Database connections pooled (max 10 by default)
- Nginx handles concurrent requests via worker processes

**Boundaries:**
- Max request body size: 10MB (nginx default)
- Max database connections: 10 (HikariCP default)
- Max log file size: 10MB per service (3 files max)

**Ordering:**
1. MySQL starts first
2. MySQL health check passes (mysqladmin ping)
3. Backend starts, runs Flyway migrations
4. Backend health check passes (/actuator/health)
5. Nginx starts, proxies traffic

**Errors:**
- MySQL startup failure → Backend waits and retries
- Flyway migration failure → Backend stops, logs error
- Backend health check failure → Nginx returns 503

**Defaults:**
- JWT_SECRET: No default (must be provided)
- MYSQL_DATABASE: danteplanner
- MYSQL_HOST: mysql (Docker hostname)
- CORS_ALLOWED_ORIGINS: http://localhost:4173

## Success Criteria

**Minimal Viable Deployment:**
- [ ] `docker-compose up` succeeds without errors
- [ ] Frontend accessible at http://localhost
- [ ] API accessible at http://localhost/api/health
- [ ] Rate limiting works correctly
- [ ] Data persists across restarts

**Production Readiness:**
- [ ] Multi-stage builds produce minimal images (<500MB total)
- [ ] Environment variables documented in .env.example
- [ ] Health checks ensure graceful startup
- [ ] Log rotation prevents disk exhaustion
- [ ] All tests pass

**Documentation:**
- [ ] README-DOCKER.md explains setup process
- [ ] .env.example lists all required variables
- [ ] Troubleshooting guide for common errors
- [ ] Migration guide for developers

## Questions to Resolve

1. **OAuth Callback URL:** Should docker-compose use port 80 or 5173 for local dev?
   - Impacts: Google OAuth Console configuration
   - Recommendation: Use port 80 (nginx) to match production architecture

2. **MySQL Volume Strategy:** Named volume or bind mount for local dev?
   - Impacts: Data portability, backup strategy
   - Recommendation: Named volume for dev, bind mount to EBS for production

3. **Rate Limiting Identifier Format:** Prefix with "ip:" or "device:" or just raw value?
   - Impacts: Bucket4j key format, potential collisions
   - Recommendation: Prefix to avoid collision (e.g., ip "192.168.1.1" vs device "192.168.1.1")

4. **Nginx Static File Caching:** Should nginx cache frontend assets?
   - Impacts: Cache invalidation on frontend updates
   - Recommendation: No cache for dev, aggressive cache for production (Cloudflare handles it)

5. **Docker Compose Profiles:** Should we support dev/prod profiles in docker-compose.yml?
   - Impacts: Complexity, flexibility
   - Recommendation: Single docker-compose.yml, different .env files

## Next Steps

1. **Immediate:** Fix ClientIpResolver.java for Docker NAT detection
2. **Next:** Create docker-compose.yml with health checks
3. **Then:** Write Dockerfiles with multi-stage builds
4. **Finally:** Test entire stack locally before cloud deployment
