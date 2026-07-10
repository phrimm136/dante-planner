# RDS Migration — Operator Rulebook

> Human execution checklist for the MySQL → RDS GTID native-replica cutover.
> Read alongside `requirements.md` (the contract). Work top-to-bottom. **Do not skip a gate.**
> Placeholders: `<RDS_ENDPOINT>` `<SOURCE_PRIVATE_IP>` `<DB>` `<REPL_PW>` `<RDS_ADMIN_PW>`
> `<EC2_INSTANCE_ID>` `<RDS_MASTER_SECRET_ARN>` (Secrets Manager ARN from `manage_master_user_password`,
> = the `master_user_secret_arn` Terraform output).

## Golden rules (read once, hold the whole time)

1. **The on-box `mysql-data` volume is sacred.** Never `docker compose down -v`, never `docker volume
   rm/prune` until ≥ 7 days after a verified cutover. It is your only lossless rollback.
2. **The point of no return is PROMOTE (Zone 1, step 6).** Before it, abort is free. After it, RDS is
   authoritative and rolling back to local loses every post-promote write.
3. **Terraform owns infra, never data.** Never let Terraform manage the dump/replication. Never hand-edit
   a TF-managed security group (the next `apply` reverts it).
4. **One thing at a time.** Each zone's checklist must be green before the next zone.
5. If anything is unexpected at a gate — **stop, do not improvise across the PONR.**
6. **Deploy freeze — broader than the schema freeze (0.11).** From 0.5 (source exposure) until Zone 1
   completes, nothing merges to `main` and no pipeline deploy runs. A deploy regenerates `.env` — and an
   `.env` missing `SOURCE_PRIVATE_IP` rebinds the source port to 127.0.0.1, silently severing RDS's IO
   thread — force-recreates the source MySQL container mid-replication, and could carry a schema
   migration past the freeze. Concurrent code work (e.g. `/build` on 034) stays on `dev` for the window.

---

## Zone 0 — Pre-flight + live sync  (NO downtime · fully reversible · do days ahead)

### 0.0 Measure the DB (sizes your retention margin)
```bash
docker exec danteplanner-mysql mysql -uroot -p -e \
  "SELECT table_schema, ROUND(SUM(data_length+index_length)/1024/1024) AS mb \
   FROM information_schema.tables GROUP BY 1;"
```
- [ ] DB size recorded: __________ MB. (Confirms the ASSUMED "small" — adjust retention if large.)

### 0.1 Commit 1 — provision RDS (Terraform, out-of-band)
- [ ] **IAM prereq:** Terraform runs under a dedicated least-privilege provisioning credential, separate
      from any runtime/SSM access (separation of duties). Configure it per your **private ops notes**
      (role/ARN/policy details are kept OUT of this public repo); confirm with
      `aws sts get-caller-identity` before applying.
