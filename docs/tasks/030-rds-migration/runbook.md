# RDS Migration — Operator Rulebook

> Human execution checklist for the MySQL → RDS GTID native-replica cutover.
> Read alongside `requirements.md` (the contract). Work top-to-bottom. **Do not skip a gate.**
> Placeholders: `<RDS_ENDPOINT>` `<SOURCE_PRIVATE_IP>` `<DB>` `<REPL_PW>` `<RDS_ADMIN_PW>`.

## Golden rules (read once, hold the whole time)

1. **The on-box `mysql-data` volume is sacred.** Never `docker compose down -v`, never `docker volume
   rm/prune` until ≥ 7 days after a verified cutover. It is your only lossless rollback.
2. **The point of no return is PROMOTE (Zone 1, step 6).** Before it, abort is free. After it, RDS is
   authoritative and rolling back to local loses every post-promote write.
3. **Terraform owns infra, never data.** Never let Terraform manage the dump/replication. Never hand-edit
   a TF-managed security group (the next `apply` reverts it).
4. **One thing at a time.** Each zone's checklist must be green before the next zone.
5. If anything is unexpected at a gate — **stop, do not improvise across the PONR.**

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

### 0.6 Seed RDS — consistent snapshot WITH GTID coordinate
```bash
docker exec danteplanner-mysql mysqldump \
  --single-transaction --source-data=2 --set-gtid-purged=ON \
  --routines --triggers --events --default-character-set=utf8mb4 \
  -uroot -p <DB> > /tmp/seed.sql

mysql -h <RDS_ENDPOINT> -P 3306 \
  --ssl-ca=/path/rds-combined-ca-bundle.pem --ssl-mode=VERIFY_CA \
  -u admin -p'<RDS_ADMIN_PW>' <DB> < /tmp/seed.sql
```
- [ ] Seed loaded. (`--single-transaction` = clean snapshot boundary; `--set-gtid-purged=ON` records the
      executed-GTID set so auto-position knows where to continue. proves I2's clean partition.)

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

### 0.7 Start GTID replication on RDS
```sql
-- run on RDS: mysql -h <RDS_ENDPOINT> ... -u admin -p
CALL mysql.rds_set_external_master_with_auto_position(
  '<SOURCE_PRIVATE_IP>', 3306, 'repl', '<REPL_PW>', 1 /* ssl=on */ );
CALL mysql.rds_start_replication;
```

### 0.8 Watch it catch up — and stay caught up (SITE IS LIVE the whole time)
```sql
SHOW REPLICA STATUS\G   -- on RDS
```
- [ ] `Replica_IO_Running = Yes` and `Replica_SQL_Running = Yes`
- [ ] `Seconds_Behind_Source` falls to ~0 and stays stable
- [ ] No `Last_Error` / `Last_IO_Error`
- [ ] Set a temporary CloudWatch alarm on `ReplicaLag`.

### 0.9 Validate consistency (pre-promote proof)
- [ ] Row counts match source vs RDS for the top tables.
- [ ] Checksum a Korean-text-bearing table both sides and compare (proves charset round-trip).

### 0.10 Prepare Commit 2 (DO NOT MERGE yet)
- [ ] Branch with: `docker-compose.yml` — remove `mysql` service + `backend.depends_on.mysql`;
      `MYSQL_HOST: ${MYSQL_HOST}`; mount RDS CA bundle `:ro`.
- [ ] `application-prod.properties` — JDBC + Flyway URLs `sslMode=VERIFY_CA` with the CA truststore.
- [ ] **Schema-neutral check:** `git diff` of the branch touches **no** `db/migration/**`. (proves I5)
- [ ] Set SSM `MYSQL_HOST` = `<RDS_ENDPOINT>`.

### 0.11 Schema freeze
- [ ] From here until promote: **deploy no Flyway migration.** Current head is `V044`; any new migration
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
   history (head `V044`) → no-op.
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

## Zone 2 — Post-cutover verification

- [ ] **Revert-proof:** trigger a no-op deploy → backend stays on RDS, no local MySQL starts. (proves I3)
- [ ] RDS CloudWatch alarms live: CPU, FreeableMemory, DatabaseConnections.
- [ ] Watch p95 latency for N+1 amplification (localhost → network hop). Note any endpoint that regresses.

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
Zone 3 rollback:      before PONR = start Spring on local (0 loss); after = fix forward
Zone 4 (+7d):         remove temp exposure → drop repl user → delete volume
NEVER:                down -v / volume prune before Zone 4 · hand-edit TF-managed SG
```
