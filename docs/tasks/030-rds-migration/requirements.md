# Task: Migrate backend MySQL from on-box Docker volume to AWS RDS (GTID native-replica cutover)

## Goal

Move the production MySQL database off the shared 4 GB EC2 instance onto a managed RDS
instance, using MySQL native binlog replication (GTID auto-position) so the data copy happens
live and the cutover outage is seconds. Primary driver: the co-located MySQL + JVM + nginx on a
4 GB box with no swap causes whole-instance memory-pressure hangs. Secondary gains: managed
durability (automated backups, PITR, host-failure self-heal) and a cleaner deploy story.

This is Phase 1 of a larger epic (later phases: Redis/SSE stateless refactor, k8s, EKS spike,
ArgoCD). Those are **out of scope** here and independent of this work.

## Decisions

- **Move to RDS, not stay on-box** — the memory-pressure hang is rooted in co-locating the DB on a
  4 GB no-swap box; removing MySQL frees ~512 MB + eliminates the DB's bursty memory. RDS also adds
  a durability/self-heal layer the local Docker volume never had. (evidence: `docker-compose.yml:19-31`
  MySQL capped at 512M/256M buffer pool; `Dockerfile` JVM `-Xmx768m`; conversation root-cause analysis)
- **Native MySQL replication, not dump+restore-in-window** — chosen for the learning value (binlog/CDC,
  replica lag, RDS promotion) and a seconds-long cutover. Practical downtime gain over dump+restore is
  modest at this DB size; the learning is the primary reason. (evidence: user goal = BE/SRE résumé skill)
- **Not application-level dual-write** — rejected: no cross-DB transaction → divergence; dual-write does
  not preserve transaction ordering across two servers, so non-commutative writes diverge even when both
  succeed. Replication serializes via the source's ordered binlog. (evidence: conversation; `incrementUpvotes`,
  `trySetRecommendedNotified` are order-sensitive)
- **GTID auto-position, not file+position** — front-loads complexity into Zone 0 (online enable, no restart)
  and removes manual binlog coordinates from the high-stakes promote step. (evidence: User answer)
- **Dedicated least-privilege provisioning credential, assumed via STS, separate from runtime/SSM access** —
  separation of duties: operating the box and provisioning infra are different blast radii; short-lived
  assumed-role creds over long-lived access keys. Specific role names / ARNs / policies are kept OUT of
  this public repo (private ops notes). (evidence: User)
- **db.t4g.micro, single-AZ, gp3, same AZ as EC2** — meets/exceeds current footprint at lowest sane prod
  cost (~$13–15/mo); same-AZ avoids cross-AZ latency + transfer charges. Single-AZ + automated backups is
  the right hobby-scale durability trade (skip Multi-AZ cost-doubling). (evidence: User answer; current
  footprint `docker-compose.yml:19-31`)
- **Terraform provisioning with destroy guards** — `prevent_destroy` + `deletion_protection` +
  `skip_final_snapshot=false`; `manage_master_user_password` so the secret lands in Secrets Manager, not
  tfstate. Console click-ops rejected (drift, not reproducible, defeats the IaC learning goal). (evidence: User answer)
- **TLS `VERIFY_CA` with Amazon RDS CA bundle** — encrypted *and* authenticated; CA bundle mounted `:ro`
  like the JWT keys. (evidence: User answer; `application-prod.properties:5` already `useSSL=true`)
- **Two-commit structure** — Commit 1 = Terraform (infra, applied out-of-band); Commit 2 = app repoint
  (config-only, schema-neutral, deployed by CI = the cutover). The irreversible data load lives in the gap
  between them. (evidence: conversation)
- **`spec.md` data-driven sections are N/A** — this feature consumes no raw game-data files, so Data Model
  Catalog / Normalization Layer / Rendering Modes do not apply. (evidence: `docs/spec.md` scope = data-driven features)

## Resolved Ambiguities

