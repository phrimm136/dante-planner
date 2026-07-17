# Task: Replace Global Accelerator with Cloudflare Tunnel + Load Balancer

Archives the `/brainstorm` debate of 2026-07-17 (entry-plane replacement: AWS Global Accelerator
→ per-region Cloudflare Tunnels behind one Cloudflare Load Balancer, then dissolution of the
origin-side ingress plane). Implementation-grade mechanics live in `mechanics.md` — read it
before building the entry plane, the LB/tunnel contract, the cutover, or the teardown.
N/A declarations: the `docs/spec.md` Data-Driven Features sections (Data Model Catalog,
Normalization Layer, Rendering Modes, Reference Per Mode, Implementation Order) do not apply —
this task consumes no game data. The `local-tdd` execution class is intentionally near-empty —
the design changes no application code.

## Decisions

- Decision 1 (taste): Two named tunnels (one per region) behind one Cloudflare Load Balancer
  with the Traffic Steering add-on, over a single tunnel with cross-region replicas — because an
  edge control plane that can name, health-check, weigh, and steer each region can auto-drain a
  soft-failed region, while a free routing heuristic that cannot distinguish the regions is blind
  to the most common failure class: a region serving errors while its transport stays connected.
  (Also evidence: Cloudflare tunnel-availability docs disclaim steering guarantees for replicas.)
- Decision 2: Both phases committed — entry-plane swap (phase 1), then dissolution of the two
  ingress EC2 pets + Traefik (phase 2) — because the driver is cost + learning and phase 1 alone
  is a financial wash; phase 2 carries the case (evidence: user answer; cost ledger in
  `mechanics.md` §8, net ≈ −$40/month).
- Decision 3 (taste): cloudflared targets `backend` Service directly from phase 1, bypassing
  Traefik immediately — because a staged migration should bake the end-state topology from day
  one and keep the old path only as rollback; chaining the new path through components scheduled
  for deletion means the bake validates a topology that will not survive.
- Decision 4 (taste): GA stays dormant exactly 2 weeks post-cutover, then is destroyed — because
  dormant rollback paths should be time-boxed to one full deploy cycle plus the drill set;
  indefinite dormancy is unowned insurance that silently defeats a cost-driven migration
  (window: user answer; destroyed GA's anycast IPs are unrecoverable — accepted).
- Decision 5 (taste): All Cloudflare-side objects (tunnels, tunnel configs, LB, pools, monitors,
  DNS) are managed by the Cloudflare terraform provider in a new `terraform/cloudflare/` root —
  because when the serving path crosses a vendor control plane, the vendor's objects belong in
  code with the rest of the infrastructure; hand-maintained dashboard artifacts drift and are
  invisible to review (user answer; today the DNS record is dashboard-only,
  `docs/tasks/034-multi-region-k8s-architecture/entry-plane.md:6-7`).
- Decision 6: Soft-failure drain window widens from ~30s (GA) to ~1–3 min (LB monitor floor) —
  accepted trade for this service's stakes (evidence: user chose path A with the window priced
  into the option).
- Decision 7: LB monitors probe `/actuator/health/readiness` directly with a Host header
  override — because cloudflared cannot rewrite paths, and `/healthz-local`'s only job was
  excluding a cross-region fallback route that was never deployed; probing through a per-region
  tunnel makes "local-only" inherent (evidence: `deploy/base/traefik-gateway.yaml:43-66`; no
  failover overlay exists in `deploy/`).
- Decision 8: Session affinity is the LB cookie, classified nice-to-have rather than invariant —
  because read-your-writes is already guaranteed at the application seam by the GTID cookie
  gate / primary re-check / tombstone layer (evidence:
  `backend/src/main/java/org/danteplanner/backend/CLAUDE.md`, Transactions & replica routing).
- Decision 9 (default): Ingress-node removal is gated by a `count`-toggle variable in the fleet
  module, flipped in phase 2 — follows the `enable_global_accelerator` gating precedent
  (`terraform/modules/fleet/network.tf:117-130`); the shared module serves both regions, so a
  toggle avoids lockstep surgery.
