# AWS Deployment Research

## Clarifications Resolved

| Question | Decision | Rationale |
|----------|----------|-----------|
| Sentry SDK version | v7.3.0 | Spring Boot 3.4.1 requires Jakarta EE; Sentry v7.0+ supports Jakarta |
| CloudWatch log group naming | `/ecs/danteplanner/{service}` | Standard ECS-style naming, easy to filter by service |
| GitHub Actions SSH method | `appleboy/ssh-action` | Well-maintained, supports key-based auth, simple syntax |
| Rollback strategy | Git reset + docker-compose restart | Atomic: either deployment succeeds or reverts completely |

---

## Spec-to-Code Mapping

**Sentry Integration:**
- `backend/pom.xml` → Add `sentry-spring-boot-starter` dependency (v7.3.0)
- `GlobalExceptionHandler.java` → Add `Sentry.captureException(ex)` to all 18 handlers
- `application-prod.properties` → Add DSN, environment, traces-sample-rate, send-default-pii
- `application.properties` → Add empty DSN placeholder for dev (Sentry disabled locally)

**CloudWatch Logging:**
- `docker-compose.yml` → Add `logging` section with awslogs driver to mysql, backend, nginx
- Configuration: awslogs-region, awslogs-group, awslogs-stream-prefix, awslogs-create-group

**CI/CD Pipeline:**
- `.github/workflows/deploy.yml` → NEW file with test, build, deploy, health-check stages
- GitHub Secrets → EC2_HOST, EC2_USER, EC2_SSH_KEY, SENTRY_DSN, MYSQL passwords

**Documentation:**
- `scripts/backup.sh` → NEW MySQL backup script with S3 upload
- `docs/tasks/014-deploy/03-aws/runbook.md` → NEW operational procedures document

---

## Spec-to-Pattern Mapping

| Requirement | Pattern Source | Pattern to Apply |
|-------------|----------------|------------------|
| Sentry exception capture | error-tracking skill (adapted for Java) | Wrap each @ExceptionHandler with Sentry.captureException() |
| Configuration externalization | application.properties (existing) | Use ${VAR:default} syntax for DSN |
| Docker logging | docker-compose.yml (existing services) | Add logging section parallel to existing healthcheck |
| GitHub Actions workflow | appleboy/ssh-action docs | SSH → git pull → docker-compose up → health check |
| Health check verification | Spring Actuator (existing) | Call /actuator/health with retry logic |

---

## Pattern Enforcement

| File to Modify | MUST Read First | Pattern to Copy |
|----------------|-----------------|-----------------|
| `pom.xml` | Current pom.xml lines 1-156 | Maven dependency structure, version properties |
| `GlobalExceptionHandler.java` | Same file (18 handlers) | @ExceptionHandler annotation, log.warn/error pattern |
| `application-prod.properties` | `application.properties` lines 1-105 | Property naming (dot notation), ${VAR} syntax |
| `docker-compose.yml` | Same file lines 1-113 | Service structure, healthcheck pattern |
| `.github/workflows/deploy.yml` | N/A (new) | Use appleboy/ssh-action template |

---

## Existing Utilities (Verified)

| Category | Location | Status |
|----------|----------|--------|
| Exception handling | `GlobalExceptionHandler.java` | Exists - 18 handlers, needs Sentry integration |
| Rate limiting | `RateLimitConfig.java` | Exists - TTL eviction pattern (inspiration for log retention) |
| IP resolution | `ClientIpResolver.java` | Exists - CF-Connecting-IP support ready |
| Health checks | `docker-compose.yml` | Exists - Spring Actuator /actuator/health configured |
| Property config | `application-prod.properties` | Exists - CORS, cookie domain already configured |

---

## Gap Analysis

**Currently Missing:**
- Sentry dependency in pom.xml
- Sentry.captureException() calls in GlobalExceptionHandler
- Sentry config properties in application-prod.properties
- CloudWatch awslogs driver in docker-compose.yml
- GitHub Actions deploy.yml workflow
- scripts/backup.sh for MySQL → S3
- docs/tasks/014-deploy/03-aws/runbook.md

**Needs Modification:**
- pom.xml (add dependency)
- GlobalExceptionHandler.java (add 18 Sentry calls)
- application-prod.properties (add 4 Sentry properties)
- docker-compose.yml (add logging to 3 services)

**Can Reuse:**
- Application properties pattern (dot notation, ${VAR})
- Docker service structure (healthcheck, environment)
- Spring Actuator health endpoint
- Rate limiting CF-Connecting-IP handling

---

## Testing Requirements

### Manual UI Tests
- Trigger 404 error → verify Sentry captures exception within 5 seconds
- Check CloudWatch Logs → verify backend logs appear in real-time
- Complete OAuth flow → verify cookie domain .dante-planner.com
- Push to main → verify GitHub Actions deploys successfully
- Health check → verify /actuator/health returns {"status":"UP"}

### Automated Functional Verification
- [ ] Health endpoint returns 200 after deployment
- [ ] Public endpoints accessible without auth
- [ ] Rate limiting returns 429 on 6th request/second
- [ ] CORS headers present for dante-planner.com origin
- [ ] SSL certificate valid for api.dante-planner.com

### Edge Cases
- [ ] Backend restart: MySQL data persists
- [ ] EC2 reboot: Services auto-start via docker-compose restart policy
- [ ] Flyway migration failure: Manual intervention documented in runbook
- [ ] Health check timeout: Rollback triggers automatically

### Integration Points
- [ ] Cloudflare → EC2: CF-Connecting-IP header forwarded correctly
- [ ] OAuth flow: Google → Backend → Frontend cookie works end-to-end
- [ ] SSE connections: Real-time notifications work after deployment
- [ ] GitHub Actions → EC2: SSH connection succeeds with stored key

---

## Technical Constraints

**Sentry Compatibility:**
- Spring Boot 3.4.1 requires Sentry v7.0+ (Jakarta EE support)
- Recommended: sentry-spring-boot-starter v7.3.0 (stable, tested)
- Configuration: DSN via environment variable, not hardcoded

**CloudWatch Requirements:**
- EC2 instance role must have CloudWatchLogsFullAccess policy
- Log group auto-creation enabled via awslogs-create-group: "true"
- 7-day retention stays within free tier (10GB/month)

**GitHub Actions Constraints:**
- SSH key must be PEM format, stored as secret
- Health check retry: 3 attempts, 10s timeout each
- Rollback must be atomic (git reset + docker-compose)

---

## Implementation Order

1. **Sentry Integration** (2-3 hours)
   - Add pom.xml dependency
   - Add application-prod.properties config
   - Add Sentry.captureException() to GlobalExceptionHandler (18 locations)

2. **CloudWatch Logging** (1-2 hours)
   - Add awslogs driver to docker-compose.yml (3 services)
   - Verify IAM role has CloudWatch permissions

3. **GitHub Actions CI/CD** (3-4 hours)
   - Create .github/workflows/deploy.yml
   - Configure GitHub Secrets
   - Test with manual trigger before enabling on push

4. **Documentation** (1-2 hours)
   - Create scripts/backup.sh
   - Create docs/tasks/014-deploy/03-aws/runbook.md

**Total Estimated Effort: 8-12 hours**
