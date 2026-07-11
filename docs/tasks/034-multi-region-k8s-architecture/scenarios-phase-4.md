# Phase 4 Scenario Ledger: Redis content tombstones for deletes + byId-positive tombstone check

Dossiers: /home/user/.local/state/claude-build/LimbusPlanner/034-multi-region-k8s-architecture/phase-4

## Acceptance
- Test: `ReplicaLagIT::deleteTombstonesGhost_replicaPositiveReturns404` (INV2) — replication paused, delete on primary, byId via replica → 404 even while the replica still holds the (non-soft-deleted) row.
- Status: opened RED (clean assertion-red at `ReplicaLagIT.java:243` — `java.lang.AssertionError: Expecting actual not to be null`; positive `PlannerResponse` served because no check exists) — **closed GREEN at scenario 2**

## Burndown close (my independent run)
`backend/gradlew -p backend test --tests "org.danteplanner.backend.integration.ReplicaLagIT"` → EXIT=0, `BUILD SUCCESSFUL in 1m 15s`; JUnit XML `tests="5" skipped="0" failures="0" errors="0"`. Test-file diff: only `ReplicaLagIT.java` (+72, pure insertions from RED — no existing test weakened).

## Settled decisions (pinned into Briefs)
- **New deliverable `ContentTombstoneStore`** (`shared/readpath`, `@Component`): owns the `del:<entityType>:<id>` key format + 1h TTL. Injects Spring Boot's auto-configured `StringRedisTemplate` (resolves against the `@Primary` auth `LettuceConnectionFactory`). Single source of truth for both write and check (DRY).
- **Write in the delete SERVICE**, not the controller (plan lists service first; ReplicaLagIT drives services directly). `PlannerCommandService.deletePlanner` writes the tombstone synchronously after `plannerRepository.save(planner)`, alongside the existing SSE side-effect.
- **Overloaded-constructor pattern (per `ByIdReadGuard`)** to add the dependency WITHOUT touching `PlannerCommandServiceTest` (which hand-constructs the 8-arg ctor): keep the existing 8-arg ctor as a delegating convenience ctor passing `Optional.empty()`; add an `@Autowired` ctor taking `Optional<ContentTombstoneStore>` (+ the two `@Value` ints). Field is `Optional<ContentTombstoneStore>`; delete does `store.ifPresent(...)`. In production the `@Component` is always present → write always fires.
- **Fail-open on Redis connectivity (write AND check).** The tombstone is an optimization over the Phase-3 primary re-check correctness gate, NOT the gate (mechanics §1: "1h TTL = cleanup, not the correctness gate"). `PlannerControllerTest` (`@SpringBootTest(MOCK)`, no Redis, exercises the real DELETE) must stay green — an eager write to `localhost:6379` would fail. So `writeTombstone` swallows Redis connectivity exceptions (WARN log); `isTombstoned` returns false on a Redis error (serve the positive, fall back to Phase-3 behavior).
- **Check lives in `PrimaryReCheck` on the replica-HIT positive only**, not promoted-primary positives (read-path order: `replica hit → tombstone → serve | replica miss → primary re-check → serve/404`). Structure the tombstone check OUTSIDE the miss-catch so a tombstone `PlannerNotFoundException(id)` does NOT trigger a primary re-check. `ByIdReadGuard.read` threads `entityType`+`id` into `PrimaryReCheck.readWithReCheck`. `ByIdReadGuard()` no-arg (pass-through, used by `ByIdReadGuardTest`/`ByIdReadSeamTest`) stays a pure pass-through → no tombstone check when no replica → those tests unaffected.
- Entity type is `"planner"` (reuse `ByIdReadGuard.PLANNER_ENTITY_TYPE`) for both write and check. Tombstone value marker: `"1"`.

