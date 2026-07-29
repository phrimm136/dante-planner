# Fleet rebuild + schema-decomposition runbook (one stop-the-world window)

Operational guide for the maintenance window that lands the **planner god-table decomposition**
(`V049`→`V052`) on the multi-region k3s fleet. Three operations share the window and must run **in
order**, with a hard verification gate between them:

1. **Pod reposition** — a `terraform apply` per region that **replaces** the CP, data, and ingress
   instances (a new AMI forces replacement) and rebuilds them from the updated `user-data`, baking
   in swap, `GOMEMLIMIT`, and the ArgoCD-on-data placement as first-boot behavior instead of
   hand-applied live patches.
2. **Schema migration** — the stop-the-world decomposition of `planners` into the write aggregate +
   read projections, run against the shared RDS.
3. **Edge cutover** — replace Global Accelerator with Cloudflare Tunnel + Load Balancer as the front
   door. It rides this window because the schema migration already requires every cluster down, and
   the cutover wants the same quiet period.

Fleet mechanics reused here (kubeconfig fetch, ArgoCD, ingress EIP, RDS reachability) are covered in
`oregon-cutover.md` — read Part A there first if you
cannot yet inspect the cluster.

## The one ordering rule that makes this safe

> **Rebuild the fleet first, verify it, *then* migrate the schema — never interleave.**

Rebuilding from scratch exercises the entire deploy pipeline (AMI → bootstrap → ArgoCD converge →
app ASG refresh → traffic) on brand-new instances. Passing the gate proves the path to *ship a fix*
already works. Only then do you run the migration, whose own rollback (RDS snapshot) is independent.
The parts never need rolling back together — that separation is the payoff of sequencing them with
gates. Infra rollback is AMI-pinned / git-reconverged; DB rollback is a snapshot restore; edge
rollback is re-attaching the accelerator, which stands untouched until the post-bake teardown. Keep
them disjoint.

The edge cutover is last for the same reason: it is the only part whose rollback is a single
DNS-level action, so it is the cheapest thing to be holding when something else goes wrong.

```
        WINDOW TIMELINE (per region: Oregon fully, then Seoul)

  prep ─────────► Part A: rebuild ──► ⟦GATE⟧ ──► Part B: migrate ──► Part C: attach ──► bake ──► teardown
  tunnels live on  CP+data+ingress   fleet green   detach edge,       new front door    ≥1 week   GA + SG
  a TEST hostname, replaced          before DB     drain, V049→V052,  instead of the              rules
  production                                       Oregon back first  accelerator                 removed
  untouched

  The edge cutover costs no extra downtime: traffic is already detached for the migration,
  so "attach the new front door" replaces "re-attach the old one".
```

---

## Pre-window prep (do any time before the window)

1. **Get the code onto `main`.** The applies read your local checkout, but the ArgoCD root apps
   track `main` — the rebuilt cluster must converge from a `main` that contains current reality
   (including the migrated backend image and the `deploy/` manifests). Push `dev`, merge to `main`,
   wait for CI to build the arm64 image → ECR → kustomize tag bump.
2. **Safety snapshots.**
   - Per CP: a manual k3s etcd snapshot over SSM (they also ship to S3 on schedule).
   - RDS: a manual snapshot **immediately before** Part B (see that step) — the migration's true
     rollback point.
