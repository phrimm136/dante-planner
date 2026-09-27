# Prod-account cutover — operator rulebook

Moves production from the management account onto the fleet already standing in
danteplanner-prod. The prod fleet runs the migrated schema against a copy of production data
and is verified in-VPC before any of this begins; what remains is to reload it from a final
dump and move the public hostname.

The management environment is never modified except to stop it. It stays intact as the
rollback for the whole window and for the bake period after.

## Golden rules

- **Rollback is a DNS change, until the very end and after it.** Nothing here destroys the
  management environment, so an abort at any step means restoring the records in
  "The hostname today" below and unfreezing.
- **Seoul freezes first, Oregon releases first.** Seoul writes cross-region into Oregon's
  primary, so the writer stops before its target; Oregon holds the primary and the durable
  auth Redis, so it comes back before the region that depends on both.
- **A drain is not done because the patch was accepted.** ArgoCD self-heal reverts a
  nodeSelector within one sync loop. The gates below are what prove it held.
- **`V052` is forward-only.** It asserts parity with `SIGNAL SQLSTATE '45000'` before it
  drops `planners`, so a failure leaves the old table intact. Investigate the mismatch; do
  not re-run it to see whether it passes the second time.
- **Nothing may deploy into the management account after the freeze.** A stray deploy there
  restores its DaemonSet from git and the new image migrates the management database, which
  is the rollback. See "After the window".

## The hostname today

`api.dante-planner.com` is served by two **proxied A records created by hand in the
Cloudflare dashboard**. No terraform stack in this repository owns them:
`terraform/global-accelerator` creates the accelerator, its listener and endpoint groups but
no DNS, and `terraform/cloudflare` holds a load balancer on a different hostname.

```
api.dante-planner.com   A   166.117.53.62    Proxied
api.dante-planner.com   A   99.83.184.149    Proxied
```

They are the two anycast addresses of the `danteplanner-entry` accelerator, which answers
`list-accelerators` with exactly this pair:

```bash
AWS_PROFILE=<management> aws globalaccelerator list-accelerators --region us-west-2 \
  --query 'Accelerators[].[Name,Status,IpSets[0].IpAddresses]' --output text
```

**These two addresses are the rollback.** Step 1 deletes them, and once deleted they are
recoverable only from here or from that command, so the accelerator must not be torn down
until the bake is over.

A Cloudflare load balancer occupies its hostname at the DNS layer and cannot coexist with A
records of the same name, so the deletion in step 1 is also what frees the hostname for
step 8.

## Environment facts

Look these up rather than trusting a value written here; instances and endpoints change.

```bash
# Control plane per region — every kubectl below runs on one of these, over SSM
aws ec2 describe-instances --region <region> \
  --filters "Name=tag:Name,Values=danteplanner-<region>-cp" \
            "Name=instance-state-name,Values=running" \
  --query 'Reservations[0].Instances[0].InstanceId' --output text

# Ingress private address, for the in-VPC smoke in step 7
aws ec2 describe-instances --region <region> \
  --filters "Name=tag:Name,Values=danteplanner-<region>-ingress" \
            "Name=instance-state-name,Values=running" \
  --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text

# Database endpoints
AWS_PROFILE=<prod> terraform -chdir=terraform/rds output -raw rds_endpoint
```

Two facts about the prod fleet that are not obvious from the manifests:

- Its cloudflared connects to **its own tunnels** (`danteplanner-prod-oregon` / `-seoul`),
  managed in the `prod-fleet` workspace of `terraform/cloudflare`. The management tunnels are
  separate, so a pool origin identifies which fleet is answering.
- Each ArgoCD Application carries a manual `spec.source.kustomize.images` override pinning
  the candidate tag, because the overlay's committed `newTag` names an image that exists only
  in the management account's registry. That override outranks the overlay.

## The window, and the two merges

Announced window: **20:00–22:00 KST**. The data work measures under five minutes; the rest is
freeze, gates and verification.

Two separate pull requests land on `main` around this window, at different times and with
different consequences.

**GitHub Actions cannot run for this migration.** `deploy-fleet.yml` would normally fire on a
successful PR Gate run on `main` and deploy the backend, so a merge would never be only a
merge; with CI unavailable it is. Everything the pipeline would have done — building the
arm64 image, pushing it to both regional registries, moving the deployed tag — is done by
hand instead, and the ArgoCD image override below is the mechanism that replaces the tag bump.