| Question | Resolution | Source |
|----------|------------|--------|
| Spec directory | `docs/tasks/030-rds-migration` (flat top-level; max existing = 29) | Convention (flat docs layout) |
| Replication positioning mode | GTID auto-position (`rds_set_external_master_with_auto_position`) | User |
| RDS instance class / AZ / storage | db.t4g.micro, single-AZ pinned to EC2's AZ, gp3 | User |
| Provisioning mechanism | Terraform with `prevent_destroy`/`deletion_protection` | User |
| Backend→RDS TLS strictness | `sslMode=VERIFY_CA` + RDS CA bundle | User |
| Is there a pending schema migration to land pre-snapshot? | No — git shows none uncommitted; current schema head = `V044` | Codebase `backend/.../db/migration/` + `git status` |
| Terraform code location | `terraform/rds/` (no existing IaC dir; room for later-phase modules) | Default |
| RDS engine version | MySQL 8.0.x ≥ source minor (source image `mysql:8.0`); replicate low→high only | Codebase `mysql/Dockerfile:1` + Default |
| `backup_retention_period` | 7 days (+ defined backup window) | Default |
| `auto_minor_version_upgrade` | false, with an explicit maintenance window | Default |
| Confidence window before deleting `mysql-data` volume | 7 days post-cutover | Default |
| Source MySQL exposure for RDS→source pull | Temporary host-port publish + SG rule (RDS subnet only), removed in decommission | Convention (least-exposure) |
| DB size (sizes the binlog-retention margin, not the design) | ASSUMED small (≤ a few hundred MB); operator MUST measure: `du -sh` the volume / `SELECT SUM(data_length+index_length) ...` | Default (ASSUMED) |
| Test/verification tooling for an ops migration | Operational verification (rehearsal, connectivity probe, replica-lag gate, post-cutover smoke), not unit tests | Convention (infra change, no app-logic delta) |

## Description

Provision an RDS MySQL 8.0 instance via Terraform; establish GTID-based native replication from the
on-box MySQL (source) to RDS (replica) while the site stays live; then perform a seconds-long cutover
that quiesces the source, confirms replication has drained, promotes RDS to a standalone primary, and
deploys a backend container configured to use RDS. The local MySQL volume is preserved untouched as a
lossless rollback net until a confidence window elapses.

Detailed operator procedure (copy-pasteable, gated) lives in `runbook.md` in this directory. This
`requirements.md` is the contract (what/why/invariants); `runbook.md` is the execution checklist (how).

## Scope (read for context)

- `docker-compose.yml` — service topology, MySQL tuning flags, mounts, memory limits
- `backend/src/main/resources/application.properties`, `application-prod.properties` — DB URL, Flyway, TLS
- `mysql/Dockerfile`, `mysql/entrypoint.sh` — source image (stock 8.0 + healthcheck cnf; nothing to port)
- `backend/src/main/resources/db/migration/` — schema head (`V044`); charset pinned in `V000`
- `.github/workflows/deploy.yml`, `scripts/deploy/*` — pipeline that regenerates `.env` and force-recreates
- `nginx/locations.conf` — the `/tmp/maintenance-flag` 503 mechanism used for the cutover window

## Target (create / modify)

**Create**
- `terraform/rds/` — `main.tf`, `variables.tf`, `outputs.tf` (RDS instance, DB subnet group, parameter
  group, security group, temporary RDS→source ingress rule, `prevent_destroy`/`deletion_protection`,
  `manage_master_user_password`, backups, maintenance window). Outputs the endpoint.
- `docs/tasks/030-rds-migration/runbook.md` — the human execution rulebook.
- (Operational, not committed) the RDS CA bundle file mounted into the backend container.

**Modify (Commit 2 — schema-neutral, the cutover deploy)**
- `docker-compose.yml` — remove the `mysql` service + `backend.depends_on.mysql`; change
  `MYSQL_HOST: mysql` → `MYSQL_HOST: ${MYSQL_HOST}`; mount the RDS CA bundle `:ro`.
- `application-prod.properties` — JDBC + Flyway URLs to `sslMode=VERIFY_CA` with the CA truststore.
- SSM/secret: set `MYSQL_HOST` to the RDS endpoint (value from the Terraform output; not committed).

## Impact Analysis

- **Files modified**: `docker-compose.yml` (HIGH — removing a service + repointing the DB host),
  `application-prod.properties` (MEDIUM — TLS mode). New `terraform/rds/` (isolated).
- **Dependencies**: the deploy pipeline (`scripts/deploy/setup-env.sh`) regenerates `.env` every deploy —
  it MUST source `MYSQL_HOST` from SSM, or the next deploy resurrects local MySQL. Backend `restart: "no"`
  means a bad RDS connection does not auto-recover.
- **Ripple effects**: per-query latency rises (localhost → network hop) — N+1 paths amplify. DB
  observability moves from container logs to RDS CloudWatch metrics. Frontend (Cloudflare), JWT keys,
  OAuth, Redis-less SSE state — all unaffected (backend-only migration).

## Risk Assessment

> Full register (30 items) condensed; blocking items marked 🔴. Mitigations are realized as gated steps
> in `runbook.md`.

- **Data integrity**: 🔴 drift/double-apply at the snapshot seam → `--single-transaction --source-data=2`
  + GTID (clean ≤X / >X partition); 🔴 binlog purged before RDS reaches the snapshot GTID →
  `binlog_expire_logs_seconds` ≫ load window; dump omits routines/triggers/events → explicit flags.
- **Replication**: 🔴 RDS must pull from source → temporary source exposure + tight SG; TLS on the
  replication link (`ssl=1`); engine version source ≤ RDS; replication can stop mid-sync → monitor +
  alarm; GTID online-enable has live-source constraints → follow the ordered procedure.