3. **Stand the new edge up on a test hostname — well before the window.** This is the single most
   valuable thing to do early: the tunnel path gets proven while the old front door is still
   serving, so the window itself contains no first-time-ever operations.

   The terraform exists at `terraform/cloudflare` (tunnels, load balancer, pools, through-tunnel
   monitor; see its README). Two pieces are **not** written yet and are the remaining authoring
   work: the **cloudflared Deployment per region** under `deploy/` (2 replicas, no `hostNetwork`,
   `--no-autoupdate`, mounting the origin CA at the path `origin_ca_pool_path` names), and the
   **Secrets Manager entries + `ExternalSecret`** carrying each tunnel token. A hand-authored
   Kubernetes Secret is not how secrets reach this cluster.

   a. **Clear the subscription gate first.** Load Balancing (~$5/mo) plus the Traffic Steering
      add-on (~$10/mo expected) on the zone. Two stop conditions, and neither is a judgement call:
      steering requiring a **Pro zone** inverts the cost case, and a **monitor floor above 60s**
      invalidates the failover window this plan assumes. Record the real numbers; the terraform's
      `monitor_interval_seconds` default is an assumption until you do.

   b. **Apply against a throwaway hostname.**

      ```bash
      cd terraform/cloudflare && cp terraform.tfvars.example terraform.tfvars   # token, account, zone
      # set api_hostname = "edge-test.dante-planner.com"
      terraform init && terraform plan          # creates only
      terraform apply
      ```

   c. **Verify the path end to end on that hostname** while production still runs on the
      accelerator: each tunnel reporting **≥4 edge connections** (one connection is a single edge
      location away from an outage), the monitor marking both pools healthy, a real request
      arriving at the intended region, and an SSE stream surviving the hop. Long-lived streams
      through a new proxy layer are where buffering surprises live, and you want that surprise now
      rather than at 03:00.

   Leave it there. A load balancer on a test hostname carries no production traffic, and moving
   `api_hostname` to the real hostname later is the entire cutover.

4. **Do not delete these when the accelerator goes.** The tunnel's origin *is* the ingress node's
   Traefik listener: removing the `origin-tls` secret, the `role=ingress` instances, or Traefik
   itself takes the new front door down with the old one. Traefik's TLSOption must stay
   `VerifyClientCertIfGiven` — hardening it to require a client certificate bricks the tunnel,
   which presents none.

5. **Apply the RDS parameter that makes read-your-writes precise.** `session_track_gtids = OWN_GTID`
   is declared in `terraform/rds/main.tf`'s `aws_db_parameter_group`; apply that stack. Do **not**
   set it by hand in the console — `aws_db_parameter_group` reconciles the whole group, so the next
   `terraform apply` would revert an out-of-band value. It is a dynamic parameter, so no reboot is
   needed, but confirm it actually took:

   ```bash
   scripts/ops/access/rds-query.sh "SELECT @@session_track_gtids"   # expect OWN_GTID
   ```

   It seeds a SESSION variable at connect time, so pooled connections keep the old value until they
   rotate. Inside this window every pod restarts anyway; outside it, a `rollout restart` is what
   makes the change observable.

   Until this is set, the application silently falls back to reading the primary's entire
   `@@gtid_executed` for every write. That is *correct* but maximally wide, so nearly every
   subsequent read pins to the primary and pays the cross-region round trip. The application side
   of the pair (`trackSessionState=true` on the JDBC url) ships in the image; both are required
   before any of it does anything.

6. **Add the rotation flag to both region ConfigMaps.** `JWT_ROTATION_LINEAGE_ENABLED` is now read
   from `backend-config` (`deploy/overlays/{oregon,seoul}/configmap-patch.yaml`). It used to be
   flippable at runtime through an internal endpoint that no longer exists, so a region whose
   ConfigMap lacks the key falls to the application default instead of whatever was last toggled.

7. **Decide the auth-Redis question.** The rebuild wipes the in-cluster token blacklist, so
   revocations issued before the window resurrect until natural token expiry. If any specific
   revocation matters, force those users' re-login, or consciously accept the gap. (`auth-local`
   Redis is ephemeral by design; nothing here is a data-loss event, only a revocation-window one.)

---

## Part A — Pod reposition (fleet rebuild), per region

Do **Oregon fully** (through its gate) before starting **Seoul**. Substitute the region's terraform
dir (`terraform/oregon`, `terraform/seoul`) and AWS region (`us-west-2`, `ap-northeast-2`) throughout.

### A1. Apply — replace the three stateful instances

```bash
terraform -chdir=terraform/oregon plan     # READ IT: expect 3 replacements (cp, data, ingress)
                                           # + launch-template re-render + SSM-document updates.
                                           # Confirm nothing outside that set is touched.
terraform -chdir=terraform/oregon apply
```

All three instances go down together — **the region goes dark**, which is fine; the window exists
for exactly that. Traefik on the (replacing) ingress serves 503 on empty endpoints meanwhile.

### A2. Wait for bootstrap convergence (~5–10 min)

