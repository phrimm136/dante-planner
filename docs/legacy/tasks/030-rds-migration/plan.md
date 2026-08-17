# Execution Plan — Phase 1 RDS Migration

## Phase Summary

This spec is **mostly operational**. Exactly one phase produces auto-buildable, committable code (the
Terraform module); the rest are **operator-executed** runbook actions that require AWS credentials, a
production maintenance window, and human go/no-go gates — they cannot and must not be run by an agent.

Cross-cutting invariants (hold across every phase):
- **I1 lossless-until-promote**: on-box MySQL stays authoritative and unwritten by the new path until
  RDS is promoted. Never `docker compose down -v` / `volume prune` the `mysql-data` volume until Zone 4.
- **I3 no-revert**: nothing may resurrect local MySQL after cutover — Commit 2 removes the `mysql`
  service and sources `MYSQL_HOST` from SSM.
- **I4 TF-never-destroys-data**: `prevent_destroy` + `deletion_protection` guard the RDS instance.
- **Open-source**: no account IDs / ARNs / real values in committed files — everything via variables;
  `terraform.tfvars` and state are gitignored.

## Phases

### Phase 1: Terraform `terraform/rds/` module  ← the only auto-buildable phase
- Files: `terraform/rds/{main.tf,variables.tf,outputs.tf,terraform.tfvars.example,.gitignore,README.md}`
- Tests: none runnable here (no AWS creds). Acceptance = `terraform validate` clean + a human-reviewed
  `terraform plan` against the operator's account (done in Phase 2 with creds).
- Considerations:
  - **Account-agnostic only** — region default is `us-west-2` (already public in `docker-compose.yml`
    awslogs); vpc/subnets/EC2-SG/AZ are variables with no defaults, supplied via gitignored tfvars.
  - **Destroy guards are load-bearing (I4)** — `lifecycle { prevent_destroy = true }`,
    `deletion_protection = true`, `skip_final_snapshot = false`.
  - **Master password via `manage_master_user_password`** (Secrets Manager), never in state/tfvars.
  - **GTID on the param group** — `gtid-mode=ON` + `enforce_gtid_consistency=ON` so RDS can be an
    external GTID replica; plus `time_zone=UTC`, utf8mb4, `binlog_format=ROW`, and a `sql_mode` var to be
    set from the source's captured `@@sql_mode` (runbook 0.2).
  - **Temporary replication ingress is a toggled resource** (`enable_replication_ingress`, default
    `false`) adding ingress to the *EC2* SG from the RDS SG — so it's TF-managed (no hand-edit drift)
    yet removable post-cutover by flipping the flag. Avoids owning the whole EC2 SG.
  - **db_name created** so RDS has the empty schema for the dump to load into; app user `danteplanner`
    is NOT created here (control-plane SQL step, runbook 0.6b).
- Depends on: none
- Verify: `terraform fmt -check` + `terraform validate` (syntactic); file scan shows no hardcoded
  account id / ARN / endpoint.

### Phase 2: Provision + establish replication  [OPERATOR — runbook Zone 0]
- Not agent-buildable. `terraform apply` (with the dedicated provisioning identity) → enable GTID on
  source → create repl user → expose source → seed via mysqldump → start GTID replication → watch lag→0
  → validate row counts/checksums → create app user `danteplanner` on RDS (0.6b).
- Verify: `Replica_IO_Running=Yes`, `Seconds_Behind_Source≈0`, GTID sets converge.

### Phase 3: Prepare Commit 2 (cutover diff) — DO NOT MERGE  [OPERATOR — runbook Zone 0 step 0.10]
- Not auto-applied (materializing it early risks premature cutover). On a dedicated branch off `dev`:
  remove `mysql` service + `backend.depends_on.mysql` from `docker-compose.yml`; set
  `MYSQL_HOST: ${MYSQL_HOST}`; mount RDS CA bundle `:ro`; `application-prod.properties` → `sslMode=VERIFY_CA`.
  Schema-neutral (no `db/migration/**` changes). Set SSM `MYSQL_HOST` = RDS endpoint.
- Verify: `git diff` touches no `db/migration/**`; branch not merged.

### Phase 4: Cutover  [OPERATOR — runbook Zone 1, low-traffic window]
- flag ON → stop Spring → PROMOTE GATE (lag=0 & GTID equal) → promote RDS → merge Commit 2 (= deploy)
  → smoke (health/read/write/Korean) → flag OFF. Point of no return = promote.

### Phase 5: Decommission  [OPERATOR — runbook Zone 4, ≥7 days later]
- Flip `enable_replication_ingress=false` + `terraform apply`; drop repl user; remove `mysql` service
  build context; only then delete `mysql-data` volume.

## Phase Dependencies
- Group A (buildable now): Phase 1
- Group B (operator, sequential, needs creds + prod window): Phase 2 → 3 → 4 → 5
