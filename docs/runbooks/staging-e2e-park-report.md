# Staging e2e loop — park report

Staging was torn down early on the user's explicit override of the green gate (fleets, RDS,
Cloudflare tunnels and DNS destroyed; the secrets stack kept so the next run does not hit Secrets
Manager's recovery window). Two of the three blockers were then reproduced and resolved against
local containers. All code changes are **uncommitted**, for review.

## Green, with recorded runs

| Suite | Evidence | Result |
|---|---|---|
| A + B (routes, contract, schema-portability, journey) | `/tmp/e2e-ab-staging-run6.log` | 34 passed |
| C read-your-writes (5 core) | `/tmp/e2e-c-staging-run7.log` | 5 passed |
| C fallback-ratio scrape | `/tmp/e2e-c-ratio-run1.log` | 1 passed (fallback 0 / tracker 59) |
| D replica-miss re-check | `/tmp/e2e-d-staging-run1.log` | 3 passed |
| OAuth protocol scenarios (4 of 5) | `/tmp/e2e-oauth-staging-run2.log` | callback-mints-ryw-gtid, tampered-state, replayed-code, cross-transaction — all passed |

Backend defects found and fixed on the way (all uncommitted): the GTID cookie was minted only in
a filter that runs after a JSON body is already flushed, so bodied writes set no cookie
(`GtidCookieResponseAdvice` added); the fallback branch fired on empty commits, pinning no-op reads
to the primary (`session_track_gtids` probe added to `GtidCapturingDataSource`); the JDK's default
`Java/<v>` user agent tripped Cloudflare's bot check on the outbound OAuth token exchange, giving an
HTML 403 (`RestTemplate` now sends a product UA).

## Resolved in local containers (after the staging teardown)

The compose stack plus `docker-compose.oauth-gtid.yml` (the OAuth stub beside the app) reproduced
two of the three blockers with no cloud involved, which is what made them diagnosable.

### 1. OAuth browser journey — FIXED, and it was the test
Reproduced identically against local compose, exonerating Cloudflare and the split-hostname
topology. Two test-side defects: the account trigger is an icon button whose accessible name comes
from an `aria-label` React attaches on mount, so a click at `domcontentloaded` resolved nothing;
and a first login lands on the "Keep Local Only / Enable Cloud Sync" prompt, which covers the
header, so waiting for the account email could never succeed. The flow itself was always correct —
a traced run shows start → stub authorize → callback → return with `accessToken` and `refreshToken`
set. Now passes in 1.6s (was a 90s hang). The `callback mints ryw_gtid` scenario is Seoul-pinned by
design and cannot run locally; it passed against staging.

### 2. F degradation ladder — a REAL defect, not injection fidelity
The fault-model hypothesis was wrong: `docker stop` (connection-refused) and `docker pause`
(blackhole) behaved identically, both hanging ~60s, and bypassing nginx showed the backend never
responded at all. Root cause: `TokenBlacklistService` reads through `authLocalStringRedisTemplate`,
but only `authRedisConnectionFactory` carried a `commandTimeout` — the other three Lettuce
factories inherited the minute-long default. A degraded auth Redis therefore held request threads
until nginx gave up, instead of failing open in 3s; under load that exhausts the pool, the exact
amplification §10 exists to prevent. All four factories now share a bounded client configuration.
Measured after the fix, identical under both fault models:

| Fault | Before | After |
|---|---|---|
| auth Redis stopped (conn refused) | 60s → 503 | 6s → fails open, real response |
| auth Redis paused (blackhole) | 60s → 503 | 6s → fails open, real response |

With the fix in, both locally-injectable scenarios go green against genuinely stopped dependencies
(`/tmp/e2e-f-local-run2.log`, `/tmp/e2e-f-local-ratelimit2.log`) — suite F's first non-vacuous runs.
The rate-limit scenario needed a corrected assertion: it expected `RATE_LIMIT_TEMPORARILY_UNAVAILABLE`,
but `GlobalExceptionHandler` states that code is **internal-only** — the edge runs
`proxy_intercept_errors`, so every backend 5xx reaches a client as `BACKEND_UNAVAILABLE`. The suite
was asserting something no client can observe.

Two follow-ups this leaves open: the 6s is two sequential 3s commands on that path (worth deciding
whether they should share one budget), and §10's six rows still do not all map to distinct typed
codes — with the internal-only finding above, some rows may be unassertable from outside the cluster
at all. The remaining four F scenarios (primary blackhole, pool exhaustion, bulkhead, Flyway) need
the multi-region topology and stay unrun.

`e2e/src/auth.ts` gained an `E2E_STAGING_KEY_FILE` fallback so local runs need no AWS credentials —
expired SSO should not be what stops a local debugging session.

## Local two-region rig — C and D now run outside staging

`docker-compose.multiregion.yml` + `scripts/ops/local-multiregion-up.sh` stand up a second MySQL
behind real GTID replication, one backend per region (Seoul routing-enabled on :8082, Oregon on
:8081), and Toxiproxy in front of the primary. The script migrates the primary, clones it into the
replica, starts replication and verifies a write arrives, then starts both regions.

Suites C and D pass against it, 8 tests. More importantly they still pass with a **deliberate
4-second lag** (`CHANGE REPLICATION SOURCE TO SOURCE_DELAY=4`), where the ungated-listing test goes
from 765ms to 5.5s — that jump is the evidence the gate is genuinely exercised. At zero lag the
same test passes without ever waiting for anything, which is a vacuous pass wearing a green tick.
Deterministic lag is the one thing this rig does BETTER than staging, where cross-region lag is a
few hundred milliseconds and often too fast to distinguish a working gate from a quick replica.

