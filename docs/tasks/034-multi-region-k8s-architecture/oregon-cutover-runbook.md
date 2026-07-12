# Oregon Cutover Runbook — single EC2 → k3s fleet

Operational guide for cutting production from the hand-provisioned single backend EC2 to the
Phase-13 Oregon k3s fleet. Oregon-first: one region behind Cloudflare, smallest blast radius,
DNS/EIP rollback. Seoul (Phase 14) comes later and is out of scope here.

## The one fact that makes this safe

Task 030 already moved prod to **RDS**, and the Oregon fleet backend points at that **same RDS**
(`deploy/overlays/oregon/configmap-patch.yaml` `MYSQL_HOST` = `terraform/rds` `rds_endpoint`, reached
over VPC peering). So this is a **pure compute swap over a shared database** — the old EC2 and the
fleet are never inconsistent, there is no data to migrate, and rollback cannot diverge state. The
only levers are *which compute serves traffic* (entry-plane DNS/EIP) and *how many pods are up*.

```
        BEFORE                              AFTER
Cloudflare (api A-record)          Cloudflare (api A-record)
   │  orange-cloud proxy              │  orange-cloud proxy
   ▼                                  ▼
single EC2 :443 ───┐              Oregon ingress EIP :443 (Traefik)
                   │                  │  HTTPRoute → Spring DaemonSet (app ASG)
                   ▼                  ▼
              RDS (shared) ◄──────────┘  same RDS over VPC peering
```

---

## Part A — Operate the cluster (learn this before cutover)

You cannot cut over to a cluster you cannot inspect. Get `kubectl` + ArgoCD access first.

### A1. kubeconfig
The k3s server config lives on the CP node. Pull it and rewrite the API host to the CP private IP
(reachable from a bastion / SSM session inside the VPC):

```bash
CP_IP=$(terraform -chdir=terraform/oregon output -raw cp_private_ip)
# via SSM (no public SSH on the CP):
aws ssm start-session --target <cp-instance-id> --region us-west-2
sudo cat /etc/rancher/k3s/k3s.yaml   # copy locally, replace 127.0.0.1 with $CP_IP
kubectl get nodes -o wide            # expect: cp, ingress, data, >=1 app node — all Ready
```

### A2. Is the app actually serving?
```bash
kubectl -n danteplanner get pods -o wide          # Spring DaemonSet pods on role=app nodes, Running
kubectl -n danteplanner get httproute,gateway     # Traefik Gateway + app HTTPRoute + healthz-local
kubectl -n danteplanner rollout status ds/backend # DaemonSet ready
# app-only readiness (no dependency gating): should be 200 from an in-VPC host
curl -sk https://<ingress-private-ip>/healthz-local -o /dev/null -w '%{http_code}\n'
```

### A3. ArgoCD (GitOps — the cluster follows git, not kubectl)
```bash
kubectl -n argocd get applications           # root-app + children: Synced / Healthy
argocd app get danteplanner                  # or via the UI port-forward
```
`Synced` = live state matches the tracked branch. **Never hand-edit workloads with `kubectl edit`** —
ArgoCD reverts drift. Change the git overlay and let it sync. The image tag is bumped by CI
(arm64 build → ECR → kustomize `newTag`); a deploy is a commit, not a `kubectl set image`.

### A4. Data path (prove the fleet reaches RDS before you send it users)
```bash
# from an app pod:
kubectl -n danteplanner exec -it ds/backend -- sh -c 'nc -zv <rds-endpoint> 3306'
```
If this fails, the cluster SG is not in the RDS SG allowlist — fix `terraform/rds` inputs
(`cluster_security_group_id` output → RDS SG), not this stack.

---

## Part B — Migrate (single EC2 → fleet)

### Step 0 — Merge the code to main
Two commits on `feat/034-oregon-k3s` must reach `main`: the orphan-collection (`e806ce4d`) and the
Phase-14a auth-local split (the commit you just made).

