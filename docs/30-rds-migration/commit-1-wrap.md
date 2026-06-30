# Commit 1 — Implementation Wrap (uncommitted)

**Status:** complete + reviewed (verdict ACCEPTABLE). **NOT committed** — staged for the user.
**Scope:** RDS migration infra + the DB-down resilience fixes that must precede cutover.

---

## What Was Done

### A. DB-down resilience (500 → 503)
A DB outage returned HTTP 500 because two *different* exception hierarchies are thrown:
non-transactional query failure → `DataAccessResourceFailureException` (already mapped to 503),
but a `@Transactional` method failing at transaction-begin → `CannotCreateTransactionException`
(a `TransactionException`, **not** a `DataAccessException`) → escaped to the catch-all 500.
A 500 leaks `INTERNAL_ERROR` because nginx only intercepts 502/503/504. Fix maps both to 503.

- `GlobalExceptionHandler` — handler now `@ExceptionHandler({DataAccessResourceFailureException, CannotCreateTransactionException})`, param widened to `NestedRuntimeException` (annotation pins the routing; no over-catch).
- `JwtAuthenticationFilter` — 3 catch sites widened to multi-catch (refresh path calls `findActiveById`, which is `@Transactional`; filter-thrown exceptions bypass the advice → would be a raw 500).
- `application.properties` — `spring.datasource.hikari.connection-timeout=3000` (fail fast, ~3s not 30s).
- `UserAccountLifecycleService.deleteAccount` — now calls `tokenBlacklistService.invalidateUserTokens` (closes the deleted-user gap created by token-only auth — see Decision A).
- `useAuthQuery.ts` (FE) — on `BackendUnavailableError`/`ServiceUpdatingError`, return the cached user instead of null (warm in-session session survives a blip).

### B. RDS migration infra
- `terraform/rds/` module — db.t4g.micro, single-AZ same-AZ-as-EC2, gp3, encrypted, GTID+ROW param group, `prevent_destroy`+`deletion_protection`+`manage_master_user_password`, toggled `enable_replication_ingress` SG rule (SG-scoped, not 0.0.0.0). Review hardening applied: `publicly_accessible=false`, `ca_cert_identifier` var+output, `engine_version` required+validated (`^8.0.\d+$`), `db_subnet_ids` min-2 validation, `.terraform.lock.hcl` un-ignored.
- `docker-compose.yml` — `mysql` service publishes `${SOURCE_PRIVATE_IP:-127.0.0.1}:3306:3306` (runbook 0.5; localhost-safe default; removed at cutover).

## Files Changed
Tracked (9): `GlobalExceptionHandler.java`, `JwtAuthenticationFilter.java`, `UserAccountLifecycleService.java`, `application.properties`, `JwtAuthenticationFilterTest.java`, `UserAccountLifecycleServiceTest.java`, `docker-compose.yml`, `useAuthQuery.ts`, `useAuthQuery.test.tsx`.
Untracked: `terraform/rds/**` (incl. `.terraform.lock.hcl`, now committable), `backend/src/test/java/.../exception/GlobalExceptionHandlerDbUnavailableTest.java` (new).

## Verification
- Backend: full suite `BUILD SUCCESSFUL`; `GlobalExceptionHandlerDbUnavailableTest` 2/0 (real `@ExceptionHandler` dispatch, both exception types); `DbUnavailableDuringRefreshTests` 3/0 (Site 1 + Site 2 DARFE + Site 2 CCTE).
- Frontend: `useAuthQuery.test.tsx` 11/0 (incl. `ServiceUpdatingError` preservation + strengthened queryFn tests that now actually invoke the queryFn).
- Terraform: `terraform fmt` + `terraform validate` → valid.
- Manual DB-down acceptance test (docker stop mysql → graceful 503): operator step, **not yet run**.

## Review Outcomes (3 focused reviewers; findings validated against code)
- Reviewer claims rejected: move ports to `docker-compose.override.yml` (it is gitignored/local-only → never reaches prod); `final_snapshot_identifier` + `timestamp()` (perpetual plan diff); prod-JDBC VERIFY_CA (out of scope — `application-prod.properties` unchanged, Commit 2 handles it); queryClient singleton cross-request leak (latent only — app is SPA, no `react-start`).
- Applied: the 8-item batch above + Decision C.

## Decisions
- **A — accepted:** token-only auth (Fix 3) removed the per-request DB lookup that used to catch deleted/demoted users; revocation now rests on the in-memory `TokenBlacklistService` (`ConcurrentHashMap`), which a restart wipes → a deleted/demoted user can re-authenticate for ≤15 min after a backend restart. Fixed properly by **Phase 2 Redis externalization, deploying 2026-07-02**. Accepted for the ~2-day window.
- **B — deferred to Commit 2:** `require_secure_transport=ON` (enabling now would reject the non-SSL seed-load/replication before the backend has VERIFY_CA).
- **C — done:** `engine_version` pinned (required + full-minor validation).
- **D — kept:** migration port stays in base compose (localhost-safe default; removed at Commit 2).

## Known Issue (consciously skipped)
Commit-time DB loss → `TransactionSystemException` still escapes to 500. Narrow (write-only, in-flight at the failover instant), not observed (begin-time dominates when the DB is fully down), and logged. The clean fix is root-cause classification (walk the cause chain; 503 only if the root is a connectivity exception), not a blanket type-list (which would mask app-driven `UnexpectedRollbackException`/constraint violations as 503). Left documented.

## Next Steps
1. Operator: run the manual DB-down acceptance test (`docker stop danteplanner-mysql` → hit endpoints → expect graceful 503 + warm session preserved → `docker start`).
2. Commit (user-driven, via `commit-process` skill): `static` submodule first; `git add terraform/rds/.terraform.lock.hcl` (newly un-ignored); migration two-commit model still applies (this is the infra+resilience precursor; Commit 2 = cutover removes the `mysql` service + sources `MYSQL_HOST` from SSM).
3. Migration Zone 0+ operator steps remain (terraform apply, GTID enable, repl user, seed, start replication, cutover) per `runbook.md`.

## Resume
Read this file, or `/resume`.