- **Infra/Terraform**: 🔴 a forcing change destroys the DB → `prevent_destroy` + `deletion_protection` +
  `skip_final_snapshot=false`; master password in tfstate → `manage_master_user_password`; hand-editing a
  TF-managed SG reverts on apply → temp rule lives in TF; cross-AZ cost/latency → same-AZ; sql_mode /
  time_zone / charset mismatch → parameter group parity; backups off → explicit retention.
- **Cutover/app**: 🔴 next deploy resurrects local DB → Commit 2 removes `mysql` svc + sources `MYSQL_HOST`
  from SSM; scheduler writes lost → stop Spring (not just the 503 flag); VERIFY_CA misconfig + `restart:"no"`
  → pre-cutover connectivity probe; 🔴 rollback is lossy after promote → hard go/no-go gate at promote.
- **Post-migration**: standing cost accepted (db.t4g.micro); RDS alarms (CPU, FreeableMemory,
  DatabaseConnections, ReplicaLag); surprise minor-version upgrade restart → defined maintenance window.
- **Edge — schema**: any migration that creates a derived/index table (e.g. the `V039`+`V040` pattern)
  must land its backfill **before** the snapshot; no schema change between snapshot and promote.

## Boundaries & Invariants

- **Trust/ownership boundary**: Terraform owns infra (the empty RDS instance + networking) and MUST NOT
  own data. The data load + replication + promote are imperative operator actions. The deploy pipeline
  owns the running app config (the source of truth for `MYSQL_HOST`).
- **I1 — Lossless rollback until promote**: the on-box MySQL stays authoritative and is never written by
  the new path until RDS is promoted; before promote, aborting loses zero data.
- **I2 — Exactly-once write coverage**: every committed write is in exactly one of {snapshot, replicated
  tail}, partitioned at the snapshot GTID — no gap (lost write) and no overlap (double-apply).
- **I3 — Single DB host after cutover**: post-cutover, `MYSQL_HOST` resolves to RDS through every path
  (running config *and* the next pipeline run); nothing can resurrect local MySQL.
- **I4 — Terraform never destroys data**: no Terraform operation may destroy or force-replace the
  data-bearing RDS instance; such a plan must fail, not proceed.
- **I5 — Schema frozen across the sync**: no schema migration is applied to the source between the
  snapshot and the promote.

## Failure Modes

| Invariant | Trigger (how it breaks) | Response | Test |
|-----------|-------------------------|----------|------|
| I2 | mysqldump run without `--single-transaction` → smeared snapshot, post-X writes leak into dump and also replay | GTID dedups exact-GTID re-applies; but the guard is `--single-transaction` enforcing the clean X boundary | Rehearsal row-count + checksum compare source vs RDS pre-promote |
| I2 | Load takes longer than binlog retention → snapshot GTID purged on source → replication can't start | `binlog_expire_logs_seconds` ≫ load window; if it ever happens, re-seed (idempotent) | Verify `Replica_IO_Running=Yes` + no `ER_MASTER_FATAL...` after start |
| I1 | Operator aborts mid-load / process dies before promote | Source still authoritative + serving; restart Spring on source, drop flag → 0 loss | Abort drill in rehearsal: kill load, confirm site healthy on source |
| I3 | Next `git push` triggers pipeline; `setup-env.sh` regenerates `.env` with old `MYSQL_HOST=mysql` | Commit 2 removes the `mysql` service + sources `MYSQL_HOST` from SSM → no local DB to fall back to | Post-cutover, trigger a no-op deploy; assert backend still on RDS |
| I4 | A future TF edit changes a replace-forcing attribute | `prevent_destroy` makes `terraform plan/apply` error instead of destroying | `terraform plan` of a forcing change in rehearsal → expect error |
| I5 | A migration deploys to source while replication is live → DDL replays mid-stream, collides at cutover Flyway | Schema freeze rule; Commit 2 carries no migration; land all migrations pre-snapshot | Confirm `git diff` of Commit 2 touches no `db/migration/**` |
| I2/I3 | Two callers interleave at the promote instant (a late write races the quiesce) | Stop Spring first (kills inbound + `@Scheduled` writers) → source quiesced before the lag-drain gate | Promote checklist gates on `Seconds_Behind_Source=0` AND GTID sets equal |

### Visualized Failure (worst row: I3 — silent revert)

1. Cutover succeeds; backend runs on RDS; site healthy; everyone relaxes.
2. Days later, an unrelated backend change is pushed to `main`.
3. The pipeline runs `setup-env.sh`, which regenerates `.env`. In the un-mitigated world it still
   contains `MYSQL_HOST=mysql` and a `mysql` service in compose → it starts a fresh empty local MySQL
   and points the backend at it.
