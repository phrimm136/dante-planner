# Phase 14 notes — Seoul + Global Accelerator + peering + ECR replication

Forward-looking notes for the second region, written right after Phase 13 (Oregon) reached
operational green + a proven unattended rebuild (2026-07-13). These capture what Phase 13
*learned* that changes how Phase 14 must be built. Read alongside `plan.md` (Phase 14 section) and
`requirements.md`.

## What Phase 13 proved (the reusable substrate)

The Oregon stack is now a **reproducible, region-shaped unit**: `terraform/oregon` +
`deploy/base` + `deploy/overlays/oregon` + the CP/agent user-data bootstrap. A `destroy` + `apply`
re-converges hands-free. Seoul should be built by **generalizing this unit to a second region**,
not by hand-authoring a parallel stack. Before writing Seoul, factor the region-specific bits of
`terraform/oregon` into variables (region, CIDR, name suffix, CP private IP, RDS/redis endpoints)
so `terraform/seoul` (or a reused module) differs only by tfvars.

## The hard blockers Phase 14 must solve (not present in a single region)

### 1. Cross-region VPC peering CANNOT `auto_accept`
Oregon↔RDS peering used `auto_accept = true`, which works only **same-account + same-region**.
Seoul↔Oregon (and Seoul↔RDS if Seoul reaches the primary) is **cross-region** → you must split the
peering into a **requester** (`aws_vpc_peering_connection`, `auto_accept = false`) in one region's
provider and an explicit **`aws_vpc_peering_connection_accepter`** in the other region's provider
(a second `provider "aws"` aliased to `ap-northeast-2`). Routes still required on *both* sides;
CIDRs still must not overlap (Oregon `10.20.0.0/16`, RDS `172.31.0.0/16` — pick Seoul e.g.
`10.30.0.0/16`). Apply the inline-vs-standalone-route lesson: all routes as standalone `aws_route`.

### 2. Secrets must be replicated, not re-created
The whole reason config moved to **Secrets Manager** (bundle `danteplanner/backend/runtime-config`
+ `danteplanner/jwt/*`) was cross-region replication. In Phase 14: enable Secrets Manager
**multi-region replication** of those secrets to `ap-northeast-2` (the `requirements.md` line
"RS256 private key via multi-region secret replication"). Seoul's ESO reads the **replicated**
secret locally — do NOT hand-copy. Confirm Seoul's app-node IAM grants `secretsmanager:GetSecretValue`
on the replica ARN (region-specific ARN). Infra secrets stay region-local in Parameter Store:
Seoul mints its **own** k3s join token (`/danteplanner/seoul/k3s-join-token`), not Oregon's.

### 3. A real backend change: the auth-Redis read/write split (flagged Phase 13, turn 1)
`RedisConnectionConfig` today has three endpoints: `auth` (@Primary), `rateLimit`, `sseLocal`.
Blacklist checks and tombstone checks currently read through `auth` (the write connection). In
Seoul, `auth` must be the **Oregon primary** for writes, but blacklist/tombstone **reads** should
hit the **local Seoul replica** (read-local/write-global). SSE already splits (publish→primary via
`auth`, subscribe→`sseLocal`). So Seoul needs a **fourth endpoint** (`auth-local`, mirroring
`sseLocal`) that `TokenBlacklistService.isBlacklisted()` / `ContentTombstoneStore.exists()` read
from, while their writes stay on `auth`. This is a **backend code change**, not just an overlay —
scope it as a real sub-task, with the causal-consistency tests. Without it, every Seoul auth
request pays ~130ms cross-region to Oregon (defeats read-local), OR writes hit a read-only replica
and fail.

### 4. ECR cross-region replication
Seoul pulls the backend image locally. Enable ECR **cross-region replication** to `ap-northeast-2`
so Seoul self-heals without Oregon's registry (`requirements.md`: "ECR replicated so Seoul
self-heals without Oregon"). Note Phase 13 made ECR a **data source** (shared repo, read-only) —
Seoul reads the replicated repo the same way; do not author it as an owned resource.

### 5. Data layer: cross-region RDS read replica + Redis REPLICAOF
Seoul RDS = a single-AZ **cross-region read replica** with a region-local parameter group carrying
the primary's hardened posture (`gtid_mode=ON`, `enforce_gtid_consistency=ON`,
`require_secure_transport=1`) so the someday-flip can promote it. Redis in Seoul = read-only
`REPLICAOF` the Oregon primary over peering. Both consume the peering from #1.

## Reuse-these-fixes checklist (Phase 13 fixes that must exist in Seoul too)

Each of these was a Phase-13 fix; the Seoul cluster's user-data / overlay / IAM must carry the same
(ideally via the shared module so it's automatic):

- Kubelet **ECR credential provider** on app nodes (private-ECR pulls; self-managed k3s has no CCM
  wiring) — `agent.sh.tftpl`.
- **`enableServiceLinks: false`** on the Spring DaemonSet (Redis service-link env collision) — base.
- **default `AppProject`** applied before the root Application (ArgoCD core ships none).
- ArgoCD Application **`targetRevision` wired to the clone branch var** (not hardcoded).
- **`ARGOCD_GIT_MODULES_ENABLED=false`** on the repo-server (private `static` submodule).
- `deploy/overlays/seoul` with the region-local values + the auth-local Redis endpoint (#3).
- `gitops_target_revision` / branch strategy consistent with Oregon.

## Entry plane (the two-region routing, new in Phase 14)

- **Global Accelerator**: proximity routing, ~30s regional failover, endpoints = the ingress EC2s
  directly (client IP preserved). Health check probes `/healthz-local` (through Traefik to
  local-Spring app-only readiness; the fallback route is excluded so a region serving via fallback
  looks unhealthy and GA routes clients direct).
- **Traefik cross-region `failover` service**: local Spring main, other region's Spring fallback,
  triggered by **connection failure ONLY** (never latency/5xx — cascade prevention). Alert when
  sustained fallback > 0.
- **Cloudflare** single entry with **mTLS Authenticated Origin Pulls** as the real gate; SG
  allowlist = Cloudflare + GA-health ranges only.

## Loose ends carried from Phase 13

- **Orphan `NotReady` node GC**: self-managed k3s has no cloud-controller-manager, so terminated
  app-node Node objects linger. Decide before Seoul doubles the node churn: add the AWS CCM
  (proper; also gives topology labels) or an ASG termination lifecycle hook that `kubectl delete
  node`s. Currently manual (`kubectl delete node`).
- **`root-app.yaml` repoURL / OIDC sub** already point at `phrimm136/dante-planner` (the real
  remote) — keep Seoul's consistent.
- **Gate 3** (ReplicaLag p99 baseline) closes with Seoul per `plan.md` Phase 12/14.
- Region bootstrap must stay **unattended** — provisioning Seoul through the automation IS the
  test; do not accept any manual step that Oregon's rebuild proof didn't need.

## Suggested Phase 14 build order

1. Factor `terraform/oregon` region-specifics into a reusable module / variables (no behavior change; re-verify Oregon rebuild).
2. Backend: add the `auth-local` Redis endpoint + route blacklist/tombstone reads to it (#3), with tests.
3. Secrets Manager multi-region replication + ECR replication (#2, #4).
4. `terraform/seoul` (module reuse) + `deploy/overlays/seoul` + cross-region peering accepter (#1).
5. RDS cross-region read replica + Redis REPLICAOF (#5).
6. Global Accelerator + Traefik failover + Cloudflare (entry plane).
7. Drills: region-kill (stopwatch GA failover ~30s), RDS promote rehearsal, Redis outage — write-ups to `docs/portfolio/`.