- Decision 10: mTLS Authenticated Origin Pull and the origin-TLS artifacts are removed, not
  migrated — because `VerifyClientCertIfGiven` admits certless callers by design (forced by GA's
  certless prober, `deploy/base/traefik-gateway.yaml:72-76`), so it never gated anything; the
  tunnel token is a strictly stronger origin authentication. The three doc sites claiming "mTLS
  is the real gate" are corrected in the doc sweep.
- Decision 11: Argo Smart Routing is skipped — because for the dominant JP→Seoul path the entry
  PoP and the tunnel PoP are the same or adjacent, leaving Argo little to optimize, and its
  $5/mo + $0.10/GB fee inverts the cost driver (evidence: free-plan Korea offshoring routes both
  eyeball and tunnel traffic through Japanese PoPs). Revisit only on measured regression — see
  Deferred.
- Decision 12 (default): cloudflared metric scrape ships ≤300 new active series total (keep-list
  enforced) into the metered Grafana Cloud remote-write — consistent with task-038's per-scrape
  discipline; exact keep-list is plan-mode detail.
- Decision 13: No NAT/private-subnet change — node public IPs remain the egress path; the tunnel
  closes inbound only (evidence: deliberate no-NAT cost design,
  `terraform/modules/fleet/network.tf:2-5`).

## Description

Replace the current public entry plane (Cloudflare orange-cloud proxy → GA anycast → per-region
ingress EC2 running Traefik) with: Cloudflare edge → Cloudflare Load Balancer (proximity/geo
steering, health monitors, cross-region fallback pools, session-affinity cookie) → per-region
named Cloudflare Tunnel → cloudflared Deployment (2 replicas, `role=app` nodes) →
`backend` Service. Then tear down everything the old plane needed: the GA module, the ingress
security group and its Cloudflare-CIDR sync machinery, the ingress EC2 pets, Traefik and its
Gateway API CRDs, and the origin-TLS secret chain.

Staged sequence (each intermediate state legal; full ordering in `mechanics.md` §4):

1. **Step-zero gate**: purchase the LB add-on ($5) on the Free zone; confirm Traffic Steering
   purchasability (~$10) and the health-monitor interval floor. Stop and re-debate if steering
   requires a Pro zone or the floor exceeds 60s.
2. **Phase 1 — build & cutover**: `terraform/cloudflare/` root (tunnels, configs, LB, pools,
   monitors, steering); cloudflared Deployments + ExternalSecrets via ArgoCD; verify on a test
   hostname; attach the LB to `api.dante-planner.com`. GA goes dormant (rollback path).
3. **Bake — 2 weeks**: drills I1–I8; one routine deploy observed; latency comparison.
4. **Phase 2 — teardown**: destroy GA; remove ingress SG rules, ingress nodes (toggle), Traefik
   manifests + CRD bootstrap, origin-TLS secrets, `update-cloudflare-ips.sh`, `ga-preflight.sh`,
   the `CloudflareIpSilence` alarm; doc sweep.

## Scope

Read for context:
- `terraform/global-accelerator/` (all), `terraform/modules/fleet/{network,ingress,variables}.tf`
- `deploy/base/{traefik-controller,traefik-gateway,origin-tls-secrets,jwt-secrets,kustomization}.yaml`,
  `deploy/overlays/`, `deploy/CLAUDE.md`, `deploy/README.md`
- `scripts/ops/update-cloudflare-ips.sh`, `scripts/ops/ga-preflight.sh`
- `docs/tasks/034-multi-region-k8s-architecture/{entry-plane.md,architecture-diagram.md,phase-15-notes.md}`
- Task-038 scrape/alert conventions (`docs/tasks/038-metrics-observability/`), `docs/RUNBOOK.md`

## Target