Cloudflare Pages builds from `main` through Cloudflare's own Git integration rather than
through Actions, so it publishes independently of the rest of CI. That is what makes the
announcement below deliverable at all while the pipeline is down.

**Before the window — the announcement PR.** Announcements are not fetched from the API:
`useAnnouncementData` resolves them with `import('@static/data/announcements.json')`, so they
are bundled into the SPA at build time. That is what makes them the one thing still visible
while the API is down, and it is also why the announcement has to ship in a build *before* the
window rather than during it. If Pages cannot build, there is no way to publish an
announcement at all, and the window runs with users seeing unexplained API errors on a page
that otherwise loads.

**After the window — the candidate PR.** With CI down this merge deploys nothing; it is there
so `main` finally carries what production runs. Ordering is in "After the window" below.

## Preconditions

- [ ] Prod fleet healthy in both regions: backend `1/1`, `/healthz-local` 200 through each
      region's ingress private address, `/actuator/health` UP.
- [ ] Prod schema at V060 with the decomposed tables populated, and the Seoul replica
      carrying the same counts at zero lag.
- [ ] A maintenance page published and reachable, and the record that will point at it
      decided (step 1).
- [ ] The `danteplanner-entry` accelerator still `DEPLOYED` and enabled — it is what the
      rollback addresses answer for.

## Measured timings

Against the prod-account RDS (db.t4g.micro, gp3, multi-AZ) with production's own data:
2,235 planners, 1,819 users, 66 MB dump.

| Step | Duration |
|---|---|
| Dump from the management primary, through the SSM tunnel | 148 s |
| Load into the prod primary | ~30 s |
| Flyway V046→V060 | 69 s |
| Data work, total | under 5 min |

`V058` alone is 36 s of the migration — it computes a content digest that `V060` drops again.

## 1. Park the front door

Replace the two A records with a single proxied record pointing at the maintenance page.
Traffic reaches the management fleet through the accelerator, so this is the step that
actually stops it, and it is also the deletion the load balancer needs in step 8.

Wait at least five seconds after the last request before touching anything else. The planner
view buffer flushes on a 500 ms timer, and a buffer dropped mid-flight loses view counts with
no error at all.

## 2. Freeze the management environment

Seoul first, then Oregon. On each control plane:

```bash
sudo k3s kubectl -n argocd patch application danteplanner-<region> --type merge \
  -p '{"spec":{"syncPolicy":null}}'
sudo k3s kubectl -n danteplanner patch ds/backend --type merge \
  -p '{"spec":{"template":{"spec":{"nodeSelector":{"role":"migrating"}}}}}'
sudo k3s kubectl -n danteplanner rollout status ds/backend --timeout=120s
```

The label value must begin and end with an alphanumeric character; the API server rejects
values like `__migrating__` outright.

### ⟦GATE⟧ Prove the pause held

```bash
sleep 30
sudo k3s kubectl -n danteplanner get ds/backend \
  -o jsonpath='{.spec.template.spec.nodeSelector.role}{"\n"}'   # migrating
sudo k3s kubectl -n danteplanner get pods -l app=backend --no-headers | wc -l   # 0
```

`app` here means GitOps is still reconciling and every later step races it. Re-check that the
Application's `syncPolicy` is empty before retrying.

## 3. Snapshot the management database

Writes are frozen, so this is RPO-zero for the window and it is the rollback that survives
even a stray deploy resurrecting the management fleet.

```bash
AWS_PROFILE=<management> aws rds create-db-snapshot --region us-west-2 \
  --db-instance-identifier danteplanner-mysql \
  --db-snapshot-identifier danteplanner-pre-account-cutover-$(date +%Y%m%d)
AWS_PROFILE=<management> aws rds wait db-snapshot-available --region us-west-2 \
  --db-snapshot-identifier danteplanner-pre-account-cutover-$(date +%Y%m%d)
```

A restore from it attaches the default parameter group unless told otherwise, which would
silently set `gtid_mode=OFF` and `session_track_gtids=OFF`. Pin
`--db-parameter-group-name danteplanner-mysql80` on any restore.