- [ ] `terraform/rds/` written: db.t4g.micro, single-AZ **pinned to EC2's AZ**, gp3, parameter group
      (utf8mb4, `time_zone=UTC`, `sql_mode` = source's, `long_query_time=1`, `log_output=FILE`),
      `backup_retention_period=7`, defined maintenance window, `auto_minor_version_upgrade=false`,
      `manage_master_user_password=true`, `deletion_protection=true`, `lifecycle{prevent_destroy=true}`.
- [ ] SG: permanent EC2→RDS:3306 rule **and** a temporary RDS→source:3306 rule (RDS subnet CIDR only).
```bash
cd terraform/rds
terraform plan      # review: creating RDS + SG + subnet group + param group
terraform apply
terraform output rds_endpoint     # → put this value into SSM (next step)
```
- [ ] RDS up. Endpoint: __________________________
- [ ] **Sizing check (supersession from `docs/tasks/034`):** decide micro→small BEFORE cutover —
      buffer pool, not connections, is the constraint at 50k MAU. A resize now is free; after promote
      it costs a failover/restart window.
- [ ] **Destroy-guard test:** temporarily edit a replace-forcing attr (e.g. instance identifier) and
      `terraform plan` → it MUST error on `prevent_destroy`. Revert the edit. (proves I4)

### 0.2 Capture the source @@sql_mode into the param group
```bash
docker exec danteplanner-mysql mysql -uroot -p -e "SELECT @@sql_mode, @@time_zone, @@binlog_format;"
```
- [ ] Param group `sql_mode` matches source. (`binlog_format` should already be `ROW` on 8.0.)

### 0.3 Enable GTID on the source (online, no restart — ORDER MATTERS)
```sql
-- run in: docker exec -it danteplanner-mysql mysql -uroot -p
SET PERSIST server_id = 10;                      -- any value distinct from RDS
SET PERSIST binlog_expire_logs_seconds = 604800; -- 7d; must exceed load time with margin
SET PERSIST_ONLY binlog_format = ROW;            -- if not already ROW
SET @@GLOBAL.enforce_gtid_consistency = WARN;    -- check error log for violations, then:
SET @@GLOBAL.enforce_gtid_consistency = ON;
SET @@GLOBAL.gtid_mode = OFF_PERMISSIVE;
SET @@GLOBAL.gtid_mode = ON_PERMISSIVE;
-- wait until this is 0 before the final step:
SHOW STATUS LIKE 'ongoing_anonymous_transaction_count';
SET @@GLOBAL.gtid_mode = ON;
SET PERSIST gtid_mode = ON;                       -- persist across restarts
SET PERSIST enforce_gtid_consistency = ON;
```
- [ ] `SELECT @@gtid_mode;` → `ON`. (`SET PERSIST` survives a container restart via the data volume.)

### 0.4 Create the replication user
```sql
CREATE USER 'repl'@'%' IDENTIFIED BY '<REPL_PW>';   -- tighten host to RDS subnet if feasible
GRANT REPLICATION SLAVE ON *.* TO 'repl'@'%';
```
- [ ] repl user created.

### 0.5 Temporarily expose the source to RDS
- [ ] Publish source MySQL 3306 on the EC2 **private** IP (temporary; torn down in Zone 4). Keep the SG
      rule from 0.1 scoped to the RDS subnet only.
- [ ] Verify from a host RDS can use: source reachable on `<SOURCE_PRIVATE_IP>:3306`.
- [ ] `scripts/deploy/setup-env.sh` sources `SOURCE_PRIVATE_IP` into the regenerated `.env` — or rely
      on golden rule 6's deploy freeze. (Without either, the next deploy defaults the publish to
      `127.0.0.1` and replication dies silently until the 0.8 alarm fires.)