```bash
git checkout feat/034-oregon-k3s
git log --oneline main..HEAD          # confirm exactly the intended commits, nothing stray
./gradlew -p backend test             # full suite green on the merge base (1070/1070)
git checkout main && git pull
git merge --no-ff feat/034-oregon-k3s
git push origin main
```
Wait for CI to build the arm64 image → push to ECR → bump the kustomize tag → ArgoCD sync. Confirm
`argocd app get danteplanner` shows the new revision **Synced/Healthy** *before* touching the entry
plane. The cluster must already be serving the merged code when you cut traffic to it.

> The working tree still holds unrelated drift (`.claude/*`, `scripts/ops/*`, `static`, portfolio
> docs). Do **not** sweep it into this merge — keep the branch history clean.

### Step 1 — Give the ingress a stable EIP (the "reassign the EIP" prerequisite)
The ingress publishes an **ephemeral** `public_ip` (`ingress_public_ip`); Cloudflare cannot durably
point at an IP that changes on stop/start or replacement. This is automated in **two layers** so the
address survives the fleet's destroy+apply rebuild proof:

- **Allocate (once, durable)** — `terraform/oregon-edge` holds the `aws_eip` in its own state, so a
  fleet rebuild never releases the address:
```bash
terraform -chdir=terraform/oregon-edge init
terraform -chdir=terraform/oregon-edge apply
ALLOC=$(terraform -chdir=terraform/oregon-edge output -raw ingress_eip_allocation_id)
INGRESS_EIP=$(terraform -chdir=terraform/oregon-edge output -raw ingress_eip_public_ip)
```
- **Associate (in the fleet, rebuild-safe)** — set `ingress_eip_allocation_id = "$ALLOC"` in
  `terraform/oregon/terraform.tfvars`, then apply the fleet. It creates only the
  `aws_eip_association`, re-bound on every rebuild to the SAME address:
```bash
terraform -chdir=terraform/oregon apply                       # review: 1 association, NO instance replacement
terraform -chdir=terraform/oregon output -raw ingress_eip     # == $INGRESS_EIP
```
Both applies are **consent-gated** — run with your AWS credentials, read the plan before yes. Why
two layers: an `aws_eip` inside the fleet would be released on `terraform destroy`, and reallocation
hands you a *different* IP — every rebuild would silently break Cloudflare. Splitting allocation
(durable) from association (disposable) keeps the entry IP stable **and** the rebuild unattended.

### Step 2 — Validate the cluster end-to-end BEFORE cutover (separate hostname)
Do not test by flipping prod. Point a throwaway record at the EIP and exercise the real path
(Cloudflare proxy + mTLS origin pull + Traefik + Spring + RDS):

- In Cloudflare, add `api-cluster.dante-planner.com` → `A` → `$INGRESS_EIP` (orange-cloud, same
  proxy/mTLS config as prod `api`). Lower the prod `api` record TTL to 60s now so the later swap
  propagates fast.
- Smoke the cluster through it. **The origin-TLS / Cloudflare Authenticated Origin Pulls (mTLS)
  handshake is the single most likely cutover failure** — if Phase-13 Traefik has no origin-pull
  client cert wired, every proxied request 5xxs the instant you cut prod. Make it an explicit gate:
```bash
BASE=https://api-cluster.dante-planner.com
curl -sv $BASE/actuator/health 2>&1 | grep -iE "SSL|TLS|cf-ray|200"  # mTLS handshake completes, cf-ray present, 200
# auth round-trip: login → access cookie → an authed read → refresh rotation → logout
# a write that hits RDS (create/delete a planner) then read it back
# SSE stream connects and receives an event
```
- **auth-local no-op check** (guards the 14a security wiring): log in, log out, immediately re-present
  the old access token → must be **rejected**. If it is accepted, or `blacklist_check_skipped_total`
  climbs, `REDIS_AUTHLOCAL_HOST` is misrouted (should equal `REDIS_AUTH_HOST` in single-region).
- Watch the fleet while you do it: `kubectl -n danteplanner logs -f ds/backend`, Grafana Cloud
  dashboards, RDS connections climbing on the fleet SG.

Only proceed when every check is green on `api-cluster`.

