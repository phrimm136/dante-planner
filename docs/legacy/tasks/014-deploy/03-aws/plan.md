# AWS Deployment Execution Plan

## Planning Gaps

**None identified.** Research resolved all decisions:
- Sentry SDK: v7.3.0 (Jakarta EE compatible with Spring Boot 3.4.1)
- CloudWatch log groups: `/ecs/danteplanner/{service}`
- GitHub Actions: `appleboy/ssh-action`
- Rollback: Git reset + docker-compose restart

**Correction from research:** GlobalExceptionHandler has **21 handlers** (not 18).

---

## Execution Overview

5 phases with clear dependencies:
1. **Phase 1 (Sentry)** — Error visibility BEFORE production
2. **Phase 2 (CloudWatch)** — Logging infrastructure
3. **Phase 3 (CI/CD)** — Automated deployment
4. **Phase 4 (Documentation)** — Backup scripts and runbooks
5. **Phase 5 (Verification)** — End-to-end testing

**Critical ordering:** Sentry MUST be first (cannot debug production without error tracking).

---

## Dependency Analysis

### Files Being Modified

| File | Impact | Depends On | Used By |
|------|--------|------------|---------|
| `backend/pom.xml` | MEDIUM | Maven Central | Backend build |
| `GlobalExceptionHandler.java` | HIGH | Sentry SDK | All API error responses |
| `application-prod.properties` | HIGH | SENTRY_DSN env var | All production config |
| `docker-compose.yml` | HIGH | AWS IAM role | All container deployments |

### Ripple Effect Map

- `pom.xml` changes → Backend recompile → Docker image rebuild
- `GlobalExceptionHandler.java` changes → All 21 exception handlers affected
- `application-prod.properties` changes → Requires SENTRY_DSN on EC2
- `docker-compose.yml` changes → Requires IAM role with CloudWatchLogsFullAccess

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| `GlobalExceptionHandler.java` | All error responses flow through here | Add Sentry calls individually; test 404/500 locally |
| `docker-compose.yml` | Affects all services; local dev impact | Create override file for local dev |
| `application-prod.properties` | Wrong config breaks production | Verify SENTRY_DSN exists before deploy |

---

## Execution Order

### Phase 1: Sentry Integration (2-3 hours)

| Step | Action | Files | Depends On |
|------|--------|-------|------------|
| 1.1 | Add Sentry dependency to pom.xml | `backend/pom.xml` | None |
| 1.2 | Add Sentry config to application-prod.properties | `application-prod.properties` | 1.1 |
| 1.3 | Add Sentry.captureException() to 21 handlers | `GlobalExceptionHandler.java` | 1.1 |
| 1.4 | Add empty DSN to application.properties (dev) | `application.properties` | 1.1 |
| 1.5 | Build verification: `mvn clean compile` | N/A | 1.1-1.4 |

**Checkpoint 1:** Backend compiles; running locally with empty DSN doesn't crash.

### Phase 2: CloudWatch Logging (1-2 hours)

| Step | Action | Files | Depends On |
|------|--------|-------|------------|
| 2.1 | Add awslogs driver to mysql service | `docker-compose.yml` | None |
| 2.2 | Add awslogs driver to backend service | `docker-compose.yml` | None |
| 2.3 | Add awslogs driver to nginx service | `docker-compose.yml` | None |
| 2.4 | Add SENTRY_DSN to backend environment | `docker-compose.yml` | Phase 1 |
| 2.5 | Create docker-compose.override.yml for local dev | `docker-compose.override.yml` | None |

**Checkpoint 2:** `docker-compose config` validates; local dev works with override.

### Phase 3: CI/CD Pipeline (3-4 hours)