### 0.6 Seed RDS — consistent snapshot WITH GTID coordinate
> **Use `=COMMENTED`, not `=ON`.** RDS never grants the master user SUPER/SYSTEM_VARIABLES_ADMIN, so a
> dump with `--set-gtid-purged=ON` fails on load at the `SET @@GLOBAL.GTID_PURGED` line (ERROR 1227).
> `=COMMENTED` writes that GTID set as a readable comment instead; you feed the value to RDS via
> `mysql.rds_set_external_source_gtid_purged` in 0.7 (needs RDS engine >= 8.0.37).
```bash
docker exec danteplanner-mysql mysqldump \
  --single-transaction --source-data=2 --set-gtid-purged=COMMENTED \
  --routines --triggers --events --default-character-set=utf8mb4 \
  -uroot -p <DB> > /tmp/seed.sql

grep -i 'GTID_PURGED' /tmp/seed.sql          # copy the '<uuid>:1-N,...' set — you need it in 0.7

mysql -h <RDS_ENDPOINT> -P 3306 \
  --ssl-ca=/path/rds-combined-ca-bundle.pem --ssl-mode=VERIFY_CA \
  -u admin -p'<RDS_ADMIN_PW>' <DB> < /tmp/seed.sql
```
> **If you run the mysql client from *inside* the `danteplanner-mysql` container, add `--no-defaults`.**
> The container's `~/.my.cnf` carries a `password=` line that silently overrides `-p`/`MYSQL_PWD` (an
> option-file password wins; `MYSQL_PWD` is only used when none is set) — so it sends the LOCAL root
> password to RDS and you get an endless "Access denied for admin" that no RDS password reset fixes.
- [ ] Seed loaded. (`--single-transaction` = clean snapshot boundary; `=COMMENTED` preserves the
      executed-GTID set as a comment for 0.7. proves I2's clean partition.)
- [ ] Dump ran in a low-traffic window — mysqldump on the 4GB no-swap box is itself a memory-pressure
      risk (the failure class this migration exists to cure). No load tests (e.g. 034's RSS gate)
      anywhere in the Zone 0 sync window; schedule those before Zone 0 or after cutover.

### 0.6b Create the application DB user on RDS (does NOT transfer via mysqldump)
> `mysqldump <DB>` dumps data, not users/grants — those live in the `mysql` system schema. The app
> connects as `danteplanner` (and Flyway migrates as the same user), so it must exist on RDS with the
> SAME password (the existing `MYSQL_PASSWORD` in SSM) and DDL+DML privileges, or the cutover backend
> can't authenticate / Flyway can't run.
```sql
-- run on RDS as the master user
CREATE USER 'danteplanner'@'%' IDENTIFIED BY '<EXISTING_MYSQL_PASSWORD>';
GRANT ALL PRIVILEGES ON <DB>.* TO 'danteplanner'@'%';   -- schema-scoped; RDS forbids global SUPER anyway
FLUSH PRIVILEGES;
```
- [ ] App user `danteplanner` exists on RDS, same password as SSM `MYSQL_PASSWORD`.
- [ ] Verify: `mysql -h <RDS_ENDPOINT> --ssl-mode=VERIFY_CA --ssl-ca=... -u danteplanner -p <DB> -e "SELECT 1;"`
      (Replication keeps *data* in sync; this grant is a one-time control-plane step, not replicated.)

### 0.7 Start GTID replication on RDS — driven by `aws ssm send-command`

> No native `aws rds` command exists for an EXTERNAL source (`aws rds ...read-replica` is RDS→RDS only).
> The `mysql.rds_*` procedures need a client that reaches the **private** RDS endpoint: the in-VPC EC2
> host can (app→RDS SG rule), your laptop usually cannot. Run them on EC2 via SSM (no SSH/bastion), and
> fetch secrets on the box so they never land in SSM history / CloudTrail. Prereqs: SSM agent on the
> instance + its role allows `ssm:SendCommand`, `secretsmanager:GetSecretValue`, `ssm:GetParameter`; the
> RDS CA bundle present on the box at `/path/rds-combined-ca-bundle.pem`.
>
> **Prereq — set the GTID coordinate the RDS way (the load couldn't).** With RDS engine >= 8.0.37 and
> `@@GLOBAL.gtid_executed` empty, `mysql.rds_set_external_source_gtid_purged(server_uuid, start_pos,
> end_pos)` replaces the blocked `SET @@GLOBAL.GTID_PURGED`. It takes the GTID **split into 3 args** —
> the `uuid:1-N` from 0.6 becomes `('<uuid>', 1, N)`. **One contiguous range per call**: if the set has
> gaps (`uuid:1-100:150-200`) or multiple UUIDs, call it once per range. It MUST run before
> auto-position, so it's the first CALL in the SQL below.

```bash
# Stash the repl password in SSM Parameter Store (SecureString) so it isn't inlined anywhere:
aws ssm put-parameter --name /danteplanner/repl_password --type SecureString --value '<REPL_PW>'

CMD_ID=$(aws ssm send-command \
  --instance-ids "<EC2_INSTANCE_ID>" \
  --document-name "AWS-RunShellScript" \
  --comment "RDS: set external master + start replication" \
  --parameters commands='[
    "set -euo pipefail",
    "ADMIN_PW=$(aws secretsmanager get-secret-value --secret-id <RDS_MASTER_SECRET_ARN> --query SecretString --output text | jq -r .password)",
    "REPL_PW=$(aws ssm get-parameter --name /danteplanner/repl_password --with-decryption --query Parameter.Value --output text)",
    "mysql -h <RDS_ENDPOINT> --ssl-mode=VERIFY_CA --ssl-ca=/path/rds-combined-ca-bundle.pem -u admin -p\"$ADMIN_PW\" -e \"CALL mysql.rds_set_external_source_gtid_purged('\''<SOURCE_UUID>'\'', <START>, <END>); CALL mysql.rds_set_external_master_with_auto_position('\''<SOURCE_PRIVATE_IP>'\'', 3306, '\''repl'\'', '\''$REPL_PW'\'', 1, 0); CALL mysql.rds_start_replication;\""
  ]' \
  --query Command.CommandId --output text)

# SSM nests the result in JSON — parse with jq (the raw API response is not the payload):
aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "<EC2_INSTANCE_ID>" \
  | jq -r '"status=\(.Status)", .StandardOutputContent, .StandardErrorContent'
```
- [ ] Command `Status = Success`, `StandardErrorContent` empty. (The nested SQL quoting is fiddly —
      dry-run the transport once with a harmless `-e \"SELECT 1;\"` before the real CALL.)

### 0.8 Watch it catch up — and stay caught up (SITE IS LIVE the whole time)
```bash
CMD_ID=$(aws ssm send-command \
  --instance-ids "<EC2_INSTANCE_ID>" \
  --document-name "AWS-RunShellScript" \
  --comment "RDS: replica status" \
  --parameters commands='[
    "ADMIN_PW=$(aws secretsmanager get-secret-value --secret-id <RDS_MASTER_SECRET_ARN> --query SecretString --output text | jq -r .password)",
    "mysql -h <RDS_ENDPOINT> --ssl-mode=VERIFY_CA --ssl-ca=/path/rds-combined-ca-bundle.pem -u admin -p\"$ADMIN_PW\" -e \"SHOW REPLICA STATUS\\G\""
  ]' \
  --query Command.CommandId --output text)

aws ssm get-command-invocation --command-id "$CMD_ID" --instance-id "<EC2_INSTANCE_ID>" \
  | jq -r '.StandardOutputContent' \
  | grep -E 'Replica_(IO|SQL)_Running|Seconds_Behind_Source|Last_(IO_)?Error'
```
(`ReplicaLag` is also a native CloudWatch metric — `aws cloudwatch get-metric-statistics
--namespace AWS/RDS --metric-name ReplicaLag` — if you prefer a no-SQL check.)
- [ ] `Replica_IO_Running = Yes` and `Replica_SQL_Running = Yes`
- [ ] `Seconds_Behind_Source` falls to ~0 and stays stable
- [ ] No `Last_Error` / `Last_IO_Error`
- [ ] Set a temporary CloudWatch alarm on `ReplicaLag`.

### 0.9 Validate consistency (pre-promote proof)
- [ ] Row counts match source vs RDS for the top tables.
- [ ] Checksum a Korean-text-bearing table both sides and compare (proves charset round-trip).

### 0.9b Dry-run the 034 GTID-gate assumption (cheap; the RDS instance is idle anyway)
> `docs/tasks/034`'s read-your-own-write gate assumes `session_track_gtids=OWN_GTID` is
> session-settable on RDS and surfaced by Connector/J. Verify it now on the live replica instead of
> discovering it mid-build (same SSM transport as 0.7/0.8):
```sql
SET SESSION session_track_gtids = OWN_GTID;   -- must not error
-- then any write + confirm the OK packet carries the GTID (or fallback: SELECT @@gtid_executed)
```
- [ ] Settable on RDS: yes / no. If no → 034's gate falls back to `SELECT @@gtid_executed` post-commit
      (one extra round trip); record the finding in `docs/tasks/034/mechanics.md` §9.

### 0.10 Prepare Commit 2 (DO NOT MERGE yet)
- [ ] Branch with: `docker-compose.yml` — remove `mysql` service + `backend.depends_on.mysql`;
      `MYSQL_HOST: ${MYSQL_HOST}`; mount RDS CA bundle `:ro`.
- [ ] `application-prod.properties` — JDBC + Flyway URLs `sslMode=VERIFY_CA` with the CA truststore.
- [ ] **Schema-neutral check:** `git diff` of the branch touches **no** `db/migration/**`. (proves I5)
- [ ] Set SSM `MYSQL_HOST` = `<RDS_ENDPOINT>`.

### 0.11 Schema freeze
- [ ] From here until promote: **deploy no Flyway migration.** Current head is `V045`; any new migration
      waits until after promote (applied directly to RDS).

### 0.12 Connectivity probe (app path)
```bash
nc -zv <RDS_ENDPOINT> 3306
mysql -h <RDS_ENDPOINT> --ssl-ca=/path/rds-combined-ca-bundle.pem --ssl-mode=VERIFY_CA -u admin -p -e "SELECT 1;"
```
- [ ] App path to RDS works over VERIFY_CA. (Backend is `restart:"no"` — a bad path means it stays down.)

**Zone 0 gate — ALL boxes above checked before touching Zone 1.** Site has had zero downtime so far.

---

## Zone 1 — Cutover  (downtime = SECONDS · pick a low-traffic window, e.g. ~04:00 KST)

1. **Raise maintenance flag** (nginx stays up, friendly 503):
   ```bash
   docker exec danteplanner-nginx touch /tmp/maintenance-flag
   ```
   - [ ] Wait a few seconds for in-flight requests to drain.
2. **Stop Spring** (freezes ALL writes incl. `@Scheduled` jobs):
   ```bash
   docker stop danteplanner-backend
   ```
3. **PROMOTE GATE — do not cross unless BOTH true:**
   - [ ] `Seconds_Behind_Source = 0`
   - [ ] `Executed_Gtid_Set` equal on source and RDS
4. (No-go? → go to **Abort**, you have lost nothing.)
5. **Promote RDS** to standalone primary:
   ```sql
   CALL mysql.rds_stop_replication;
   CALL mysql.rds_reset_external_master;   -- ⟵ POINT OF NO RETURN
   ```
6. **Deploy Commit 2** (merge → pipeline builds + deploys backend onto RDS). Flyway sees the replicated
   history (head `V045`) → no-op.
7. **Verify (still flagged):**
   - [ ] `/actuator/health` green
   - [ ] a read returns real data
   - [ ] a write succeeds
   - [ ] Korean string round-trips
8. **Drop the flag:**
   ```bash
   docker exec danteplanner-nginx rm -f /tmp/maintenance-flag
   ```

---

## Zone 2 — Post-cutover verification + upgrades

- [ ] **Revert-proof:** trigger a no-op deploy → backend stays on RDS, no local MySQL starts. (proves I3)
- [ ] RDS CloudWatch alarms live: CPU, FreeableMemory, DatabaseConnections.
- [ ] Watch p95 latency for N+1 amplification (localhost → network hop). Note any endpoint that regresses.
- [ ] **Enable Multi-AZ** (supersession from `docs/tasks/034`: bought, deliberately deferred to
      post-cutover — a sync standby during Zone 0 only slows catch-up and widens the I2 window).
      `multi_az = true` in `terraform/rds/` → `apply` in a quiet window; expect brief I/O elevation
      while the standby builds. Verify: `aws rds describe-db-instances` shows `MultiAZ: true`.
- [ ] **Retire the temporary `ReplicaLag` alarm** from 0.8 — the metric goes stale after
      `rds_reset_external_master` and a stale-metric alarm is silence dressed as health.

---

## Zone 3 — Rollback

**Before promote (Zone 1 step 5):** trivial and lossless.
```bash
docker exec danteplanner-nginx rm -f /tmp/maintenance-flag   # if flagged
docker start danteplanner-backend                            # back on local MySQL
```
- Local DB was frozen at step 2, never written → 0 loss.

**After promote:** rolling back to local means losing every write since promote. Prefer fixing forward
on RDS. Only consider reverse-replication (RDS→local) if absolutely required — out of scope here.

---

## Zone 4 — Decommission  (≥ 7 days after a verified cutover)

- [ ] Remove the temporary RDS→source SG rule and the source port publish (close the hole) — via Terraform.
- [ ] Drop the `repl` user on the (now-retired) source.
- [ ] Remove the `mysql` service build context (`mysql/`) if fully unused.
- [ ] **Only now:** delete the `mysql-data` volume.

---

## One-screen cheat sheet

```
Zone 0 (days, live):  terraform apply → enable GTID → repl user → expose source
                      → mysqldump --single-transaction --source-data=2 → load RDS
                      → rds_set_external_master_with_auto_position → wait lag≈0
                      → validate → prep Commit 2 (schema-neutral) → freeze schema
Zone 1 (seconds):     flag ON → stop Spring → GATE(lag=0 & GTID equal)
                      → rds_stop/reset_external_master  ◄PONR► → deploy Commit 2
                      → verify → flag OFF
Zone 2 (post):        revert-proof deploy → alarms → multi_az=true → retire ReplicaLag alarm
Zone 3 rollback:      before PONR = start Spring on local (0 loss); after = fix forward
Zone 4 (+7d):         remove temp exposure → drop repl user → delete volume
NEVER:                down -v / volume prune before Zone 4 · hand-edit TF-managed SG
                      · deploy/merge to main between 0.5 and Zone 1 (golden rule 6)
```
