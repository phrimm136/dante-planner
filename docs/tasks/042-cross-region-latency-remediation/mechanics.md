# Mechanics — Cross-Region Latency & Slow-Query Remediation

Companion to `requirements.md`; transcribed from the 2026-07-18 design session. Prose residue only: mechanism narrative, DDL sketches, choreography, environment context. Row-shaped contracts (scenario expectations, 409 body values, flag-state behavior) live in `tests.manifest.json` and are not repeated here. Sections are binding unless marked *reference*.

## 1. Migration ledger

Numbers are the next free Flyway slots at implementation time (V046+ as of transcription). Ordering within the PR is binding; index names carry their own migration number (version-prefix convention, precedent V007).

1. **Index swap** — create before drop so coverage never gaps:
   `CREATE INDEX idx_v0NN_published_recent ON planners (published, deleted_at, created_at, category)`,
   then `DROP INDEX idx_v006_published_views`, then `DROP INDEX idx_published`. Index-only → no seed update.
2. **Settings backfill** — `INSERT INTO user_settings (user_id) SELECT id FROM users u WHERE NOT EXISTS (SELECT 1 FROM user_settings s WHERE s.user_id = u.id)`; defaults apply, `sync_enabled` stays NULL. Modifies data → update `migration-test-seed.sql`.
3. **planner_stats** — `CREATE TABLE planner_stats (planner_id BINARY(16) PRIMARY KEY, view_count INT NOT NULL DEFAULT 0, upvotes INT NOT NULL DEFAULT 0, CONSTRAINT fk_stats_planner FOREIGN KEY (planner_id) REFERENCES planners(id) ON DELETE CASCADE)` plus backfill from `planners`. Modifies data → seed update.

## 2. Published-list projection

Record `PlannerSummaryRow`, constructor-expression order binding:
`(UUID id, String title, String category, Integer contentVersion, PlannerType plannerType, Set<String> selectedKeywords, String authorUsernameEpithet, String authorUsernameSuffix, Integer upvotes, Instant createdAt, Integer viewCount, Instant lastModifiedAt)`

JPQL shape: `SELECT new ...PlannerSummaryRow(...) FROM Planner p JOIN p.user u WHERE p.published = true [AND p.category = :category] AND p.deletedAt IS NULL AND p.takenDownAt IS NULL`, explicit `countQuery` with the identical WHERE. `JOIN p.user` is INNER where `@EntityGraph` was LEFT — equivalent because `user_id` is NOT NULL; the field-parity row certifies it. Under the phase-03 flag-on state the counter fields come from `planner_stats` (join or second query, implementer's choice; the parity row decides).

## 3. View-recording pipeline

Request thread (readOnly tx): serve from the replica, record `(plannerId, viewerHash, viewDate)` into a per-pod concurrent buffer, no I/O. A `@Scheduled` flush (5s, default) drains it in one transaction: batched `INSERT IGNORE INTO planner_views`, then per-planner increments equal to actually-inserted row counts — never increment on ignored duplicates. NOT ShedLocked (per-pod buffers, not a singleton job; `ShedLockConfig` is for singletons). Loss bound: one flush window per pod death. From phase 03 the increment dual-writes `planners` and `planner_stats` unconditionally.

## 4. Logout revocation script

One `EVALSHA` via Spring `RedisScript` (exemplar: `RefreshRotationService.ROTATE_SCRIPT`) performing atomically: blacklist access jti, blacklist refresh jti (TTLs = remaining token lifetimes, exactly as today's sequential `TokenBlacklistService.blacklistToken` calls), revoke family (`HSET`, as `revokeFamily`). Key layouts are the existing services' — read them at build time, don't invent. The absent/invalid-token fast path stays outside the script.

## 5. Stats cutover choreography

- Flag: property `planner.stats.reads-enabled` ← env `PLANNER_STATS_READS_ENABLED`, declared in both overlay ConfigMaps, default `false` (exemplar: `DATASOURCE_ROUTING_ENABLED`, `deploy/overlays/seoul/configmap-patch.yaml:30`; `configMapGenerator` hash-suffixing turns the edit into the rollout).
- This PR ships: migration 3 + unconditional dual-write + flag-gated reads.
- Flip (operational, post-merge): reconciliation checksum `SELECT COUNT(*) FROM planners p JOIN planner_stats s ON s.planner_id = p.id WHERE p.view_count <> s.view_count OR p.upvotes <> s.upvotes` must return 0, then set the env true in both overlays and apply.
- Follow-up PR (deferred): remove flag + dual-write, drop legacy columns (online INPLACE rebuild), remove the `updatable=false` guard.

## 6. Resolution seams

- `resolveOrCreateUser`: lookup OUTSIDE any transaction (returning user = one round trip); miss enters `@Transactional createOrRecover` (user INSERT + settings INSERT + reactivation branch, one tx). On `DataIntegrityViolationException` from `uk_provider_provider_id`: the transaction dies whole — never catch-and-continue inside the poisoned Hibernate session — and the facade retries the lookup once in a fresh transaction.
- `getSettings`: `@Transactional(readOnly = true)`; absent row → defaults DTO + anomaly log (defensive read).

## 7. Config defaults

`RestTemplate` connect 3s / read 5s (default — undebated numbers, free to change) in `HttpClientConfig`; both Google OAuth calls inherit.

## 8. CLAUDE.md amendment (binding text)

Append to the check-then-act line in `backend/src/main/java/org/danteplanner/backend/CLAUDE.md`: "…except when a DB constraint (PK/unique key) can own the invariant — then prefer `INSERT IGNORE`/handled-violation over the lock; reserve `PESSIMISTIC_WRITE` for invariants no constraint can express."

## 9. Latency ledger (*reference*)

Seoul↔Oregon ≈ 130ms per statement; `published/{id}` = BEGIN + 5–9 statements + COMMIT ≈ 0.9–1.4s (observed p50 1.25s); settings ≈ 4 trips ≈ 0.52s (observed 0.59s); logout = 3 Redis trips ≈ 0.39s + overhead (observed p90 0.61s). Post-fix floors: replica read ~15ms; callback keeps ~0.35s (Google + 1 DB trip + 1 Redis trip — accepted). Derivations live in the 2026-07-18 session; re-measure, don't re-derive.
