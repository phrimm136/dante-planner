# Phase 8 Scenario Ledger: GTID cookie gate (author read-your-own-write)

Dossiers: ${XDG_STATE_HOME:-$HOME/.local/state}/claude-build/LimbusPlanner/034-multi-region-k8s-architecture/phase-8/

## External contract
An authenticated write sets an HttpOnly `Secure; SameSite=Lax` GTID cookie. A cookie-bearing
read-only request runs `WAIT_FOR_EXECUTED_GTID_SET('<gtid>', 0.05)` on the replica: returns 0 →
route replica + clear cookie; returns 1 → route THIS request to primary. Token rides the request
cookie ONLY — never a server-side/Redis store (mechanics §0 FORBIDDEN). The 0.05 is a probe bound,
not a correctness window (a longer lag simply keeps routing to primary).

## Acceptance
- Test: CausalGateIT::causalGate_authorWriteSetsGtidCookie_readYourOwnWriteRoutesPrimaryThenClearsCookieWhenCaughtUp
  — opened RED 2026-07-11 (assertion: "a Set-Cookie carrying the transaction GTID ... among []";
  write returned 2xx, no gate cookie — not vacuous). Closed GREEN at scenario 3.

## Scenarios
| # | Scenario (one line) | Status | Red proof | Green proof |
|---|--------------------|--------|-----------|-------------|
| 1 | GtidCookie: build HttpOnly/Secure/SameSite=Lax cookie with GTID value; cleared variant Max-Age=0 (unit) | closed | compile-red: `cannot find symbol GtidCookie.of/.cleared` | green: GtidCookieTest tests=2 failures=0; ryw_gtid, of()/cleared() ResponseCookie |
| 2 | GtidCookieFilter read-path: no-cookie no-op; caught-up clears cookie + no pin; not-caught-up pins PRIMARY; pin cleared in finally even on chain exception (unit, mocked GtidReadGate) | closed | compile-red: 6 `cannot find symbol` on GtidCookieFilter/GtidReadGate/GtidWriteCapture | green: GtidCookieFilterTest BUILD SUCCESSFUL (5 cases, log); S1 still green; test files untouched |
| 3 | CausalGateIT: authenticated write captures @@gtid_executed → sets cookie; cookie-bearing read routes primary while replica paused, then replica + clears cookie after awaitCaughtUp (containerized, = acceptance; FINAL scenario) | closed | acceptance assertion-red (opened at phase open): "a Set-Cookie carrying the transaction GTID ... among []"; write 2xx, no gate cookie | green: CausalGateIT tests=1 failures=0 errors=0 (all 3 legs); RoutingSeoulIT sibling green; naming green |

## List Revisions
- [seed]: tdd-red proposed 8 scenarios (1 capture, 2 cookie-attrs, 3 WAIT-probe, 4 WAIT=1→pin,
  5 WAIT=0→clear, 6 no-cookie no-op, 7 token-rides-cookie-only, 8 pin lifecycle). Phase-manager
  consolidation into 3: S1=proposed#2 (cookie attrs, fast unit); S2=proposed#4+5+6+8 (filter
  orchestration, fast unit with mocked gate); S3=proposed#1+3 folded into the containerized
  acceptance (write-capture + WAIT probe are integration-only, matching the existing causal-path
  pattern: RoutingSeoulIT/BulkheadIT prove the seam via containers, not pure units). Struck
  proposed#7 (token-rides-cookie-only): untestable as a positive assertion-by-absence; the
  no-server-side-store property is verified by spec-verifier + review, not a red test.

## Independent scoped run (phase-manager, burndown close)
Command: `backend/gradlew -p backend test --tests "*CausalGateIT" --tests "*TestNamingConventionTest" --tests "*GtidCookieFilterTest" --tests "*GtidCookieTest"`
Tail: `BUILD SUCCESSFUL in 1m 26s` — EXIT=0. All four scoped classes green (CausalGateIT 1/0/0,
TestNamingConventionTest 1/0, GtidCookieFilterTest 5/0, GtidCookieTest 2/0).

## Pipeline (post-burndown)
- refactor: done — GtidGateConfig single filter-registration path (dropped dual @Bean+FilterRegistrationBean trip-wire); GtidCookie extracted private base() builder. Strain #2 (JdbcTemplate ctor dup) left as-is (YAGNI). Suite green, tests byte-unchanged.
- verify: PASS after 1 round — verification.md Phase 8 (all 4 items MET; FORBIDDEN §0 both clauses
  hold: no server-side token store, 0.05 is a safe probe bound on both branches).
- capture: SKIPPED per user directive (no meme draft / no sweep).
- staged: 11 files, +812/-2 — 5 prod (shared/gtid/GtidCookie, GtidCookieFilter, GtidReadGate,
  GtidWriteCapture, GtidGateConfig), 3 tests (CausalGateIT, GtidCookieFilterTest, GtidCookieTest),
  scenarios-phase-8.md, verification.md, status.json. Excluded (not this phase): mechanics.md,
  requirements.md, todo.md.