The new CP runs the updated `cp.sh.tftpl`, so swap (`/swapfile`, `vm.swappiness=10`),
`GOMEMLIMIT=800MiB`, and the ArgoCD-on-data placement are now **first-boot** behavior. The new data
and ingress instances join under the CP's **new private IP**. That IP change cascades:

- the app ASG launch template is re-rendered with the new CP IP, so
- the published kubeconfig is re-published by the new CP's bootstrap (SSM param self-activates on CP
  replacement — see the oregon runbook's activation caveat).

### A3. Refresh the app ASG (the existing app nodes still point at the dead CP IP)

```bash
aws autoscaling start-instance-refresh --region us-west-2 \
  --auto-scaling-group-name danteplanner-oregon-app
```

(The name is `${name_prefix}-${region_name_suffix}-app` from `terraform/modules/fleet/app-asg.tf`.)

### ⟦GATE⟧ A4. Verify the fleet BEFORE touching the DB

This gate is the reason Part A precedes Part B. Do not proceed until **all** hold:

```bash
export KUBECONFIG=~/.kube/dante-oregon          # re-fetch: scripts/ops/oregon-verify.sh --kubeconfig
kubectl get nodes -o wide                        # cp, data, ingress, >=1 app — all Ready
kubectl -n argocd get applications               # root app Synced / Healthy
kubectl -n argocd get pods -o wide               # argocd + metrics-server land on the *-data node
kubectl -n danteplanner rollout status ds/backend
```

- **swap + `GOMEMLIMIT` are live from first boot** — SSM into the CP/app node and confirm
  `swapon --show` is non-empty and `systemctl show k3s -p Environment | grep GOMEMLIMIT`. First-boot
  presence (not a live patch) is the proof the `user-data` edits are correct.
- **Backend still serving on the old image** (pre-migration) from an in-VPC host:
  `curl -sk https://<ingress-private-ip>/healthz-local -o /dev/null -w '%{http_code}\n'` → 200.
- **Edge reachability.** The ingress instance's public IP changed, so the *old* front door needs
  re-pointing before Part B: `terraform -chdir=terraform/global-accelerator apply` so the endpoint
  group tracks the new instance. The tunnel does not care — it dials out from inside the cluster and
  has no origin IP to update, which is one of the reasons it replaces this step permanently. Confirm
  public reachability before Part B.

Only when this gate is green do you run the migration. If a region fails the gate, stop — do not
migrate a fleet you cannot ship a fix to.

---

## Part B — Schema migration (task 043 stop-the-world)

Runs once, against the **shared RDS**, after both regions (or at least the region you migrate from)
pass their gate. The backend runs Flyway on boot; `V052` asserts parity with `SIGNAL SQLSTATE
'45000'` **before** it drops `planners`, so a failed migration aborts with the old table intact.

### B1. Detach the edge, drain, and freeze writes

**Detach the front door first.** Park the accelerator's DNS record (or drop its endpoint
association) so no client reaches a fleet that is about to serve 503. Do not destroy anything —
the accelerator is the rollback until the post-bake teardown.

Then drain, in this order. None of these steps is padding:

1. **Wait ≥5s after the last request** before scaling anything down. The planner view buffer flushes
   on a 500ms timer; a buffer dropped mid-flight loses view counts with no error at all — the
   numbers are simply wrong afterwards.
2. **Freeze Seoul before Oregon.** Seoul writes cross-region into Oregon's primary, so stopping the
   writer before its target leaves no half-applied cross-region work.
3. **Assert parity before touching the schema:**

   ```bash
   # RDS: the replica has applied everything the primary has
   scripts/ops/access/rds-query.sh "SHOW REPLICA STATUS\G" | grep -E 'Retrieved_Gtid_Set|Executed_Gtid_Set'
   # Redis auth: compare master_repl_offset on the primary with slave_repl_offset on the replica
   ```

   Equal GTID sets and equal offsets mean nothing is in flight. Unequal means wait — not proceed.

Then freeze writes, per region in that order:

```bash
# Pause GitOps so ArgoCD self-heal does not fight the manual scale-down. Pause the ROOT app:
# the root app-of-apps manages the child Application, so a syncPolicy cleared on the child is
# itself a drift the root reverts.
#
# Patch the Application CR, not `argocd app set`. This fleet runs ArgoCD in CORE mode
# (cp.sh.tftpl applies core-install.yaml) — there is no API server for the CLI to reach, and no
# argocd binary is installed on any node. Root app is danteplanner-oregon / danteplanner-seoul.
kubectl -n argocd patch application danteplanner-<region> --type merge \
  -p '{"spec":{"syncPolicy":null}}'
# Drain the write path — scale the backend DaemonSet to zero schedulable pods
# (patch its nodeSelector to an unschedulable label, or cordon the app nodes):
kubectl -n danteplanner patch ds/backend --type merge \
  -p '{"spec":{"template":{"spec":{"nodeSelector":{"role":"__migrating__"}}}}}'
kubectl -n danteplanner rollout status ds/backend --timeout=120s   # 0 pods
# GATE: prove the pause held before trusting the drain. If the nodeSelector reverts, ArgoCD is
# still syncing and every later step races it.
sleep 30 && kubectl -n danteplanner get ds/backend \
  -o jsonpath='{.spec.template.spec.nodeSelector.role}'          # expect __migrating__
```

Traefik now serves 503 (empty endpoints) — this is the visible stop-the-world window.

### B2. RDS snapshot (the rollback point)

Writes are already frozen (B1), so this snapshot is RPO-zero for the window.

```bash
aws rds create-db-snapshot --region us-west-2 \
  --db-instance-identifier <rds-id> --db-snapshot-identifier danteplanner-pre-v052-$(date +%s)
# wait for status "available" before proceeding
```

**Restoring it is a window-only option.** V052 is forward-only, so a restore is the only route back
to a working schema — but it costs every write made since the snapshot, which is nothing while the
edge is detached and hours once traffic resumes. After reopening, fix forward.

A restore creates a **new instance with a new endpoint**, and it attaches the *default* parameter
group unless told otherwise — which would silently set `gtid_mode=OFF` (breaking the Seoul replica),
`session_track_gtids=OFF` (breaking read-your-writes) and `require_secure_transport=0`. Pin all three
attachments:

```bash
aws rds restore-db-instance-from-db-snapshot --region us-west-2 \
  --db-instance-identifier <new-rds-id> \
  --db-snapshot-identifier danteplanner-pre-v052-<ts> \
  --db-parameter-group-name danteplanner-mysql80 \
  --db-subnet-group-name danteplanner-rds \
  --vpc-security-group-ids <rds-sg-id> \
  --multi-az
# Then repoint MYSQL_HOST in both overlays and restart the app pods. session_track_gtids seeds a
# SESSION variable at connect time, so pooled connections opened against the old instance keep the
# old value; without a restart the capture silently runs on the wide @@gtid_executed fallback.
# gtid.capture{source=fallback} is the check.
```

### B3. Run the migration

Bump the backend image to the migrated build (already on `main`/ECR from prep) and let one pod boot.
Flyway takes its advisory lock on `flyway_schema_history`, so even if several pods start, exactly one
runs `V049`→`V052` while the rest wait, then start clean:

```bash
# restore the real nodeSelector so pods schedule again; the first up runs Flyway:
kubectl -n danteplanner patch ds/backend --type merge \
  -p '{"spec":{"template":{"spec":{"nodeSelector":{"role":"app"}}}}}'
kubectl -n danteplanner logs -f ds/backend | grep -i 'flyway\|migrat\|V05'
```

- `V049` create (information_schema-guarded DDL) → `V050` backfill (CSV→JSON with rename REPLACEs) →
  `V051` filter backfill (`JSON_TABLE`) → `V052` FK-repoint procedure + parity assertions → `DROP
  planners`.
- **If `V052` SIGNALs a parity failure**, the pod dies before any drop; `planners` is intact. Do not
  retry blindly — investigate the count/sum mismatch, or restore the B2 snapshot.

### B4. Bring the regions back — Oregon first — and verify the chain

Oregon holds the RDS primary and the durable auth Redis. Starting Seoul first hands you a region
whose writes *and* token checks both cross a WAN to something that is not up yet, which reads as a
Seoul fault and is not one.