## 4. Assert parity, then dump

```bash
AWS_PROFILE=<management> scripts/ops/access/rds-query.sh "SHOW REPLICA STATUS\G" seoul \
  | grep -E 'Retrieved_Gtid_Set|Executed_Gtid_Set|Seconds_Behind'
```

Equal GTID sets mean nothing is in flight. Unequal means wait, not proceed.

```bash
AWS_PROFILE=<management> scripts/ops/access/rds-tunnel.sh start oregon

RO_USER=$(AWS_PROFILE=<management> aws secretsmanager get-secret-value --region us-west-2 \
  --secret-id danteplanner/rds/readonly-username --query SecretString --output text)
export MYSQL_PWD=$(AWS_PROFILE=<management> aws secretsmanager get-secret-value --region us-west-2 \
  --secret-id danteplanner/rds/readonly-password --query SecretString --output text)

docker run --rm --network host -e MYSQL_PWD mysql:8.0 \
  mysqldump -h 127.0.0.1 -P 3306 -u "$RO_USER" \
    --single-transaction --no-tablespaces --column-statistics=0 \
    --set-gtid-purged=OFF --routines --triggers --events \
    --default-character-set=utf8mb4 danteplanner > final.sql

unset MYSQL_PWD
```

`--routines` matters: the schema above V053 carries a stored procedure that a default
`mysqldump` leaves behind. `--set-gtid-purged=OFF` keeps the dump loadable, since the source
runs `gtid_mode=ON` and the emitted `SET @@GLOBAL.gtid_purged` would be rejected.

## 5. Freeze the prod fleet

Same three commands as step 2, same order, on the prod-account control planes. Same gate.

## 6. Reload the prod schema

The schema being dropped holds the rehearsal's copy of production data. Production itself is
intact in the management account and in the step 3 snapshot.

```bash
AWS_PROFILE=<prod> scripts/ops/access/rds-tunnel.sh start oregon

export MYSQL_PWD=$(AWS_PROFILE=<prod> aws secretsmanager get-secret-value --region us-west-2 \
  --secret-id danteplanner/rds/master-password --query SecretString --output text)

docker run --rm -i --network host -e MYSQL_PWD mysql:8.0 \
  mysql -h 127.0.0.1 -P 3306 -u admin \
  -e "DROP DATABASE danteplanner; CREATE DATABASE danteplanner CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

docker run --rm -i --network host -e MYSQL_PWD mysql:8.0 \
  mysql -h 127.0.0.1 -P 3306 -u admin --default-character-set=utf8mb4 danteplanner < final.sql

unset MYSQL_PWD
```

Application grants are scoped `ON danteplanner.*` and survive the database being dropped and
recreated, so the users do not need re-provisioning.

### ⟦GATE⟧ The dump landed at V045

```bash
AWS_PROFILE=<prod> scripts/ops/access/rds-query.sh "SELECT
  (SELECT MAX(CAST(version AS UNSIGNED)) FROM danteplanner.flyway_schema_history) AS ver,
  (SELECT COUNT(*) FROM danteplanner.planners) AS planners,
  (SELECT COUNT(*) FROM danteplanner.users) AS users;"
```

## 7. Release Oregon, then Seoul

Oregon first — it holds the primary and the durable auth Redis.

```bash
sudo k3s kubectl -n danteplanner patch ds/backend --type merge \
  -p '{"spec":{"template":{"spec":{"nodeSelector":{"role":"app"}}}}}'
sudo k3s kubectl -n danteplanner logs -f ds/backend \
  | grep -iE 'flyway|migrating to version|successfully applied'
```

Flyway takes an advisory lock on `flyway_schema_history`, so several pods starting at once is
harmless: one migrates and the rest wait. Thousands of duplicate-key warnings during `V051`
are expected — the filter backfill swallows duplicates by design.

### ⟦GATE⟧ The decomposition landed

```bash
AWS_PROFILE=<prod> scripts/ops/access/rds-query.sh "SELECT
  (SELECT MAX(CAST(version AS UNSIGNED)) FROM danteplanner.flyway_schema_history) AS ver,
  (SELECT COUNT(*) FROM danteplanner.planner) AS planner,
  (SELECT COUNT(*) FROM danteplanner.planner_content) AS content,
  (SELECT COUNT(*) FROM danteplanner.planner_stats) AS stats,
  (SELECT COUNT(*) FROM danteplanner.planner_catalog) AS catalog,
  (SELECT COUNT(*) FROM danteplanner.planner_entity_filter) AS entity_filter;"
```

