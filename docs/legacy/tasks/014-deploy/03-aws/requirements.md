# Task: AWS EC2 Backend Deployment (Phase 3)

## Description

Deploy the LimbusPlanner backend to AWS EC2, completing the production infrastructure. The backend serves the API for `api.dante-planner.com` while the frontend is already live on Cloudflare Pages at `dante-planner.com`.

**Infrastructure Requirements:**
- Launch EC2 t3.small instance (2 vCPU, 2GB RAM) in us-west-2
- Attach 30GB gp3 EBS volume for MySQL data persistence
- Allocate Elastic IP for static addressing
- Configure Security Groups restricting HTTP/HTTPS to Cloudflare IP ranges only
- Restrict SSH access to developer IP addresses only

**Docker Deployment:**
- Deploy existing docker-compose stack (nginx, backend, mysql)
- Mount EBS volume at `/data/mysql` for database persistence
- Verify Flyway migrations execute successfully on first startup
- Configure health checks for zero-downtime deployments

**Monitoring & Observability:**
- Integrate Sentry for error tracking (CRITICAL - deploy blind without this)
- Configure CloudWatch Logs via Docker awslogs driver
- Set up CloudWatch Alarms for CPU (>70%), memory (<200MB available), HTTP 5xx rate (>10/5min)
- 7-day log retention (free tier)

**CI/CD Pipeline:**
- GitHub Actions workflow triggered on push to main
- Run backend tests (Maven) and frontend tests (Vitest) before deployment
- SSH deploy to EC2: git pull, rebuild containers, health check
- Automatic rollback on health check failure

**Backup & Recovery:**
- Enable AWS Backup for daily EBS snapshots (7-day retention)
- Create mysqldump script with S3 upload (cron at 2 AM)
- Document rollback procedures for frontend, backend, and database

**Security Hardening:**
- fail2ban for SSH brute-force protection
- AWS GuardDuty for threat detection
- Quarterly SSH key rotation plan

## Research

- [ ] Read `GlobalExceptionHandler.java` to understand Sentry integration points
- [ ] Review Sentry Spring Boot starter documentation for v7.3.0
- [ ] Verify Cloudflare IP ranges for Security Group configuration
- [ ] Check AWS free tier limits for t3.small and EBS
- [ ] Review GitHub Actions `appleboy/ssh-action` documentation
- [ ] Understand CloudWatch awslogs driver configuration
- [ ] Review AWS Backup pricing for EBS snapshots

## Scope

**Read for context:**
- `docker-compose.yml` - Current orchestration configuration
- `backend/Dockerfile` - Multi-stage build configuration
- `nginx/nginx.conf` - Reverse proxy and header forwarding
- `.env.example` - Required environment variables
- `backend/src/main/resources/application-prod.properties` - Production configuration
- `backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java` - Exception handling
- `backend/src/main/java/org/danteplanner/backend/config/RateLimitConfig.java` - Rate limiting (already handles Cloudflare)
- `backend/src/main/java/org/danteplanner/backend/util/ClientIpResolver.java` - IP resolution for rate limiting
- `docs/tasks/014-deploy/epic.md` - Full deployment epic with cost breakdown

## Target Code Area

**New files to create:**
- `.github/workflows/deploy.yml` - CI/CD pipeline
- `scripts/backup.sh` - MySQL backup to S3
- `docs/tasks/014-deploy/03-aws/runbook.md` - Operational procedures

**Files to modify:**
- `backend/pom.xml` - Add Sentry dependency
- `backend/src/main/resources/application-prod.properties` - Add Sentry configuration
- `backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java` - Add Sentry.captureException()
- `docker-compose.yml` - Add CloudWatch logging driver configuration

## System Context (Senior Thinking)

- **Feature domain**: Infrastructure/DevOps (cross-cutting, affects all backend services)
- **Core files in this domain**:
  - `docker-compose.yml` (orchestration)
  - `config/SecurityConfig.java` (security headers, CORS)
  - `config/RateLimitConfig.java` (rate limiting with Cloudflare awareness)
  - `util/ClientIpResolver.java` (IP extraction from CF-Connecting-IP)
  - `exception/GlobalExceptionHandler.java` (centralized error handling)
- **Cross-cutting concerns touched**:
  - Authentication (JWT cookie domain already configured for `.dante-planner.com`)
  - Rate limiting (already handles Cloudflare proxy IPs via CF-Connecting-IP)
  - CORS (configured in `application-prod.properties` for `https://dante-planner.com`)
  - Logging (SLF4J → CloudWatch Logs)
  - Error tracking (NEW: Sentry integration)

## Impact Analysis

**Files being modified:**

| File | Impact | What Depends On It |
|------|--------|-------------------|
| `GlobalExceptionHandler.java` | HIGH | All error responses, exception logging |
| `docker-compose.yml` | HIGH | All containerized deployments |
| `pom.xml` | MEDIUM | Backend build, dependency resolution |
| `application-prod.properties` | HIGH | All production configuration |

**Dependencies:**
- CloudWatch logging depends on IAM role attached to EC2
- Sentry depends on valid DSN in environment
- Health checks depend on Spring Actuator `/actuator/health`
- Rate limiting depends on `TRUSTED_PROXY_IPS` containing Cloudflare ranges

**Potential ripple effects:**
- Adding logging driver to docker-compose may affect local development (use override file)
- Sentry integration adds network calls on every exception (monitor latency impact)
- CloudWatch log groups must be created (awslogs-create-group: "true")

**High-impact files to watch:**
- `GlobalExceptionHandler.java` - All error responses flow through here
- `docker-compose.yml` - Affects all services

## Risk Assessment

**Critical Risks:**

