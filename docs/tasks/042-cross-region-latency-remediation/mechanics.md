# Mechanics — Cross-Region Latency & Slow-Query Remediation

Companion to `requirements.md`. Transcribed from the 2026-07-18 design session. Sections marked **binding** are contracts implementation must honor; sections marked *reference* archive derivations — cite, don't re-debate.

## 1. Migration ledger (binding)

Numbers are the next free Flyway slots at implementation time (V046+ as of transcription; V045 is the latest committed). Ordering within the PR is binding:

| Order | Content |
|---|---|
| M1 | Index swap — create before drop, so coverage never gaps: `CREATE INDEX idx_v0NN_published_recent ON planners (published, deleted_at, created_at, category)` → `ALTER TABLE planners DROP INDEX idx_v006_published_views` → `ALTER TABLE planners DROP INDEX idx_published`. Index name carries its own migration number (version-prefix convention, precedent V007). Index-only migration → no seed update. |
| M2 | Settings backfill: `INSERT INTO user_settings (user_id) SELECT id FROM users u WHERE NOT EXISTS (SELECT 1 FROM user_settings s WHERE s.user_id = u.id)` — defaults apply (`sync_enabled` NULL preserved). Modifies data → update `migration-test-seed.sql`. |
| M3 | `CREATE TABLE planner_stats (planner_id BINARY(16) PRIMARY KEY, view_count INT NOT NULL DEFAULT 0, upvotes INT NOT NULL DEFAULT 0, CONSTRAINT fk_stats_planner FOREIGN KEY (planner_id) REFERENCES planners(id) ON DELETE CASCADE)` + backfill from `planners`. Modifies data → seed update. |

## 2. Published-list projection (binding)

Record `PlannerSummaryRow` — constructor-expression order is the contract:
`(UUID id, String title, String category, Integer contentVersion, PlannerType plannerType, Set<String> selectedKeywords, String authorUsernameEpithet, String authorUsernameSuffix, Integer upvotes, Instant createdAt, Integer viewCount, Instant lastModifiedAt)`

Query shape (category variant; all-category drops the category predicate):

```
SELECT new ...PlannerSummaryRow(p.id, p.title, p.category, p.contentVersion, p.plannerType,
    p.selectedKeywords, u.usernameEpithet, u.usernameSuffix, p.upvotes, p.createdAt,
    p.viewCount, p.lastModifiedAt)
FROM Planner p JOIN p.user u
WHERE p.published = true [AND p.category = :category]
  AND p.deletedAt IS NULL AND p.takenDownAt IS NULL
```

Explicit `countQuery` with identical WHERE (repo convention, hook-adjacent). `JOIN p.user` is INNER vs the old `@EntityGraph` LEFT — equivalent because `user_id` is NOT NULL; the field-parity characterization test certifies it. Under the D16 flag-on state, `upvotes`/`viewCount` come from `planner_stats` (join or second query — implementation's choice, parity test decides).

## 3. View-recording pipeline (binding)

- Request thread (readOnly tx): serve from replica, then record `(plannerId, viewerHash, viewDate)` into a per-pod concurrent buffer. No I/O on the request thread.
- `@Scheduled` flush, interval 5s (default), NOT ShedLocked (per-pod buffers, not a singleton job). One transaction per flush: batched `INSERT IGNORE INTO planner_views`, then per-planner increments equal to the number of *actually inserted* rows (affected-rows arithmetic — never increment on ignored duplicates).
- Idempotency key = the `planner_views` composite PK; a replayed batch is a no-op. Loss bound: one flush window per pod death (accepted class, D4).
- Dual-write (D16): increments apply to `planners.view_count` (legacy) AND `planner_stats.view_count` unconditionally from this version.

## 4. Logout revocation script (binding semantics)

Single `EVALSHA` (Spring `RedisScript`, exemplar `RefreshRotationService.ROTATE_SCRIPT`) performing atomically: blacklist access jti, blacklist refresh jti (TTLs = remaining token lifetimes, exactly as the current sequential `TokenBlacklistService.blacklistToken` calls), revoke family (`HSET`, as `revokeFamily` today). Key layouts are the existing services' — read them at build time, do not invent. Absent/invalid-token fast path stays outside the script (B10).

## 5. 409 contract (binding)

`ObjectOptimisticLockingFailureException` → HTTP 409, same body family as `PlannerConflictException` (`serverVersion` field name per convention), plus discriminator `reason: "STALE_CLIENT" | "CONCURRENT_WRITE"` — `STALE_CLIENT` for the syncVersion mismatch path, `CONCURRENT_WRITE` for the OOLFE mapping. `log.warn`, no Sentry (expected user-error class).

## 6. Stats cutover choreography (binding)

- Flag: property `planner.stats.reads-enabled` ← env `PLANNER_STATS_READS_ENABLED`, declared in both overlay ConfigMaps, **default `false`** (exemplar: `DATASOURCE_ROUTING_ENABLED`, `deploy/overlays/seoul/configmap-patch.yaml:30`).
- This PR ships: M3 + unconditional dual-write + flag-gated read path.
- Flip procedure (operational, post-merge): run reconciliation checksum — `SELECT COUNT(*) FROM planners p JOIN planner_stats s ON s.planner_id = p.id WHERE p.view_count <> s.view_count OR p.upvotes <> s.upvotes` must be 0 — then set the env true in both overlays and roll out.
- Follow-up PR (Deferred): remove flag + dual-write, drop legacy columns (`DROP COLUMN` = online INPLACE rebuild), remove `updatable=false` guard.

## 7. Callback / settings seams (binding)

- `resolveOrCreateUser`: lookup runs OUTSIDE any transaction (returning user = 1 round trip); miss enters `@Transactional createOrRecover` (user INSERT + settings INSERT + reactivation branch, one tx). On `DataIntegrityViolationException` (`uk_provider_provider_id` race): the transaction dies whole; the facade retries the lookup once in a fresh transaction. Never catch-and-continue inside the failed session.
- `getSettings`: `@Transactional(readOnly = true)`; absent row → defaults DTO (`sync_enabled` null) + anomaly log.

## 8. RestTemplate timeouts (binding values, default-derived)

Connect 3s, read 5s (default — no debate on exact numbers; any change is free). Applied in `HttpClientConfig`; both Google OAuth calls inherit.

## 9. CLAUDE.md amendment (binding text)

Append to the check-then-act line in `backend/src/main/java/org/danteplanner/backend/CLAUDE.md`: "…except when a DB constraint (PK/unique key) can own the invariant — then prefer `INSERT IGNORE`/handled-violation over the lock; reserve `PESSIMISTIC_WRITE` for invariants no constraint can express."

## 10. Latency ledger (*reference*)

Session-derived arithmetic anchoring the acceptance targets: Seoul↔Oregon ~130ms/statement; `published/{id}` = BEGIN + 5–9 statements + COMMIT ≈ 0.9–1.4s (observed p50 1.25s); settings ≈ 4 trips ≈ 0.52s (observed 0.59s); logout = 3 Redis trips ≈ 0.39s + overhead (observed p90 0.61s). Post-fix floors: replica read ~15ms; callback keeps ~0.35s (Google + 1 DB + 1 Redis — accepted). Derivations live in the 2026-07-18 session; re-measure, don't re-derive.