`planner`, `planner_content` and `planner_stats` each carry one row per source planner;
`planner_catalog` holds only the visible ones. Compare against what the same dump produced in
rehearsal, not against absolute numbers.

Then Seoul, and restore GitOps on both:

```bash
sudo k3s kubectl -n danteplanner patch ds/backend --type merge \
  -p '{"spec":{"template":{"spec":{"nodeSelector":{"role":"app"}}}}}'
sudo k3s kubectl -n argocd patch application danteplanner-<region> --type merge \
  -p '{"spec":{"syncPolicy":{"automated":{"prune":true,"selfHeal":true},"syncOptions":["CreateNamespace=true"]}}}'
```

Clearing `syncPolicy` removed `syncOptions` with it, so both halves go back or the
Application is left with no automated sync at all and the next deploy silently does nothing.

Smoke both regions in-VPC before the edge moves: `/healthz-local` through each ingress
private address, and `/actuator/health` from inside a backend pod.

**Restore GitOps on the prod fleet only.** The management fleet stays paused.

## 8. Move the hostname

Delete the maintenance record from step 1, so the hostname is free, then:

```bash
terraform -chdir=terraform/cloudflare workspace select prod-fleet
AWS_PROFILE=<prod> terraform -chdir=terraform/cloudflare apply -var-file=prod.tfvars
```

Without `-target` this creates the load balancer, its monitor and both region pools on
`api.dante-planner.com`, pointing at the prod-account tunnels. The tunnels themselves already
exist from the staged apply and the fleet's cloudflared is already connected to them.

Confirm the pools resolve to the prod tunnel ids rather than the management ones.

## 9. Verify the live path

Through the public hostname, not in-VPC: create, stale-sync 409, one-request publish of a
never-synced draft, list card, identity and keyword search, vote, detail counters, unpublish
emptying the catalog. Watch 5xx rate, p99, RDS connections and JVM memory against limit.

## Rollback posture

| Stage | Rollback |
|---|---|
| Before step 6 | Restore the two A records, unfreeze the management fleet. Nothing was written. |
| Steps 6–7 | Same. The prod schema is disposable until the hostname moves. |
| After step 8 | Destroy the load balancer, restore the two A records, unfreeze the management fleet. Writes taken on prod since the cutover are lost, which is why the bake watches for errors rather than waiting them out. |

The management database is untouched throughout, and the step 3 snapshot survives even if the
instance itself is later disturbed.

## After the window

1. **Flip the GitHub secrets** `AWS_ACCOUNT_ID` and `AWS_PROVISIONER_ROLE_ARN` to the prod
   account. Nothing depends on this while CI is down, which is exactly why it is easy to
   forget: the first pipeline run after CI returns builds into, and deploys at, whichever
   account these name. They must not still name the management one by then.
2. **Open and merge the candidate PR to `main`,** so the branch finally carries what
   production runs. With CI down the merge deploys nothing. Once CI is back it would, and
   then item 1 becomes an ordering rule rather than housekeeping — merging with the secrets
   still pointed at the management account would send a deploy at the rollback environment,
   where ArgoCD restores the DaemonSet from git and the new image migrates the management
   database.

   If Pages still builds from `main`, this merge also republishes the SPA, which is the point
   at which the maintenance announcement should already have been replaced or removed.
3. **Leave the management fleet's GitOps paused for the whole bake.** Its Application is what
   would otherwise resurrect the drained DaemonSet. Restoring it "to tidy up" starts the
   backend against a database the rollback depends on being left alone.
4. **Leave the ArgoCD image override in place while CI is down, and treat it as the deploy
   mechanism.** It outranks the overlay, so with no pipeline bumping `newTag` in git it is the
   only thing that moves the running image: build and push by hand, then patch the
   Application and hard-refresh it. The day CI returns, this reverses — an override still
   pinned to an old tag means every deploy renders but never changes what runs, which fails
   silently.
5. **Bake at least seven days** before decommissioning anything in the management account.