Four ordering defects had to be fixed to get there, each worth knowing:

- Both MySQL healthchecks probed over the unix socket (`-h localhost`), which reports healthy
  before the TCP listener accepts, so dependents started into connection-refused. Invisible while
  a data volume persists; reliable after `down -v`.
- A routing-enabled instance cannot boot before its replica holds the schema — its startup
  read-only query goes to the replica. Migration and cloning therefore happen before either
  region starts, which is also the order RDS builds a read replica in.
- The replica's own bootstrap DDL is binlogged, so its `gtid_executed` is non-empty and the
  clone's `SET @@GLOBAL.GTID_PURGED` is refused without a `RESET MASTER` first.
- Cloning `--all-databases` carries the primary's `mysql.user` over the replica's own and cuts
  the connection doing the cloning; only the app schema should travel.

This moves C and D out of the ephemeral-1:1 tier. What genuinely remains there: Cloudflare edge
behavior, geo steering, secret and image delivery, IAM, and the failure modes that only come from
real distance.

## G migration rehearsal — synthetic path built and running

`scripts/ops/migration-rehearsal-synthetic.sh` times V046→V054 against generated data at any row
count, needing no production access and carrying no PII (so it is CI-safe). It builds the
pre-decomposition schema with Flyway `-target=45`, generates rows with a recursive CTE inside the
server, then reports per-migration timings from `flyway_schema_history`.

The row mix is deliberately adversarial where the backfills branch: malformed `device_id` (1 in 10),
NULL and empty keyword sets, 4-byte characters in titles, soft-deleted rows, and taken-down rows —
plus 60% published, without which the two backfills that matter process nothing. That last point
was a real trap: the first run reported V051 at 47ms because zero rows were published; with
published rows it is 711ms at the same size, a 15× difference between a measurement and a vacuum.

Measured (local NVMe, mysql:8.0.46 — directional, not a db.t4g.micro):

| Rows | V050 backfill | V051 filters | V052 cutover | V049 create | Total V046+ |
|---|---|---|---|---|---|
| 2,000 | 1,510ms | 711ms | 4,061ms | 2,282ms | 9.5s |
| 20,000 | 5,740ms | 2,779ms | 6,421ms | 2,570ms | 21.2s |
| 100,000 | 30,303ms | 15,466ms | 10,655ms | 3,038ms | 69.8s |

**The chain is linear in row count over a 50× range**, which is what makes extrapolation legitimate
rather than wishful: fitting the 2k and 100k points gives `total_ms ≈ 8,300 + 0.615 × rows`, and
that model predicts the untouched 20k point at 20.6s against 21.2s measured. V049 stays flat
(2.3→3.0s) as pure DDL should, confirming it belongs to the fixed term.

Two migrations own **72% of the marginal cost** — V050 at 0.294ms/row and V051 at 0.151ms/row —
so any effort to shorten the window belongs there and nowhere else.

Extrapolated on this hardware: 500k rows ≈ 5.3 min, 1M rows ≈ 10.4 min. Plug in production's
planner count for the estimate; the one remaining unknown is the storage multiplier between local
NVMe and the instance's gp3, which the dump run against a restored snapshot would pin down.

A finding fell out of building it: at V045 `selected_keywords` is a SET whose members are already
the renamed ones, so V050's `REPLACE('AccelBullet'…)` / `REPLACE('ChargeLoad'…)` guard against
values a SET column cannot physically hold — V038/V044 migrated them years earlier. Harmless, but
it is dead code in the hottest backfill.

**Still needs the dump run (ruled local-only):** synthetic data proves timing and the skew shapes
someone thought of; only the production dump surfaces the skew nobody predicted. That run is
blocked on `aws sso login` (the session token expired mid-loop).
Staging RDS booted with V046–V054 already applied by Flyway at pod start, so rehearsing the
migration chain needs a pre-V046 baseline, and "a real dump" has no production-data source inside
the isolated staging account (prod is off-limits by ruling). **Ruling needed:** the baseline and
data source for the rehearsal (a synthetic seed at V045, a sanitized dump, or run G only in the
local `tmp/migration-rehearsal.sh` path against the read-only prod tunnel).

## Also done (uncommitted)
- ADR `docs/adr/035-staging-e2e-environment.md` (hostname scheme, fake-IdP + manual smoke,
  tunnels-only access).
- Gate workflow `.github/workflows/gate-staging-e2e.yml` recreated from scratch (`pull_request` on
  `main`, `head_ref == 'staging'`), encoding the full stand-up choreography this loop discovered.
- Provision scripts `scripts/ops/provision/staging-e2e-{secrets,db-users,workloads}.sh`.
- Six staging tfvars mirrored into SSM Parameter Store for the gate.

## Environment state
Nothing is billing: 8 fleet instances terminated, RDS deleted in both regions, tunnels and DNS
records removed, the hand-created ECR repos and artifacts bucket swept. Kept deliberately: the
19-secret stack, and the `danteplanner-mysql-final` snapshot (delete it whenever — it holds only
e2e rows). `prevent_destroy` in `terraform/rds/main.tf` was lifted for the teardown and is
**restored to true**.

Re-standing the environment is one `staging-rehearsal.yml` dispatch, or the same provision-script
sequence by hand; both already encode every fix the teardown documented above.
