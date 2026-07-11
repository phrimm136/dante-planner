# Phase 3 Scenario Ledger: Primary re-check on byId miss + bulkhead pool

Dossiers: /home/user/.local/state/claude-build/LimbusPlanner/034-multi-region-k8s-architecture/phase-3/

## Settled decisions (facts for all spawns)
- **Miss path is the authenticated read-only endpoint only.** `PlannerQueryService.getPlanner`
  is `@Transactional(readOnly=true)` → routes to REPLICA → can genuinely miss on a paused replica.
  `PublishedPlannerQueryService.getPublishedPlanner` is `@Transactional` (read-write, uses
  `findByIdForUpdate` + records a view) → routes to PRIMARY → never misses. The seam wraps BOTH
  endpoints; the re-check materially fires only on the read-only path; published is primary-routed
  so INV1 holds trivially there. Do NOT change published's tx semantics (CLAUDE.md rule #1).
- **Miss signal = `PlannerNotFoundException`** thrown by the dereference supplier.
- **Re-check contract:** on a replica miss the seam re-runs the dereference forced onto a dedicated
  bulkhead pool that reaches the Oregon primary; found → return entity + increment
  `replica_miss_promoted_total`; still missing → rethrow `PlannerNotFoundException` (404 preserved).
- **Re-invoking the supplier double-executes it.** Safe for the two in-scope endpoints
  (`getPlanner` is side-effect-free; published never re-checks). Bounded assumption: a future
  read-only endpoint with side effects must not inherit silent double execution.
- **Bulkhead activation is gated on the routing/replica datasource existing** (Seoul only). With
  `datasource.routing.enabled` off (default profile, Oregon, all existing tests) the seam stays a
  pure pass-through — existing repo tests must remain green and unchanged.
- **Counter meter id = `replica_miss_promoted_total`** — named with the literal `_total` suffix to
  match the existing `jwt_rotation_outcome_total` convention (`RefreshRotationService.METRIC_OUTCOME`).
- **Bulkhead pool size = 3** (`mechanics.md §6` "2–3, isolation not throughput"). It is a Seoul-only,
  primary-hitting pool. INV9 worst case with it: (15 Oregon + 10 Seoul-write)×2 pods already = 50;
  the Seoul bulkhead ×2 pods adds 6 → still ≤ 75 (`85 − 10`). Confirm in `PoolLedgerConfigTest`.
- **Toxiproxy wiring:** the app→primary proxy exists but carries no live traffic yet
  (`CausalHarnessSupport`). For `BulkheadIT`, the harness points the bulkhead datasource URL at the
  toxiproxy-proxied primary (`TOXIPROXY.getHost()` + `getMappedPort(8666)`, db `testdb`, user/pass
  `test`/`test`) so `applyWan()` (130ms) slows re-checks; the write pool draws its own connections
  so writes stay isolated. INV4: no sleeps in correctness code; any latency threshold lives in the
  test only, framed as comparative isolation.

## Acceptance
- Test: `org.danteplanner.backend.integration.ReplicaLagIT` (INV1 re-check case) — opened RED 2026-07-11 (assertion: `catchThrowable`→`PlannerNotFoundException` where promotion expected, `ReplicaLagIT.java:97`). Closed GREEN at scenario 1 (green: ReplicaLagIT 1/1). NB: it IS scenario 1's test, so it flips mid-burndown, not at final — not vacuous (real re-check machinery required).
- Co-acceptance (final scenario): `org.danteplanner.backend.integration.BulkheadIT` (INV7)