## Scenarios
| # | Scenario (one line) | Status | Red proof | Green proof |
|---|--------------------|--------|-----------|-------------|
| 1 | delete writes `del:planner:<id>` synchronously with a bounded ~1h TTL | closed | assertion: `ReplicaLagIT.java:264` `hasKey isTrue` fails (key absent); JUnit errors=0 | green: `deleteWritesTombstoneKeyWithBoundedTtl` PASSED (ReplicaLagIT 5 tests, sole fail=INV2 by design); PlannerControllerTest + PlannerCommandServiceTest BUILD SUCCESSFUL (fail-open verified) |
| 2 | replica-served byId positive with tombstone present → 404 (INV2, closes acceptance) | closed | assertion: `ReplicaLagIT.java:243` isNotNull fails (positive served) | green: seam check (PrimaryReCheck replica-hit branch) → INV2 GREEN; ReplicaLagIT 5/5; seam units ByIdReadGuardTest/ByIdReadSeamTest green |

Run command (no root gradlew — only `backend/gradlew`): `backend/gradlew -p backend test --tests "org.danteplanner.backend.integration.ReplicaLagIT"`

Scenario "no-tombstone positive still served" is NOT added — the existing `byIdHitOnReplica_servesWithoutReCheck_andDoesNotPromote` (no tombstone written, replica hit, served) is the regression net proving the check does not over-fire.

## List Revisions
- (seed) none yet — the list held at 2 scenarios; no implementation-driven revision.

## Seam blast-radius closure
Scenario 2 changed the shared `PrimaryReCheck.readWithReCheck` signature + adds a `hasKey` on every replica-hit byId positive. Only `BulkheadIT` (besides ReplicaLagIT) drives a byId through the seam (`byIdReadGuard.read`); the other replica ITs (RoutingSeoulIT/ReplicaResumeIT/ReplicaStopIT/ToxiproxyWanIT) assert at the routing/pool level and never call the seam. `BulkheadIT` run independently → BUILD SUCCESSFUL, 1/1 green (`/tmp/tdd-bulkhead-phase4.log`). Compile is guaranteed (all test sources compile together); the change is additive (isTombstoned=false for non-tombstoned UUIDs). Read-side gate closed.

## Accepted residue (log-only, not a Phase-4 gap)
The tombstone write sits inside `deletePlanner`'s `@Transactional`, before the MySQL commit. A commit failure AFTER the Redis write (e.g. the trailing `sseService.notifyPlannerUpdate` throwing) would leave a false `del:planner:<id>` 404'ing a still-live entity for up to 1h, and the seam deliberately skips the primary re-check on a tombstone throw so nothing self-corrects it. Outside Phase-4's stated contract (the spec's accepted residue is the ghost-read window, not delete-rollback consistency). Candidate for a later hardening phase; not reopened here.

## Pipeline (post-burndown)
- refactor: nothing to consolidate — empty production diff; suite green (BUILD SUCCESSFUL EXIT=0 `/tmp/tdd-refactor-phase4.log`); tests unchanged. Store is single source of truth for key/TTL/marker; no Javadoc drift; no dead code. Overloaded ctors (PlannerCommandService/ByIdReadGuard/RoutingDataSourceConfig) left intact (required pattern).
- verify: PASS after 1 round — verification.md Phase 4 (all in-scope clauses MET, ReplicaLagIT 5/5; GTID-gate/Oregon-topology/N+1 audit SCOPED-OUT). Log `/tmp/spec-verify-phase4.log`.
- capture: drafts written (task + dossier phase dir, both EXIT=0, silent); sweep EXIT=0 (watermark → d019e28b) — all candidates still-true, none stale, none obsolete (Phase 4 is purely additive; reinforces `lesson-materialized-views-should-be-defensive-read`). No doc edits, no retire proposals.
- staged: 9 files, +286/-13 — ContentTombstoneStore.java (new), PlannerCommandService.java, PrimaryReCheck.java, ByIdReadGuard.java, RoutingDataSourceConfig.java, ReplicaLagIT.java, verification.md, scenarios-phase-4.md, status.json. Pre-existing dirty mechanics.md/requirements.md deliberately NOT staged. status.json: phase 4 → done, currentPhase → 4.
