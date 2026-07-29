# Phase 6 Scenario Ledger: Refresh rotation Lua externalization + scheduled-job multi-pod safety

Dossiers: /home/user/.local/state/claude-build/LimbusPlanner/034-multi-region-k8s-architecture/phase-6/

## Acceptance
- Test: RefreshRotationServiceTest (migrated to Redis-Lua harness) — cross-instance theft: a token driven to USED on service instance A, replayed on a second service instance B sharing the SAME auth Redis, is detected as THEFT and the family revoked. Against today's per-instance in-memory impl this is impossible.
- Status: CLOSED GREEN at scenario 2 — compile-red (deferred, opening) → assertion-red harvested after scenario 1 (cross-instance "replayed on instance B over shared Redis": `expected Revoked but was Rotated`) → GREEN (CrossInstanceAtomicity.xml tests=1 failures=0). Vacuity guard satisfied (logged assertion-red preceded green).

## Scenarios
| # | Scenario (one line) | Status | Red proof | Green proof |
|---|--------------------|--------|-----------|-------------|
| 1 | happy-path rotation over Redis: fresh login -> parent PENDING + successor UNUSED_LATEST, cookie set, rotated metric | closed | compile-red (opening) | green: 6 must-pass tests green (fresh-login ROTATED=1, successor-first-use USED/ROTATED=2, cookie, invalid, access-token size0, legacy x3) /tmp/tdd-green-1783764012.log |
| 2 | theft detection (single- and cross-instance): USED/SUPERSEDED replay -> THEFT, family revoked, cookies cleared, theft_revoked metric (verbatim transition Lua) | closed | assertion: Theft D (USED) + A/B (SUPERSEDED) `expected Revoked but was Rotated` | green: TheftDetection.xml tests=2 fail=0 (verbatim §2.2 Lua) |
| 3 | retry supersede: re-present parent -> prior successor SUPERSEDED, fresh minted, retry_superseded metric | closed | assertion: `expected SUPERSEDED but was UNUSED_LATEST` | green: Retry.xml tests=2 fail=0 |
| 4 | revoked family: revokeFamily then present -> Rejected(REVOKED_FAMILY), cookies cleared, rejected_revoked_family metric | closed | assertion: `expected Rejected but was Rotated` | green: FamilyRevocation.xml tests=3 fail=0 |
| 5 | legacy admission over Redis: deterministic synthesized family, legacy_admitted metric; admit-off rejects INVALID | closed | (n/a — Java-side synthesis unchanged) | green: LegacyAdmission.xml tests=3 fail=0 at scenario 1 |
| 6 | concurrent same-parent (2 threads): one UNUSED_LATEST winner, one SUPERSEDED loser, both valid JWTs (Lua single-threaded IS the lock) | closed | assertion: `expected one SUPERSEDED loser, got [UNUSED_LATEST, UNUSED_LATEST]` | green: ConcurrentRotation.xml tests=1 fail=0 |
| 7 | scheduled cleanup deletion: rotation @Scheduled hourly sweep + in-memory maps/gauges removed (Redis TTL replaces them) | closed | (deletion — no dedicated test) | green: grep confirms no @Scheduled/ConcurrentHashMap in RefreshRotationService; RotationEntry.java deleted; suite green |
| 8 | ShedLock mutual exclusion: two RedisLockProvider over one Redis, same lock name -> first acquires, concurrent second does not; @SchedulerLock present on 2AM notification + 3AM user cleanup jobs | closed | compile-red (deferred, dep is deliverable) -> assertion: pre-annotation reflection checks `AssertionFailedError` at lines 85/95 (annotation absent) | green: ShedLockMultiPodTest.xml tests=3 fail=0 + TestNamingConventionTest tests=1 fail=0; deps shedlock-spring/redis 5.16.0, ShedLockConfig LockProvider over authRedisConnectionFactory, @SchedulerLock on both jobs |

## List Revisions
- [after scenario 8] brief gap: ShedLock red's two reflection test-method names (`..._IsAnnotatedWithSchedulerLock`) violated the frozen `TestNamingConventionTest` ArchUnit rule (regex requires >=3 underscore segments). My brief omitted the naming convention. Resolved by re-spawning red to rename to `..._WhenDeclared_IsAnnotatedWithSchedulerLock`; no production change. Lesson: brief for any new test file must pin the `methodName_WhenCondition_ExpectedBehavior` rule when the assertion is a plain reflection/structural check that doesn't naturally fit the pattern.
- (seed) scenarios 2-6 are largely closed at once by the verbatim §2.2 transition Lua landing in scenario 2's green; each still gets an independent assertion-red digest harvested from the migrated existing-suite run after scenario 1's minimal (no-theft) green, before scenario 2 closes them. Recorded because the Lua is atomic (cannot be half-implemented and stay verbatim per mechanics §2.2).

## Pipeline (post-burndown)
- independent suite (run by me): BUILD SUCCESSFUL, EXIT=0 — RefreshRotationServiceTest 15/15, ShedLockMultiPodTest 3/3, TestNamingConventionTest 1/1 (log scratchpad/phase6-burndown-close.log). CPD ratchet clean (cpdCheck EXIT=0).
- refactor: RefreshRotationService.java — extracted `parseLeadingState` shared by `presentedState`/`stateOf`; suite green (15/15), tests untouched.
- verify: PASS after 1 round — verification.md Phase 6 (INV8, metric parity, mechanics §2.2/§2.3/§2.5, plan external contract all MET).
- capture: SKIPPED per user (no meme draft/sweep this phase).
- staged: see COMMIT PROPOSAL in return.