### Step 3 — Cut over
Two ways to move prod `api` onto the fleet. **Recommended: DNS repoint** (controllable, reversible in
one edit, both computes stay up). The **EIP-reassociation** variant is faster but abrupt and blind —
and only exists **if the old EC2 actually holds an EIP** (if prod is an ephemeral IP behind
Cloudflare, this column does not apply; use DNS repoint). Confirm first:
`aws ec2 describe-addresses --region us-west-2 --filters Name=instance-id,Values=<old-ec2-id>`.

| | DNS repoint (recommended) | EIP reassociation |
|---|---|---|
| Action | Cloudflare `api` A-record → `$INGRESS_EIP` | move the **old EC2's** existing EIP onto the ingress |
| Speed | TTL-bounded (60s if pre-lowered) | seconds (association propagation) |
| Old EC2 | stays up, still reachable by its own IP | loses its public IP immediately |
| Rollback | edit the record back | reassociate the EIP back to the old EC2 |
| Validation before flip | yes (`api-cluster`) | no — the IP serves one instance at a time |
| Matches "reassign the EIP" | via a dedicated ingress EIP | literally |

DNS repoint:
```
Cloudflare → DNS → api.dante-planner.com → A → $INGRESS_EIP   (keep orange-cloud + mTLS)
```
Keep the old EC2 **running** through the whole cutover — it is your rollback target.

### Step 4 — Confirm traffic is processed well
```bash
BASE=https://api.dante-planner.com
# 1. it's the fleet, not the old box:
curl -sI $BASE/actuator/health          # cf-ray present (Cloudflare), 200
kubectl -n danteplanner logs -f ds/backend | grep -i "GET\|POST"   # requests arriving at pods
# 2. functional: real login, authed read, a write→read on RDS, refresh rotation, logout-everywhere
# 3. auth-local no-op holds (single region): blacklist a token via logout, immediately re-present it
#    → rejected (auth-local == auth, zero lag). blacklist_check_skipped_total flat (no fail-open).
# 4. metrics/alerts quiet: 5xx rate, p99 latency, RDS connections/ReplicaLag, JVM mem vs limit
```
Watch for ~10–15 min. The old EC2 should be receiving **zero** new requests (its logs go quiet).

### Step 5 — Rollback on error
Trigger: sustained 5xx, health flapping, pods CrashLooping, RDS unreachable from the fleet, latency
blowout. Because RDS is shared, rollback is pure entry-plane — **no data repair**:

```
# DNS-repoint cutover:
Cloudflare → api.dante-planner.com → A → <old EC2 IP>     # revert the one record
# EIP-reassociation cutover:
aws ec2 associate-address --instance-id <old-ec2-id> --allocation-id <eip-alloc> --region us-west-2
```
Then confirm the old EC2 is serving again (`curl -sI $BASE/actuator/health`, its logs resume). The
fleet keeps running for post-mortem — do not tear it down to roll back; just stop sending it traffic.

Fleet-internal issues (not entry-plane) that also warrant rollback, and their in-cluster remedy if
you'd rather fix forward:
- bad image → ArgoCD rollback to the previous synced revision (`argocd app rollback danteplanner`).
- app node wedged → the ASG replaces it (~14s to Ready, proven Phase 13); or `kubectl delete pod`.

### Step 6 — Decommission gate (do NOT rush)
Keep the old EC2 **stopped-not-terminated** for ≥ 3–7 days of clean fleet operation (mirror the
`docs/tasks/030-rds-migration/runbook.md` Zone-4 discipline). Only after a quiet window: terminate
the EC2, remove `api-cluster.dante-planner.com`, and delete the old EIP if it was distinct.

---

## Quick reference

| Need | Command |
|---|---|
| Cluster health | `kubectl get nodes -o wide` · `kubectl -n danteplanner get pods` |
| GitOps state | `argocd app get danteplanner` (Synced/Healthy) |
| Ingress EIP | `terraform -chdir=terraform/oregon output -raw ingress_eip` |
| Cutover | Cloudflare `api` A-record → ingress EIP |
| Rollback | Cloudflare `api` A-record → old EC2 IP |
| Rollback (in-cluster) | `argocd app rollback danteplanner` |
| Prove shared RDS | app-pod `nc -zv <rds-endpoint> 3306` |