```bash
# Oregon first: restore the nodeSelector (B3), wait Ready, smoke it in-VPC
kubectl -n danteplanner rollout status ds/backend
curl -sk https://<oregon-ingress-private-ip>/healthz-local -o /dev/null -w '%{http_code}\n'   # 200
# only then Seoul. Restore the policy the root app declares in deploy/argocd/root-app-<region>.yaml
# — clearing it left no automated sync at all, so nothing reconciles until this lands.
kubectl -n argocd patch application danteplanner-<region> --type merge \
  -p '{"spec":{"syncPolicy":{"automated":{"prune":true,"selfHeal":true}}}}'
```

Drive the live path against the migrated fleet (the `/verify` acceptance drive):
create → stale-sync 409 → one-request publish of a never-synced draft → trimmed list card →
identity / renamed-keyword / ngram-substring search hits → vote → stats-served detail counters →
bodyless unpublish empties the catalog. Watch 5xx rate, p99, RDS connections, JVM mem vs limit.

---

## Part C — Attach the new front door (the cutover)

The fleet is migrated and back up, but nothing is pointing at it yet: the edge was detached in B1
and never re-attached. So the cutover is not an extra operation — it is choosing *which* front door
to re-attach, and the new one has already been proven on the test hostname during prep.

### C1. Move the load balancer to the real hostname

```bash
cd terraform/cloudflare
# api_hostname: edge-test.dante-planner.com -> api.dante-planner.com
terraform plan     # read it: the load balancer is renamed, nothing is destroyed
terraform apply
```

