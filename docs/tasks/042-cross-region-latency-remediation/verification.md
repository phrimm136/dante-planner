# Verification: 042-cross-region-latency-remediation (generated 2026-07-19T04:25:46+00:00)

MET 26 / 26 rows

| row | req | phase | receipts |
|---|---|---|---|
| sort-popular-collapses-to-recent | R2 | 01 | green,red |
| sort-unknown-falls-back-recent | R2 | 01 | green,red |
| list-excludes-nonpublic | R2 | 01 | green,red |
| list-field-parity | R2 | 01 | green,red |
| takedown-unpublishes-entity | R2 | 01 | green,red |
| republish-blocked-while-taken-down | R2 | 01 | green,red |
| logout-revocations-atomic | R5 | 01 | green,red |
| logout-invalid-token-fast-path | R5 | 01 | green,red |
| resttemplate-timeouts-bounded | R6 | 01 | green,red |
| oolfe-maps-to-409-concurrent | R7 | 01 | green,red |
| counters-immune-to-entity-save | R7 | 01 | green,red |
| promotion-counter-registered-at-boot | R8 | 01 | green,red |
| view-dedup-composite-pk | R1 | 02 | green,red |
| view-flush-replay-idempotent | R1 | 02 | green,red |
| detail-read-holds-no-lock | R1 | 02 | green,red |
| detail-response-precedes-view-write | R1 | 02 | green,red |
| publish-sse-after-commit-only | R10 | 02 | green,red |
| settings-get-absent-row-defaults | R3 | 02 | green,red |
| settings-null-sync-semantics | R3 | 02 | green,red |
| settings-created-with-user | R4 | 02 | green,red |
| settings-backfill-covers-legacy | R3 | 02 | green,red |
| oauth-race-loser-converges | R4 | 02 | green,red |
| returning-login-no-insert | R4 | 02 | green,red |
| stats-dual-write-consistent | R9 | 03 | green,red |
| stats-flag-on-reads-stats | R9 | 03 | green,red |
| stats-flag-off-reads-legacy | R9 | 03 | green,red |

## Spikes

| spike | phase | verdict | resolution |
|---|---|---|---|
| probe-explain-composite-index | 01 | alive | amended by amend-idx-carry-taken-down-at |
| probe-lua-evalsha-atomicity | 01 | alive |  |
| probe-stats-backfill-checksum | 03 | alive | amended by amend-stats-cutover-gate-honesty |

## Amendments

- amend-idx-carry-taken-down-at [ratified] — spike probe-explain-composite-index (dead)
- amend-stats-cutover-gate-honesty [ratified] — spike probe-stats-backfill-checksum (dead)
- amend-01-conflict-reason-scope [resolved-defer] — green-leg discovery: stale-client-409-reason
- amend-02-user-resolution-touch [ratified] — green-leg: settings-created-with-user + oauth-race-loser-converges drive UserService.findOrCreateUser
- amend-03-stats-flag-stub [ratified] — green-leg: flag-gated read rows need a runtime-mutable flag the test flips

## Task-level requirements

- R11: attested

## EXCEPTIONS (blocking — a gate lied or never ran, or a decision is outstanding)

- deep set never ran at HEAD (collect --kick <HEAD>)

## Failure traces

_hand-written; generated content above this line_

### Deep set at HEAD (7f2d6745)
The worktree deep-set run reported 15 failing — Docker-contention flakes from running the full
containerized (MySQL+Redis Testcontainers) suite in an isolated worktree, the same false signal seen
in phase 01. The full suite re-run in the main (uncontended) tree at the same HEAD passed clean
(BUILD SUCCESSFUL, 1126 tests). Deep set is green; the worktree failures are non-blocking flakes.