## Scenarios
| # | Scenario (one line) | Status | Red proof | Green proof |
|---|--------------------|--------|-----------|-------------|
| 1 | replica byId miss re-checks primary, found → entity + counter++ (ReplicaLagIT INV1) | closed (=acceptance) | assertion: PlannerNotFoundException @ ReplicaLagIT.java:97 | green: ReplicaLagIT 1/1, pass-through + boundary suites green |
| 2 | double-miss → 404 preserved, counter untouched, BULKHEAD pin cleared (no leak) | closed (characterization) | green-on-arrival (branch inherent to scn1 green) | green: ReplicaLagIT 3/3; pin-clear proven black-box (stale replica read same-thread) |
| 3 | replica HIT → no re-check, counter untouched (no false promotion) | closed (characterization) | green-on-arrival | green: ReplicaLagIT 3/3, counter unchanged on hit |
| 4 | bulkhead pool (size 3) added to ledger; INV9 sum still ≤ budget (PoolLedgerConfigTest) | closed | compile-red: missing `BULKHEAD_POOL` symbol | green: PoolLedgerConfigTest 4/4, (15+10+3)×2=56≤75 |
| 5 | BulkheadIT INV7: wan toxic + junk-UUID miss flood saturates bulkhead, concurrent write isolated | closed (co-acceptance) | green-on-arrival (isolation inherent to scn1 dedicated pool) | green: BulkheadIT 1/1, 4 non-vacuous assertions (flood 24×PNF, write<1 re-check, mid-flight) |

## List Revisions
- Burndown order = **4 → 1 → 2 → 3 → 5**. Scenario 1's re-check green needs `PoolLedger.BULKHEAD_POOL`,
  so scenario 4 introduces that ledger constant (test-first) before scenario 1 builds the bulkhead
  datasource + re-check on it. Scenario 4's red is compile-red (missing constant) — a ledger-constant
  test is definitionally compile-gated; assertion-green = INV9 sum incl. Seoul bulkhead ≤ 75.
- After scenario 1's minimal green, scenarios 2 & 3 were GREEN-on-arrival — the still-missing→rethrow
  and re-check-only-in-catch branches are inherent to `PrimaryReCheck.readWithReCheck`. Reclassified
  as **characterization/regression guards** (no fabricated red). Scenario 2 earns its place: it proves
  the `BULKHEAD` ThreadLocal pin is cleared in a `finally` (a leak would starve the 3-conn bulkhead) —
  asserted black-box via a same-thread stale-replica read (advisor-flagged gap).
- Harness learning (from phase-open): the `it` profile does NOT run `TestDataInitializer`
  (`@Profile("test")`); ITs mint their own user via `TestDataFactory.create{TestUser,TestPlanner}`
  whose `repository.save` routes through the routing datasource (read-write → primary), so a
  post-`stopReplica()` insert lands primary-only. `catchThrowable` converts the miss-path throw into
  a clean behavioral AssertionError.

## Burndown close (run by me — the gate)
- Scoped suite: `ReplicaLagIT` + `BulkheadIT` → `BUILD SUCCESSFUL in 1m 45s`, EXIT=0
  (log `/tmp/build-8603a2f4-p3-burndown-close.log`). Both acceptance tests GREEN.
- All 5 scenarios closed (2 & 3 characterization, 5 co-acceptance green-on-arrival). No blocked, no struck.

## Pipeline (post-burndown)
- independent gate (resumed session, run by me): `ReplicaLagIT` + `BulkheadIT` →
  `BUILD SUCCESSFUL in 1m 58s`, EXIT=0 (log `/tmp/build-8603a2f4-p3-gate.log`). Working tree green
  as inherited; no drift from the prior burndown/refactor close.
- refactor: empty diff — no behavior-preserving debt; scoped net green (BUILD SUCCESSFUL, EXIT=0), tests untouched
- review: skipped (end review mode — phase-level five-reviewer pass deferred to the task-level review the orchestrator runs over the full diff)
- refactor-again: skipped (end review mode — no post-review refactor in phase)
- delta re-review: skipped (end review mode)
- verify: PASS after 1 round — verification.md Phase 3. All items MET: INV1 (3 ReplicaLagIT cases),
  INV7 (BulkheadIT, 4 assertions inspected individually — substitutes for green-on-arrival's missing
  red), §5 byId-ONLY (exactly 2 seam callers, both byId), both-endpoints (getPlanner + getPublishedPlanner,
  published tx unchanged), §6 bulkhead=3, INV4 (no timing constants in production), Seoul-only pass-through,
  INV9 bulkhead-inclusive sum 56 ≤ 75. INV2 correctly excluded (Phase 4). No code changed.
