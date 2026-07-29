# Phase 11 Ledger: mysqld_exporter deploy + memory gate

Dossiers: none — no persistent agents spawned (see "Nature of phase").
Baseline: HEAD b9382dc6 (phases 01–10 committed). Branch `dev` (NOT `build/*`). The working tree
carries a heavy PRE-EXISTING dirty set unrelated to this phase and NOT this phase's to stage:
`.claude/*` (harness/skills/settings/commands), backend integration test sources
(CausalHarnessSupport/DegradationIT/HibernateInsertBatchingIT/MySQLIntegrationTest/
PlannerQueryCountTest/RedisConnectionRecoveryIT), backend/build.gradle.kts, deploy/base/
spring-daemonset.yaml, scripts/ops/lib/*, static submodule pointer, frontend vite.config.ts +
StartBuffMiniCard.test.tsx, requirements.md (M), phases/03 + phases/04 ledgers (M), and every
untracked docs/tasks/* + terraform/* + frontend/* path, plus plan/ (untracked task-level artifact).
Phase-11 delta at open = none (phases/11-mysqld-live-verify did not exist).

## Nature of phase — infra / operational + live-verify, docs-only repo artifact
Kind: infra (GitOps-synced deploy after main promotion; consent-gated operational provisioning;
metric-verified). Files: none new in-repo — the mysqld_exporter manifest is phase 03 (committed:
`deploy/base/mysqld-exporter.yaml` + both overlays' endpoint patches); this phase is the LIVE deploy +
the operational provisioning (`mechanics.md` §5: RDS monitoring user + DSN) + the per-instance memory
gate (`mechanics.md` §6, INV7). There is NO production/test code to author, NO local test suite (task
brief: "Scoped run command: none local expected … do not manufacture a vacuous green"), and NO
application behavior in the repo. The entire behavioral deliverable is: (a) the operational
provisioning steps the USER runs against AWS Secrets Manager + RDS, and (b) the live probes the USER
runs against region-local Prometheus + CloudWatch — all under the infra consent boundary. Accordingly:
- tdd-red / tdd-green / tdd-refactor burndown is INAPPLICABLE — no assertions exist to drive red, no
  code to write, no minimum-code debt to consolidate. Spawning a red test here would manufacture a
  vacuous green, which the brief forbids.
- No close-run: there is no scoped suite to run. The phase's regression net is the live probe +
  provisioning record below.
- No spec-verifier / no meme capture spawned here — spec verification beyond a close run and memory
  capture are TASK-LEVEL (owned by the /build orchestrator), not this phase manager's to run.
- The pipeline collapses to: author this ledger (the operational provisioning + verification runbook +
  record scaffolding) → docs-only stage (ledger + manifest.json phase-11 entry) → phase closes
  `authored`, not `done` (every provisioning + probe step is the user's, consent-gated) → the commit
  is the user's/orchestrator's; a staged proposal is returned, NOT run here (branch is `dev`, not
  `build/*`).

## Verification-doc decision (plan line 27 "Record in the phase verification doc")
Deliberately resolved, not silently mirrored. The record scaffolding lives EMBEDDED in this ledger
(as phase 10 did), and a standalone task-level `verification.md` is DEFERRED to task-level
verification — this phase manager is FORBIDDEN from spawning any verifier/reviewer (both are
/build-orchestrator-owned), and the live probe values that a verification.md would certify do not
exist until the USER runs the runbook post-promotion. Phase 03 carries a standalone `verification.md`
because it certified a RENDER artifact that existed at authoring time; phase 11 has no such
authoring-time artifact to certify. The ledger-embedded scaffolding is the durable record the
user/verifier fills in; the manifest phase-11 entry does not yet list a `verification` artifact.

## Doctrine deviation (surfaced) — in-repo ledger, no commit
- Phase-manager doctrine places the ledger in the state dir, never in the repo. This TASK's established
  convention (10 committed precedents: phases 01–10 each carry `phases/<id>-<slug>/ledger.md` in-repo)
  and my explicit launch instruction (in-repo ledger path) override it. The in-repo ledger IS the
  heartbeat; no redundant state-dir copy is written. Deviation surfaced for the merge gate.
- Doctrine forbids `git commit` on any branch not matching `build/*`. The current branch is `dev`.
  Consistent with phases 08 and 10 ("Commit is the USER's — proposal returned, not run here"), this
  phase STAGES the docs-only set and RETURNS the commit proposal; it does not commit.

## External contract (plan/phase-11.md)
mysqld_exporter reports `up == 1` in both regions; digest metrics (`events_statements_summary_by_digest`,
exported as `mysql_perf_schema_events_statements_*`) and QPS (`mysql_global_status_*`) are visible; the
per-instance `FreeableMemory` before/after memory gate result (INV7) is recorded, on primary AND replica
independently, with a replica-gate failure → primary-only fallback recorded as a known blind spot.

## Infra consent boundary
Every step below is the USER's: AWS Secrets Manager writes, MySQL user/grant DDL on RDS, `kubectl`, and
CloudWatch `FreeableMemory` measurements. They are given as named, exact-command steps with concrete
pass thresholds and blank record slots. The phase ships `authored`, not `done`; the user acting on these
steps after promotion is the point of the phase. NO secret value (DSN, password, primary endpoint) is
authored into the repo (INV11) — the steps below WRITE those values into AWS Secrets Manager and RDS,
never into any committed file.

## Committed contract this phase deploys (phase 03, HEAD)
- k8s Secret consumed by the exporter: `mysqld-exporter-dsn` (created by ESO), keys `username`,
  `password`, `primary-endpoint`.
- ExternalSecret `mysqld-exporter-dsn` (identical in BOTH overlays) reads THREE SEPARATE AWS Secrets
  Manager secrets — each a PLAIN STRING, NOT a JSON blob — via the `aws-secrets-manager` SecretStore,
  `refreshInterval: 1h`: `secretKey username ← remoteRef.key danteplanner/mysqld-exporter/username`,
  `password ← danteplanner/mysqld-exporter/password`,
  `primary-endpoint ← danteplanner/mysqld-exporter/primary-endpoint`. **No `property:` field** on any
  remoteRef, so ESO's AWS provider treats each `key` as a full secret NAME and fetches that secret's
  entire value as the string. This is the house JWT-secret model (`external-secret.yaml`,
  `jwt-secrets.yaml`: bare slash-path `key`, no `property`, one secret per field), NOT the JSON-blob
  model (`origin-tls-secrets.yaml` uses `key: danteplanner/origin-tls` + `property: tls_crt` to pull one
  JSON key; `runtime-config.yaml` uses `dataFrom.extract`). The phase-03 manifest is CONSISTENT with the
  JWT precedent — do NOT provision a single `danteplanner/mysqld-exporter` JSON blob; provision three
  separate string secrets (STEP 1).
- Exporter: `prom/mysqld-exporter:v0.19.0`, one Deployment per region on `nodeSelector role: data`,
  args `--collect.perf_schema.eventsstatements` + `--collect.perf_schema.tableiowaits` only, scrape
  annotation `prometheus.io/job: mysqld` (→ job label `mysqld`). Oregon `--mysqld.address` ← Secret
  `primary-endpoint`; Seoul overlay patches `MYSQLD_ADDRESS` to the literal replica host
  `mysql-replica.seoul.danteplanner.internal:3306` — so the Seoul exporter targets its region-local
  replica while STILL requiring the `username`/`password` keys (the `primary-endpoint` key is present
  but unused in Seoul).

### Cross-region Secrets Manager note (SURFACED — user's to confirm)
The ExternalSecret is IDENTICAL in both overlays and each region's ESO reads via the region-local
`aws-secrets-manager` SecretStore. AWS Secrets Manager is a per-region service. Therefore all three
`danteplanner/mysqld-exporter/*` secrets must be resolvable by the SecretStore in BOTH the Oregon and
Seoul clusters — either provisioned in each region's Secrets Manager, or via whatever cross-region
reference the existing `aws-secrets-manager` SecretStore already uses for the phase-precedent secrets
(`external-secret.yaml`, `jwt-secrets.yaml`). Confirm the region topology against how those precedent
secrets are provisioned before writing; do not assume a single-region write suffices. All three secrets
(including `.../primary-endpoint`) must exist in every region that syncs the ExternalSecret, even though
Seoul overrides the address.

## Gating preconditions (ALL steps below gate on these — nothing is deployed yet)
`dev` has NOT been promoted to `main` at authoring time, so the phase-03 mysqld_exporter manifests are
not deployed. Every LIVE probe (STEP 5+) gates on:
1. `dev` promoted to `main`.
2. The provisioning steps below (STEP 1 Secrets Manager secret, STEP 2 RDS monitoring user + grants,
   STEP 3 instrument enablement) COMPLETE — else ESO cannot materialize the k8s Secret and the exporter
   cannot authenticate to MySQL.
3. ArgoCD has SYNCED the phase-03 mysqld_exporter Deployment + ExternalSecret into BOTH clusters AND the
   phase-01/04 `deploy/base/prometheus.yaml` scrape+relabel config that gives the target its `mysqld`
   job label.
4. The Prometheus rollout has been RESTARTED so the mysqld scrape job + `job` relabel are live
   (`kubectl -n <mon-ns> rollout restart deploy/prometheus` — or the region's Prometheus workload — then
   `rollout status`).
Run the whole runbook in BOTH regions: oregon AND seoul (one exporter Deployment per cluster). Kubeconfigs
are per-region files (Oregon `~/.kube/dante-oregon`; Seoul fetchable from SSM via `terraform/seoul`
outputs) — phase 05/07/08/10 precedent.

## Operational runbook (USER-executed, consent-gated)

### STEP 1 — Provision THREE separate AWS Secrets Manager string secrets
The phase-03 ExternalSecret reads three separate PLAIN-STRING secrets (no `property:`; JWT-secret model —
see "Committed contract" above). Provision each with its plain value — do NOT create one JSON blob named
`danteplanner/mysqld-exporter` (that layout requires a `property:` selector the manifest does not use, and
ESO would fail to resolve `.../username` → the `mysqld-exporter-dsn` k8s Secret never materializes →
STEP 4 `up==1` fails). Do NOT commit any value (INV11). Pick a strong password; it MUST equal the RDS
monitoring user's password set in STEP 2. `.../primary-endpoint` is the RDS PRIMARY (Oregon) writer
endpoint (`<name>.<hash>.us-west-2.rds.amazonaws.com:3306`) — sourced here, never committed
(deploy/CLAUDE.md).
- Command shape (per region per the cross-region note above — repeat with `--region ap-northeast-2` for
  Seoul if that region's SecretStore reads region-locally; use `put-secret-value` if a secret exists):
  - `aws secretsmanager create-secret --name danteplanner/mysqld-exporter/username --region us-west-2 --secret-string 'mysqld_exporter'`
  - `aws secretsmanager create-secret --name danteplanner/mysqld-exporter/password --region us-west-2 --secret-string '<generated-strong-password>'`
  - `aws secretsmanager create-secret --name danteplanner/mysqld-exporter/primary-endpoint --region us-west-2 --secret-string '<primary-writer-host>:3306'`
- Expected: `aws secretsmanager get-secret-value --secret-id danteplanner/mysqld-exporter/username`
  (and `/password`, `/primary-endpoint`) each returns its plain string in every required region.

### STEP 2 — Create the RDS monitoring user + grants on the PRIMARY (replicates to Seoul)
Create the read-only monitoring user on the PRIMARY only — the grants replicate to the Seoul replica via
binlog (mechanics §6; taste — create once at the write source). The username/password MUST match STEP 1.
```sql
CREATE USER 'mysqld_exporter'@'%' IDENTIFIED BY '<same-password-as-STEP-1>';
GRANT PROCESS, REPLICATION CLIENT ON *.* TO 'mysqld_exporter'@'%';
GRANT SELECT ON performance_schema.* TO 'mysqld_exporter'@'%';
```
- Grants are EXACTLY `PROCESS`, `REPLICATION CLIENT`, `SELECT ON performance_schema.*` — read-only, no
  more (mechanics §6). RDS blocks `SUPER`/`GRANT ALL`; these three are all mysqld_exporter needs for the
  global-status + perf_schema collectors in scope.
- Verify replication reached Seoul: connect to the replica endpoint and confirm
  `SELECT user,host FROM mysql.user WHERE user='mysqld_exporter';` returns the row (do NOT re-create it on
  the replica — a replica is read-only and the user must arrive via replication; its ABSENCE means
  replication of the `mysql.*` grant tables is filtered/broken → escalate, the Seoul exporter cannot
  authenticate).

### STEP 3 — MEMORY GATE (INV7): FreeableMemory before/after enabling perf_schema instruments
This is the phase's go/no-go gate. Measure RDS `FreeableMemory` from CloudWatch (metric
`AWS/RDS FreeableMemory`, `DBInstanceIdentifier` dimension) BEFORE and AFTER the digest + table_io
instruments are active, on the PRIMARY and the REPLICA INDEPENDENTLY.

- **ORDERING TRAP (capture-first, decoupled from the k8s rollout).** The "before" reading MUST be taken
  while the perf_schema digest/table_io instruments are NOT yet enabled — it is gated on
  instruments-off, not on promotion. Once the instruments are on, "before" is contaminated and the delta
  is unmeasurable. Capture "before" first, per instance.
- **Verify the enabling mechanism first (it sets the ordering).** Determine whether the digest +
  table_io instruments are turned on by (a) a STATIC RDS parameter-group change (`performance_schema=1`,
  or the relevant `performance-schema-*` consumers) — which requires a REBOOT — or (b) a RUNTIME
  `UPDATE performance_schema.setup_consumers/setup_instruments` (perf_schema already ON). If (a): after
  the reboot the buffer pool is COLD, so an immediate "after" FreeableMemory reads artificially HIGH and
  MASKS the instrument cost (the drop looks smaller than it is) — take "after" only once the buffer pool
  has re-warmed and FreeableMemory has stabilized under normal load. If (b): no reboot, but still let the
  instruments accumulate rows and FreeableMemory settle before reading "after". Never read "after"
  immediately after enablement.
- **Decision rule (no pinned floor exists in requirements/mechanics — authored here from handoff:108).**
  For each instance:
  - Expected drop ≈ tens of MB (handoff:108 — "performance_schema costs tens of MB on a 1GiB t4g.micro").
    A drop FAR exceeding this band (e.g. > ~150 MB) signals unexpected instrument cost → investigate
    (are only digest + table_io on. Any other consumer enabled?) before leaving instruments on.
  - GATE FAIL for an instance if post-enable FreeableMemory falls to a level that threatens buffer-pool
    hit ratio on the 1GiB instance. No hard floor is pinned upstream, so the exact low-watermark is the
    user's operational call — as a starting bound, treat sustained post-enable FreeableMemory below
    ~100 MB, OR a measurable regression in buffer-pool hit ratio / a rise in `ReadIOPS`/`ReadLatency`
    after enablement, as a FAIL for that instance and back its instruments out.
  - **Replica FAIL → primary-only fallback**, recorded as a known blind spot (mechanics §6 taste:
    primary-only telemetry structurally cannot see replica-only pathologies). Record the blind spot in
    the table and as a manifest followUp if it triggers.
  - **Primary FAIL → escalate.** There is no lower fallback than the primary; a primary gate failure puts
    the whole §G perf_schema route at risk (BLOCKED, surface to the merge gate).

### STEP 4 — mysqld_exporter is up in both regions (per region, region-local Prometheus)
- PromQL (instant): `up{job="mysqld"}`
- Expected: exactly `1` per region (single series, value 1) — confirms the phase-03 exporter is scheduled
  on the `role=data` node, ESO materialized `mysqld-exporter-dsn`, the exporter authenticated to its
  region-local MySQL, AND the phase-01/04 relabel gave it the `mysqld` job label. Empty result =
  scrape/relabel wiring failed or pod not scheduled; `0` = scraped but the exporter cannot reach/auth to
  MySQL (check STEP 1/2 — DSN key values, grants replicated to Seoul, security-group path data-node →
  RDS:3306).

### STEP 5 — digest metrics visible (per region)
The `--collect.perf_schema.eventsstatements` collector exposes `events_statements_summary_by_digest` as
`mysql_perf_schema_events_statements_*` series (labels include `schema`, `digest`). Confirm presence:
- PromQL (instant): `count(mysql_perf_schema_events_statements_total)` — Expected: `> 0` per region.
- Also confirm the table_io collector: `count(mysql_perf_schema_table_io_waits_total)` — Expected: `> 0`.
- If EMPTY: confirm the exact metric suffix against the live exporter `/metrics` (v0.19.0 may name these
  `..._seconds_total` / a variant); an empty result with `up==1` means the collector ran but perf_schema
  returned no rows → instruments not actually enabled (revisit STEP 3 enablement) or the monitoring user
  lacks `SELECT ON performance_schema.*` (STEP 2).

### STEP 6 — QPS metrics visible (per region)
- PromQL (instant): `mysql_global_status_queries` present, and `rate(mysql_global_status_queries[5m])` > 0
  under load — Expected: series present per region (QPS derives from this counter). `mysql_global_status_*`
  is the always-on global-status collector; its presence confirms the exporter's baseline scrape works
  even independent of perf_schema. Also useful: `mysql_global_status_slow_queries` (handoff §G slow-query
  rate). If `mysql_global_status_queries` is empty while `up==1`, confirm the exact name against live
  `/metrics`.

### STEP 7 — Performance Insights instance-class caveat (handoff:107-110)
Do NOT plan around RDS Performance Insights — it is likely UNSUPPORTED on this instance class (t4g.micro).
Verify support in the RDS console / `aws rds describe-db-instances` (`PerformanceInsightsEnabled`) before
relying on it. The digest + table_io instruments (STEP 5) are the sanctioned §G route regardless of PI.

## Record scaffolding (USER fills after promotion + provisioning)

### INV7 memory gate (FreeableMemory before/after, per instance — CloudWatch)
| Instance | Enablement mechanism (param-reboot / runtime) | before (MB) | after (MB, stabilized) | drop (MB) | hit-ratio / ReadIOPS regression? | Gate |
|----------|-----------------------------------------------|-------------|------------------------|-----------|----------------------------------|------|
| primary (Oregon writer) |                                     |             |                        | before−after |                              | PASS / FAIL→ESCALATE |
| replica (Seoul)         |                                     |             |                        | before−after |                              | PASS / FAIL→primary-only blind spot |

Blind-spot note (fill only if replica gate FAILED): _______________________________________________

### Provisioning + live-probe record
| # | Check | Region | Query / command | Expected | Observed | Pass? |
|---|-------|--------|-----------------|----------|----------|-------|
| 1 | 3 string secrets | oregon | `get-secret-value --secret-id danteplanner/mysqld-exporter/{username,password,primary-endpoint}` | all 3 present |  |  |
| 1 | 3 string secrets | seoul  | (region-local SecretStore reads all 3) | all 3 resolvable |  |  |
| 2 | monitoring user + grants | primary | `SHOW GRANTS FOR 'mysqld_exporter'@'%'` | PROCESS, REPLICATION CLIENT, SELECT ON performance_schema.* |  |  |
| 2 | user replicated to replica | seoul | `SELECT user,host FROM mysql.user WHERE user='mysqld_exporter'` | row present (via replication) |  |  |
| 4 | exporter up | oregon | `up{job="mysqld"}` | `== 1` |  |  |
| 4 | exporter up | seoul  | `up{job="mysqld"}` | `== 1` |  |  |
| 5 | digest metrics | oregon | `count(mysql_perf_schema_events_statements_total)` | `> 0` |  |  |
| 5 | digest metrics | seoul  | `count(mysql_perf_schema_events_statements_total)` | `> 0` |  |  |
| 5 | table_io metrics | oregon | `count(mysql_perf_schema_table_io_waits_total)` | `> 0` |  |  |
| 5 | table_io metrics | seoul  | `count(mysql_perf_schema_table_io_waits_total)` | `> 0` |  |  |
| 6 | QPS metric | oregon | `mysql_global_status_queries` / `rate(...[5m])` | present, > 0 under load |  |  |
| 6 | QPS metric | seoul  | `mysql_global_status_queries` / `rate(...[5m])` | present, > 0 under load |  |  |
| 7 | PI support caveat | both  | `aws rds describe-db-instances … PerformanceInsightsEnabled` | note support; §G route = perf_schema regardless |  |  |

## Scenarios
| # | Scenario | Status | Red proof | Green proof |
|---|----------|--------|-----------|-------------|
| — | none — infra / operational + live-verify phase; no testable local code (see "Nature of phase") | n/a | n/a | n/a |

## List revisions
- none — no burndown.

## Close
- burndown: not applicable (infra/live-only; no code, no local tests).
- refactor: not applicable (no authored code to consolidate).
- close run: not applicable — no scoped suite exists (brief: "none local expected"). No vacuous green
  manufactured; the phase's regression net is the live provisioning + probe record above.
- fast gate: not applicable — no code/lint/format target; the sole authored artifact is this docs ledger.
- verification.md: DEFERRED to task-level (spec-verifier is task-level; this phase manager spawns none) —
  see "Verification-doc decision" above. Record scaffolding embedded here is the durable record.
- manifest (file classification — `git status --porcelain` minus Baseline):
  STAGED: phases/11-mysqld-live-verify/ledger.md (new),
  docs/tasks/038-metrics-observability/manifest.json (M — phase-11 entry ONLY; diff verified to touch no
  other entry). NO code (infra/live-only phase — nothing touches the repo).
  IGNORED: the entire pre-existing baseline-dirty set (see Baseline) — NOT this phase's; plan/ (untracked
  task-level artifact). No dossiers (none spawned).
- manifest status: phase 11 → authored (verdict: runbook authored; operational provisioning + all live
  probes + the INV7 memory gate deferred — task-level verifier + user, consent-gated).
- staged: `git diff --cached --stat` = 2 files — phases/11-mysqld-live-verify/ledger.md (new),
  docs/tasks/038-metrics-observability/manifest.json (M). Docs-only; no code. Commit is the USER's —
  proposal returned, not run here (branch is `dev`, not `build/*`).
- commit: NOT run (branch `dev` ≠ `build/*`; task convention returns a staged proposal). SHA: n/a.
</content>
</invoke>