Confirm before declaring the window closed:

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://api.dante-planner.com/actuator/health   # 200
```

- Each tunnel still reports **≥4 edge connections**.
- The record is **proxied**. A DNS-only record resolves past the edge and never enters the tunnel,
  which fails in a way that looks like an origin problem.
- **Never health-check through the proxied public hostname** from a script or monitor: bot
  protection answers 521/403 and you will chase an outage that is not happening. The load
  balancer's own monitors are internal probes and are exempt.

### ⟦GATE⟧ C2. Prove the properties the accelerator used to provide

The old front door did geo-proximity and region failover. Do not assume the new one does until each
is observed:

- **Geo steering lands where it should.** From a JP vantage and a US vantage, hit the API and watch
  the per-region request-rate split on the fleet dashboard. Both vantages landing in one region
  means the steering rules are not doing what the plan says.
- **Region loss degrades rather than fails.** Kill one region's `cloudflared`, confirm the survivor
  serves 200s, restore it. Better learned now than during an incident.
- **A login survives the split.** The OAuth callback is a mutating GET; run a real sign-in and
  confirm the immediate follow-up request does not flash logged-out.
- **SSE survives the tunnel** in production shape, not just on the test hostname.

### C3. Confirm the write-path behaviour that ships in this image

None of this is visible until exercised, and all of it fails silently:

- **The read-your-writes cookie is minted on a write and not on a logout.** After the RDS parameter
  from prep is live, a write's cookie should decode to a **short** GTID set. A value covering the
  primary's entire history means the capture is still falling back — correct, but it pins nearly
  every following read to the primary and pays the cross-region round trip each time.
- **`/api/internal/*` answers 404.** The endpoints are gone, and so are the authentication bypass
  and CSRF exemption that used to sit on that path prefix.
- **A `static` submodule bump on `main` triggers a deploy.** The trigger used to match only paths
  *beneath* that directory, which a pointer bump never produces — so game-data updates silently
  stopped shipping.
- **`planner.legacy_toggle{operation=publish|bookmark}` is trending down.** It counts requests still
  arriving in the pre-idempotent shape from cached frontend bundles. The deprecated handlers stay
  until it reads ~0; removing them earlier gives long-lived tabs a dead button.

---

## Rollback posture

| Failure | Rollback | Notes |
|---|---|---|
| Part A bootstrap / gate | AMI-pinned re-apply, or restore etcd snapshot onto a rebuilt CP | Everything reconverges from git, so snapshot restore is the fallback, not the plan |
| Part B migration | Restore the B2 RDS snapshot; redeploy the pre-migration image | `V052` parity abort usually leaves `planners` intact — prefer fix-forward over restore |
| Part C edge | Re-point the record at the accelerator, which is still standing until the post-bake teardown | This is the reason the teardown waits: keeping the old front door alive costs a little money and buys a one-command rollback |

The rollbacks are **independent** by construction — the gates between the parts are what guarantee
you never have to unwind two at once.

One thing is *not* rollable and is accepted going in: the rebuild wipes the auth Redis data node.
Rotation families self-bootstrap when absent, so this causes **no mass re-login** — what is lost is
revocations, blacklist entries, and tombstones written before the window. That is a
revocation-window gap, not data loss, and it is the same gap the prep step asks you to decide about.

---

## Post-bake teardown (after ≥1 week on the new edge, not during the window)

Only once the new front door has carried real traffic for a bake period. Removing these earlier
throws away the Part C rollback.

```bash
terraform -chdir=terraform/global-accelerator destroy     # the accelerator itself
```

Then, in the region roots, remove what only existed to serve it:

- the **ingress security-group CIDR rules** that admitted the accelerator's ranges
- the **Route 53 health-check records** tied to the old front door
- the `scripts/ops/update-cloudflare-ips.sh` **cron** and `scripts/ops/ga-preflight.sh`
- the **CloudflareIpSilence alarm** (`scripts/ops/lib/alarms.sh`), which watched a list that no
  longer feeds anything

Keep the `role=ingress` instances, Traefik, and `origin-tls`. They are the tunnel's origin now.

**Re-verify the ingress surface afterwards.** The point of the teardown is that the fleet stops
accepting public inbound at all; the tunnel dials out. Confirm from outside the VPC that no port
answers, and confirm in the plan that the only inbound rules left are the cluster's own and the
cross-region Redis rule the auth replica needs:

```bash
terraform -chdir=terraform/oregon plan | grep -A3 'ingress'    # same for seoul
# then an external port scan of the former ingress IPs — expect nothing open
```

---

## Post-window hardening

Pin the AMI so drift becomes a **deliberate** upgrade, not a surprise replacement hiding inside an
unrelated plan (today's near-miss was avoided only with `-target`):

```hcl
# in the instance resources that were replaced this window:
lifecycle { ignore_changes = [ami] }   # or pin the SSM parameter version feeding the AMI
```

**Pinning the AMI turns off the only thing that currently delivers user-data changes.** The fleet
module sets no `user_data_replace_on_change`, so the AWS provider default (`false`) applies: a
user-data-only diff is an in-place stop/start and cloud-init never re-runs. Today a drifting AMI
forces a replacement and the new user-data lands as a side effect. Pin the AMI and a `cp.sh.tftpl`
edit will plan clean, apply clean, and reach nothing.

Pin the AMI **and** set `user_data_replace_on_change = true` on the pets, or the next control-plane
bootstrap fix ships to an instance that never executes it.

---

## Quick reference

| Need | Command |
|---|---|
| Rebuild a region | `terraform -chdir=terraform/oregon apply` (expect 3 replacements) |
| Refresh app nodes | `aws autoscaling start-instance-refresh --region us-west-2 --auto-scaling-group-name danteplanner-oregon-app` |
| Fleet gate | nodes Ready · ArgoCD Synced/Healthy · argocd+metrics-server on `*-data` · swap+GOMEMLIMIT first-boot |
| Freeze writes | scale `ds/backend` to 0 (nodeSelector) + `argocd app set … --sync-policy none` |
| DB rollback point | `aws rds create-db-snapshot …` before `V052` |
| Migration order | `V049` → `V050` → `V051` → `V052` (parity SIGNAL before `DROP planners`) |
| Verify | create → 409 → publish → search → counters → unpublish |
| Edge, before the window | apply `terraform/cloudflare` with `api_hostname` = a test hostname; verify; leave it |
| Edge cutover | move `api_hostname` to the real hostname and apply — that is the whole cutover |
| Tunnel health | ≥4 edge connections per tunnel, always |
| Quiesce order | detach edge → drain ≥5s → freeze Seoul → freeze Oregon → assert GTID + Redis offset parity |
| Bring-back order | Oregon (RDS primary + auth Redis) → smoke → Seoul → attach the new front door |
| RYW parameter | `SELECT @@session_track_gtids` → `OWN_GTID`, else cookies stay maximally wide |
| Retire deprecated toggles | when `planner.legacy_toggle` reads ~0 |
| Teardown gate | ≥1 week bake; keep ingress nodes, Traefik, `origin-tls` |