| Step | Action | Files | Depends On |
|------|--------|-------|------------|
| 3.1 | Create .github/workflows directory | `.github/workflows/` | None |
| 3.2 | Create deploy.yml workflow | `.github/workflows/deploy.yml` | None |
| 3.3 | Define test job (Maven + Vitest) | deploy.yml | None |
| 3.4 | Define build job (Docker build) | deploy.yml | 3.3 |
| 3.5 | Define deploy job (SSH → EC2) | deploy.yml | 3.4 |
| 3.6 | Define health-check job | deploy.yml | 3.5 |
| 3.7 | Define rollback job (on failure) | deploy.yml | 3.6 |
| 3.8 | Document required GitHub Secrets | runbook.md | 3.7 |

**Checkpoint 3:** Workflow YAML validates; manual workflow_dispatch works.

### Phase 4: Documentation (1-2 hours)

| Step | Action | Files | Depends On |
|------|--------|-------|------------|
| 4.1 | Create scripts directory | `scripts/` | None |
| 4.2 | Create backup.sh (mysqldump + S3) | `scripts/backup.sh` | None |
| 4.3 | Create runbook.md | `docs/tasks/014-deploy/03-aws/runbook.md` | Phases 1-3 |
| 4.4 | Document Flyway recovery | runbook.md | None |
| 4.5 | Document rollback procedures | runbook.md | None |
| 4.6 | Document CloudWatch alarm responses | runbook.md | None |

**Checkpoint 4:** All docs complete; backup.sh is executable.

### Phase 5: Verification (2-3 hours)

| Step | Action | Depends On |
|------|--------|------------|
| 5.1 | Deploy to EC2 (manual first) | All phases |
| 5.2 | Verify health: `curl .../actuator/health` | 5.1 |
| 5.3 | Trigger 404, verify Sentry receives event | 5.2 |
| 5.4 | Verify CloudWatch logs appear | 5.2 |
| 5.5 | Complete OAuth flow end-to-end | 5.2 |
| 5.6 | Push to main, verify CI/CD deploys | 5.2 |
| 5.7 | Test rollback with intentionally broken code | 5.6 |

---

## Verification Checkpoints

| # | After Step | Method | Success Criteria |
|---|------------|--------|------------------|
| 1 | 1.5 | `mvn clean compile` | Build succeeds; no Sentry errors in dev |
| 2 | 2.5 | `docker-compose config` | Valid YAML; logging config present |
| 3 | 3.7 | GitHub Actions manual trigger | Workflow completes |
| 4 | 4.6 | Manual review | Runbook sections complete |
| 5 | 5.7 | End-to-end test | All items pass |

---

## Risk Mitigation

| Risk | Mitigation | Implemented In |
|------|------------|----------------|
| EBS volume failure | Daily snapshots + S3 backups | Phase 4 (backup.sh) |
| SSH key compromise | IP restriction, quarterly rotation | Runbook |
| No error tracking | Sentry FIRST priority | Phase 1 |
| Flyway migration failure | Document recovery | Phase 4 |
| Docker build failure | Rollback job | Phase 3 |
| Cloudflare IP changes | Update procedure | Runbook |

---

## Rollback Strategy

**Safe stopping points:**
- After Phase 1: Backend runs without deployment changes
- After Phase 2: docker-compose reverts with `git checkout`
- After Phase 3: CI/CD disabled by removing workflow

**Rollback by failure point:**

| Failure | Action |
|---------|--------|
| Step 1.3 (build breaks) | `git checkout GlobalExceptionHandler.java` |
| Step 2.4 (compose invalid) | `git checkout docker-compose.yml` |
| Step 5.1 (production broken) | `git reset --hard HEAD~1 && docker-compose up -d` |

---

## Pre-Implementation Checklist

- [ ] EC2 instance launched with Elastic IP
- [ ] IAM role attached with CloudWatchLogsFullAccess
- [ ] Security Groups configured (Cloudflare IPs + SSH)
- [ ] Sentry project created, DSN available
- [ ] GitHub Secrets configured (EC2_HOST, EC2_SSH_KEY, SENTRY_DSN, etc.)
- [ ] S3 bucket created for backups