4. **Broken state**: the site is now live on an empty database — every user's data appears gone, though
   RDS still holds it. Silent, and discovered only by users.
   → **Response (I3)** intervenes at step 3: because Commit 2 deleted the `mysql` service and sources
   `MYSQL_HOST` from SSM (now the RDS endpoint), there is no local DB to start and nothing to repoint —
   the regenerated `.env` keeps pointing at RDS. The revert is structurally impossible, not merely avoided.

## Done When

- [ ] `terraform plan` for `terraform/rds/` is clean; `prevent_destroy`/`deletion_protection` present;
      a deliberately replace-forcing change makes `plan` error.
- [ ] RDS instance is up, same AZ as EC2, reachable from EC2 over TLS; parameter group matches source
      `@@sql_mode`, `time_zone=UTC`, utf8mb4; automated backups enabled.
- [ ] GTID enabled on source (`gtid_mode=ON`, `enforce_gtid_consistency=ON`, persisted); replication user
      created; source temporarily reachable by RDS on 3306 (tight SG).
- [ ] Replication running: `Replica_IO_Running=Yes`, `Replica_SQL_Running=Yes`, `Seconds_Behind_Source`
      stable near 0; source and RDS executed-GTID sets converge.
- [ ] Pre-promote validation: row counts (and a content checksum on a Korean-text table) match source vs RDS.
- [ ] Cutover executed: 503 flag → Spring stopped → lag=0 + GTID equal → RDS promoted
      (`rds_reset_external_master`) → Commit 2 deployed → backend healthy on RDS.
- [ ] App DB user `danteplanner` created on RDS with the existing password + schema privileges
      (users/grants do NOT transfer via `mysqldump <db>`); `danteplanner` can connect over VERIFY_CA.
- [ ] Terraform runs under a dedicated provisioning identity, separate from runtime/SSM access
      (`sts get-caller-identity` confirms; specific role kept out of this public repo).
- [ ] Post-cutover smoke: `/actuator/health` green, a read, a write, Korean string round-trips intact.
- [ ] Revert-proof: a subsequent no-op deploy keeps the backend on RDS (does not start local MySQL).
- [ ] RDS CloudWatch alarms exist (CPU, FreeableMemory, DatabaseConnections; ReplicaLag during sync).
- [ ] Rollback net intact: `mysql-data` volume preserved; decommission deferred ≥ 7 days.
- [ ] All existing backend tests pass (no app-logic change; schema head unchanged at `V044`).

## Test Plan

### Runner
- Backend unit/integration: `./gradlew test` (JUnit) — used only to confirm no regression; this migration
  changes no application logic, so the substantive verification is operational (below).

### Operational verification (realizes every Failure-Modes `Test` cell)
- [ ] **Rehearsal** on a throwaway RDS (or the real target before go-live): full snapshot → load →
      GTID replication → row-count + checksum compare → practice promote → reset. Proves the data path end-to-end.
- [ ] **Abort drill**: during rehearsal, kill the load mid-flight; confirm the site stays healthy on source
      and a restart of Spring on source loses nothing (I1).
- [ ] **Binlog-retention check**: confirm `binlog_expire_logs_seconds` exceeds measured load time with margin (I2).
- [ ] **Connectivity probe** (pre-cutover): from EC2, `nc -zv <rds> 3306` + a TLS `mysql` login with the CA bundle (app path); confirm RDS→source pull is established (`Replica_IO_Running=Yes`).
- [ ] **Promote gate**: assert `Seconds_Behind_Source=0` AND `Executed_Gtid_Set` equal on both before promote.
- [ ] **TF destroy-guard test**: a replace-forcing `terraform plan` errors out (I4).
- [ ] **Revert test** (post-cutover): trigger a no-op pipeline deploy; assert backend remains on RDS (I3).
- [ ] **Schema-neutrality check**: `git diff` of Commit 2 touches no `db/migration/**` (I5).

## Verification

### Manual
1. Follow `runbook.md` Zone 0 → 4 in order; each zone ends with its checklist satisfied before proceeding.
2. The promote step (Zone 1) is the point of no return — do not cross it until the promote gate passes.

### Edge Cases
- [ ] Empty/near-empty tables: snapshot+replication handles them (no special case).
- [ ] Korean text (utf8mb4_unicode_ci): round-trips after cutover (smoke test asserts this).
- [ ] Source restart during Zone 0 (e.g. a deploy): GTID is persisted (`SET PERSIST`) so it survives; verify
      `gtid_mode=ON` after any source restart before relying on replication.
- [ ] A migration queued mid-project: must land before the snapshot, or wait until after promote (applied to RDS).
- [ ] App user/grants absent on RDS (`mysqldump <db>` excludes the `mysql` schema): created as an explicit
      control-plane step in Zone 0 (runbook 0.6b), not via replication.