Create:
- `terraform/cloudflare/` — provider, two tunnels + remotely-managed configs, LB + 2 pools +
  monitors + steering + DNS attachment (contract: `mechanics.md` §1–§2)
- `deploy/base/cloudflared.yaml` (Deployment + ExternalSecret) + per-region overlay patches
- Prometheus scrape + alert rules for cloudflared (task-038 conventions; `mechanics.md` §6)

Modify:
- `deploy/base/kustomization.yaml`; fleet module (ingress toggle variable, Decision 9);
  `docs/RUNBOOK.md`, architecture diagram, `deploy/README.md` (doc sweep incl. Decision 10 fix)

Delete (phase 2, full ledger in `mechanics.md` §5):
- `terraform/global-accelerator/`; ingress SG + rules + prefix-list data source; ingress EC2
  (via toggle); `deploy/base/traefik-controller.yaml`, `traefik-gateway.yaml`,
  `origin-tls-secrets.yaml`; Gateway API CRD bootstrap; `scripts/ops/update-cloudflare-ips.sh`,
  `scripts/ops/ga-preflight.sh`; `CloudflareIpSilence` alarm

## Invariants

- INV1: While ≥1 region is healthy, `api.dante-planner.com` serves 200s — verify: drill D1
  (hard-fail, kill one region's cloudflared; `mechanics.md` §7)
- INV2: After phase 2, no security group carries a public inbound rule other than `cluster_self`
  and `redis_auth_cross_region` — verify: terraform plan assertion + external port scan (drill D6)
- INV3: Proximity routing holds (JP vantage → Seoul, US vantage → Oregon) — verify: drill D2,
  vantage curls read against per-region Prometheus request-rate deltas
- INV4: A soft-failed region (readiness failing, tunnel up) drains within 3 minutes — verify:
  drill D3 (wedged-readiness), demotion time measured
- INV5: During the bake window, rollback (re-point hostname at GA anycast IPs) restores service
  within DNS TTL — verify: drill D4, executed once early in the bake
- INV6: A routine deploy-fleet cycle never demotes a pool — verify: operational observation of
  one production deploy during bake (drill D5)
- INV7: SSE streams connect, heartbeat, and reconnect through the tunnel — verify: drill D7
  (browser session across a forced cloudflared restart)
- INV8: The auth-Redis replication path (`redis_auth_cross_region`, VPC peering, internal DNS)
  is untouched — verify: terraform plan shows no-op on those resources, both phases
- INV9: LB DNS-query usage stays within the included 500k/month — verify: Cloudflare
  usage/billing check during bake, then monthly (metered sink budget)
- INV10 (default): cloudflared scrape adds ≤300 active series to the Grafana Cloud remote-write
  — verify: Prometheus `scrape_samples_scraped`/series count query after scrape lands

## Behavior Inventory

Seam: the public entry plane (Cloudflare proxy → GA → ingress SG → Traefik → `backend:8080`).
Rows read from the live implementation during the debate.

| # | Seam element | Observable behavior (as-is) | Verdict |
|---|---|---|---|
| B1 | GA proximity routing | client (CF-egress) routed to nearest healthy region | preserved — LB proximity/geo steering (Decision 1) |
| B2 | GA health failover | unhealthy region drained in ~30s via `/healthz-local` | preserved, relaxed — drain ≤3 min (Decision 6) |
| B3 | GA SOURCE_IP affinity | a CF edge IP pins to one region | dropped as guarantee — cookie affinity best-effort (Decision 8) |
| B4 | Static anycast front-door IPs | DNS A-records target 166.117.53.62/99.83.184.149 | dropped — LB-owned hostname; IPs live only as bake-window rollback (Decision 4) |
| B5 | Traefik TLS termination (CF Origin Cert) | edge→origin leg encrypted via origin-tls | dropped — tunnel QUIC replaces it (Decision 10) |
| B6 | mTLS `VerifyClientCertIfGiven` | verifies CF client cert when presented; admits certless | dropped — never gated; superseded by tunnel auth (Decision 10) |
| B7 | Path routing `/` → `backend:8080` | all API paths reach the Spring Service | preserved — cloudflared ingress rule |
| B8 | `/healthz-local` → readiness rewrite | health URL maps to local Spring readiness | preserved in function — monitor probes readiness path directly; the URL itself retired (Decision 7) |
| B9 | SSE pass-through | event streams flow unbuffered, heartbeats survive | preserved — cloudflared streams natively |
| B10 | Client IP via `CF-Connecting-IP` | rate-limit identity = real client IP | preserved — `ClientIpResolver.java:236-240` unchanged |
| B11 | Origin admits only CF edge + R53 health CIDRs | SG allowlist is the origin gate | preserved, strengthened — zero public inbound |
| B12 | GA traffic dials | per-region weight control for drills/cutover | preserved — LB pool/origin weights |

## Done When

- [ ] Step-zero gate passed: LB add-on active on the Free zone; Traffic Steering purchasable and
      priced; monitor floor recorded in `mechanics.md` §3 — or stopped and re-debated (live-only)
- [ ] `terraform/cloudflare/` applies cleanly: two healthy tunnels, LB + pools + monitors +
      steering as per `mechanics.md` §1–§2 (infra)
- [ ] cloudflared Deployments green in both regions, each tunnel reporting ≥4 edge connections (infra)
- [ ] Test-hostname verification: JP vantage lands Seoul, US vantage lands Oregon (INV3) (live-only)
- [ ] Cutover executed; INV1–INV9 verified during the 2-week bake per the drill specs (live-only)
- [ ] Rollback drill (INV5) passed early in the bake window (live-only)
- [ ] Phase-2 teardown applied; INV2 and INV8 re-verified post-teardown (infra)
- [ ] Doc sweep complete: all three "mTLS is the real gate" sites corrected; RUNBOOK and
      architecture diagram reflect the new entry plane (infra)
- [ ] All existing tests pass — backend and frontend suites untouched and green (local-tdd)
- [ ] Following invoice shows ≈ −$40/month delta per `mechanics.md` §8 ledger (live-only)

## Deferred

- Argo Smart Routing evaluation — deferred until the bake's latency comparison produces data;
  until then, US/EU (transpacific/transcontinental) API latency may modestly regress versus GA's
  backbone, by an unmeasured amount (Decision 11).
- Smart Tiered Caching toggle — free and orthogonal; until enabled, cache-miss fan-in behavior
  is unchanged (no user-visible consequence beyond marginal cache hit ratio).

## Test Plan

> No application code changes: the gradle/vitest suites are regression rails only. This task's
> verification vehicles are terraform, kustomize rendering, and the live drill set.

### Test Runner

- Terraform: `terraform -chdir=terraform/cloudflare validate` / `plan` (never `cd`; per-root
  `-chdir`), same for touched roots (`oregon`, `seoul`, `global-accelerator` destroy plan)
- Manifests: `kubectl kustomize deploy/overlays/oregon` / `.../seoul` render clean
- Live: drill specs D1–D7 in `mechanics.md` §7; Prometheus queries for INV3/INV10;
  external port scan for INV2

### Tests to Write

- [ ] INV1/INV4/INV5/INV6/INV7 → drills D1, D3, D4, D5, D7 (procedures: `mechanics.md` §7)
- [ ] INV2/INV8 → terraform plan assertions (no public ingress rules; no-op on auth-Redis/peering)
      + port scan D6
- [ ] INV3 → drill D2 vantage checks against per-region Prometheus deltas
- [ ] INV9/INV10 → Cloudflare usage check + Prometheus series-count query
- [ ] Preserved Behavior Inventory rows pinned: B1/B2/B12 → D1–D3; B7/B9 → D7 + smoke curl;
      B10 → existing `ClientIpResolver` unit suite (already green); B11 → D6
- [ ] Every invariant above has its drill/assertion assigned — none ships unverified