| Risk | Mitigation |
|------|------------|
| EBS volume failure | Daily EBS snapshots + S3 SQL backups |
| SSH key compromise | Restrict to developer IP, rotate quarterly, consider AWS SSM |
| No error tracking | Sentry integration is FIRST priority before production |
| Rate limiting bypass | Already solved - CF-Connecting-IP header used |

**Edge cases not yet defined:**
- Flyway migration failure on first deploy (manual intervention required)
- Docker image build failure during deployment (need rollback procedure)
- Cloudflare IP range changes (need update procedure)
- MySQL connection pool exhaustion under load

**Performance concerns:**
- t3.small (2GB RAM) is minimum viable; monitor for OOM
- CloudWatch logging adds I/O overhead
- Sentry network calls on exceptions (async, should be minimal)

**Backward compatibility:**
- N/A - new infrastructure, no existing production

**Security considerations:**
- SSH must NOT be open to 0.0.0.0/0 (critical)
- Secrets in GitHub Actions stored as encrypted secrets
- EBS volume not encrypted by default (consider enabling)
- Logs may contain PII (configure Sentry `send-default-pii=false`)

## Testing Guidelines

### Manual UI Testing

**Phase A: Infrastructure Verification**
1. SSH into EC2 instance using private key
2. Verify `/data` directory exists and is mounted (EBS volume)
3. Run `docker --version` and `docker-compose --version`
4. Verify Security Group blocks access from non-Cloudflare IPs

**Phase B: Docker Deployment**
1. Clone repository to `/opt/limbusplanner`
2. Create `.env` file with production secrets
3. Run `docker-compose up -d`
4. Wait 60 seconds for Spring Boot startup
5. Run `curl http://localhost:8080/actuator/health`
6. Verify response: `{"status":"UP"}`
7. Check logs: `docker-compose logs backend | grep "Started BackendApplication"`
8. Check Flyway: `docker-compose logs backend | grep "Successfully applied"`

**Phase C: DNS and SSL**
1. Update Cloudflare DNS: `api.dante-planner.com` A record → Elastic IP
2. Wait 5 minutes for propagation
3. Run `dig api.dante-planner.com` - verify returns Elastic IP (or Cloudflare IP if proxied)
4. Open browser to `https://api.dante-planner.com/actuator/health`
5. Verify HTTPS works (Cloudflare SSL)

**Phase D: Full Stack Verification**
1. Open `https://dante-planner.com` in browser
2. Open DevTools Network tab
3. Click login button
4. Verify OAuth redirects to Google, then back to frontend
5. Verify cookie set with domain `.dante-planner.com`
6. Navigate to planner page
7. Verify API calls go to `api.dante-planner.com`
8. Verify no CORS errors in console

**Phase E: Monitoring Verification**
1. Trigger intentional error (e.g., request non-existent planner)
2. Check Sentry dashboard for captured exception
3. Check CloudWatch Logs for corresponding log entry
4. Verify CloudWatch Alarm not triggered (CPU should be low)

### Automated Functional Verification

- [ ] Health endpoint returns 200: `curl -f https://api.dante-planner.com/actuator/health`
- [ ] Public endpoint accessible: `curl https://api.dante-planner.com/api/planner/md/config`
- [ ] Rate limiting works: 6th request in 1 second returns 429
- [ ] CORS headers present: `curl -I -H "Origin: https://dante-planner.com" https://api.dante-planner.com/api/planner/md/config`
- [ ] SSL certificate valid: `openssl s_client -connect api.dante-planner.com:443`
- [ ] CloudWatch logs appearing: Check AWS Console → CloudWatch → Log groups
- [ ] Sentry receiving events: Check Sentry dashboard for test exception

### Edge Cases

- [ ] Backend restart: MySQL data persists after `docker-compose restart`
- [ ] EC2 reboot: Services auto-start, data persists
- [ ] Invalid JWT: Returns 401, not 500 (check Sentry for no false positives)
- [ ] Database connection failure: Returns 503 with graceful error message
- [ ] Rate limit exceeded: Returns 429 with Retry-After header
- [ ] Flyway migration conflict: Check logs, manual resolution documented

### Integration Points

- [ ] Cloudflare → EC2: CF-Connecting-IP header properly forwarded
- [ ] OAuth flow: Google → Backend → Frontend cookie flow works end-to-end
- [ ] SSE connection: Real-time notifications work (requires authenticated user)
- [ ] GitHub Actions → EC2: Push to main triggers successful deployment
- [ ] CloudWatch Alarms → SNS: Test alarm triggers notification

## Execution Order (Recommended)

1. **Sentry Integration** (2-3 hours) - Do FIRST, don't deploy blind
2. **EC2 + EBS + Elastic IP** (4 hours) - Infrastructure foundation
3. **Security Groups** (1 hour) - Lock down before anything runs
4. **Manual Docker Deploy** (3 hours) - Prove it works before automating
5. **DNS Update** (5 minutes) - Point api subdomain to EC2
6. **Full Stack Verification** (1 hour) - OAuth, API, real-time
7. **CloudWatch Logs + Alarms** (2 hours) - Observability
8. **GitHub Actions CI/CD** (4 hours) - Automate deployment
9. **EBS Snapshots + S3 Backup** (2 hours) - Data protection
10. **Documentation** (2 hours) - Runbooks for operations

**Total estimated effort: 22-25 hours**

## Open Questions

| Question | Decision Needed For |
|----------|---------------------|
| Who receives CloudWatch Alarm notifications? | SNS topic configuration |
| What's acceptable downtime SLA? | Backup frequency, multi-region need |
| Should SSH use AWS SSM instead of direct access? | Security posture |
| EU user GDPR compliance needed? | Data residency (us-west-2 may not be compliant) |
| Multiple developers deploying? | Branch protection, approval workflow |
