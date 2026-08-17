# Mechanics: GA → Cloudflare Tunnel + Load Balancer

Companion to `requirements.md`. Transcribed from the `/brainstorm` debate of 2026-07-17.
Sections marked **binding** are contracts implementation is held to; §9 is **reference**
(derivations preserved against compaction, citable but not contractual).

## §1 Load Balancer contract (binding)

One LB, attached to `api.dante-planner.com` (replaces the A-records to GA anycast IPs).

| Object | Value |
|---|---|
| Pools | `oregon`, `seoul` — one per region |
| Origin per pool | `<tunnel-uuid>.cfargotunnel.com`, **Host header override** `api.dante-planner.com` (required — the app hostname cannot be an LB endpoint behind a tunnel) |
| Steering | proximity/geo (Traffic Steering add-on); region map: NEAS/Japan-PoP traffic → `seoul`, WNAM/ENAM → `oregon` |
| Fallback | each pool's fallback is the other region (`seoul`→`oregon`, `oregon`→`seoul`) |
| Session affinity | `__cflb` cookie, on; best-effort only (requirements Decision 8) |
| Monitor | HTTPS through the tunnel origin; path `/actuator/health/readiness`; Host `api.dante-planner.com`; expect 200; interval 60s (floor pending §3 gate); retries 2 |
| Weights | pool/origin weights are the drill/cutover dials (GA traffic-dial parity) |

DNS: the LB record must stay **proxied (orange-cloud)** — a DNS-only record bypasses the tunnel
path entirely.

## §2 Tunnel + cloudflared contract (binding)

| Item | Value |
|---|---|
| Tunnels | one **named** tunnel per region; config **remotely managed** via the terraform provider (rules live in code; pods hold only the token) |
| Ingress rules | `api.dante-planner.com` → `http://backend.danteplanner.svc.cluster.local:8080`; final catch-all → 404 (cloudflared requires it). No path rewriting exists in cloudflared — do not recreate `/healthz-local` |
| Workload | Deployment (not DaemonSet), 2 replicas, `nodeSelector role=app`, **no hostNetwork**, `--metrics 0.0.0.0:2000` |
| Sizing | requests 100m CPU / 128Mi, limit 256Mi (mirrors retired Traefik sizing, `deploy/base/traefik-controller.yaml:88-93`) |
| Token flow | one AWS Secrets Manager entry per region → `ExternalSecret` per overlay (pattern: `deploy/base/jwt-secrets.yaml`; per-region `SecretStore` patch as in `deploy/overlays/seoul/secretstore-region-patch.yaml`). Never a hand-authored k8s Secret (`deploy/CLAUDE.md`) |
| Provider auth | scoped Cloudflare API token (Tunnel + LB + DNS edit), custody in untracked tfvars (existing `terraform.tfvars` gitignore precedent) |

## §3 Step-zero gate (binding)

Before any terraform: subscribe Load Balancing ($5/mo) on the Free zone and record in this file:
(a) Traffic Steering add-on purchasability + actual price (expected ~$10/mo);
(b) the minimum monitor interval offered.
**Stop triggers** — back to `/brainstorm`, do not improvise: steering requires a Pro zone
($25/mo inverts the cost case), or monitor floor > 60s (re-ratify the INV4 window).

## §4 Cutover sequence + rollback primitive (binding)

1. Apply `terraform/cloudflare/` (tunnels, configs, LB on a **test hostname**); deploy
   cloudflared via ArgoCD. State: both paths live, production traffic untouched.
2. Verify test hostname: tunnels ≥4 connections; INV3 vantage checks; monitor behavior.
3. **Cutover primitive**: attach the LB to `api.dante-planner.com` (terraform). GA dormant.
4. Early in bake: rollback drill D4. **Rollback primitive** (bake window only): detach LB /
   restore A-records to GA anycast `166.117.53.62` / `99.83.184.149` — GA and Traefik still
   alive and untouched until phase 2.
5. After 2-week bake + all drills green: phase 2 teardown (§5). Post-teardown rollback is
   re-creating GA (mints NEW anycast IPs, ~30 min hands-on) — accepted residual risk.

Ops caveat carried from the store: never health-check through the proxied public hostname
(Cloudflare bot protection 521/403); LB monitors are Cloudflare-internal probes and exempt.
Deploy-time verification stays SSM/kubectl (unchanged).

## §5 Teardown ledger (binding)

Phase 1 completion deletes nothing; phase 2 deletes exactly:

| Artifact | Location |
|---|---|
| GA module (accelerator, listener, endpoint groups) | `terraform/global-accelerator/` (root removed) |
| Cloudflare CIDR ingress rules + variable + tfvars | `terraform/modules/fleet/network.tf:99-107`, `ingress_allowed_cidrs` |
| R53 health-check prefix rule + data source + flag | `network.tf:109-130`, `enable_global_accelerator` |
| Ingress SG | `network.tf:92-97` |
| Ingress EC2 (per region) | `terraform/modules/fleet/ingress.tf` via new count-toggle var (Decision 9) |
| Traefik: controller, gateway, TLSOption, RBAC, GatewayClass | `deploy/base/traefik-controller.yaml`, `traefik-gateway.yaml` |
| origin-tls / origin-client-ca ExternalSecrets + ASM entries | `deploy/base/origin-tls-secrets.yaml` (ASM entries: user deletes) |
| Gateway API CRD bootstrap | CP user-data `gateway_api_version` (see `deploy/README.md:56`) |
| IP-sync cron + heartbeat alarm | `scripts/ops/update-cloudflare-ips.sh`, `CloudflareIpSilence` |
| GA preflight | `scripts/ops/ga-preflight.sh` |

**Must-not-touch list** (INV8): `cluster_self`, `cluster_all` egress, `redis_auth_cross_region`
(`network.tf:60-88`); all RDS/fleet peering + routes + `danteplanner.internal` zone; node public
IPs on cp/data/app (egress path — no-NAT design, `network.tf:2-5`).

## §6 Observability & alerts (binding)

- Scrape cloudflared `:2000` per region (task-038 conventions); metric keep-list bounds new
  active series ≤300 total (requirements INV10) — keep at minimum: tunnel HA connection count,
  request/error counts, latency summary.
- New alert: per-region tunnel connections < 2 for 5m — replaces `CloudflareIpSilence`.
- Cloudflare-side: LB pool health notifications on (dashboard/terraform), informational.

## §7 Drill specs (binding)

| Drill | Procedure | Pass |
|---|---|---|
| D1 hard-fail | scale one region's cloudflared to 0 | continuous 200s from a vantage in that region's geography; recovery on scale-up (INV1) |
| D2 proximity | curl from JP + US vantages, read per-region Prometheus request-rate deltas | JP→Seoul, US→Oregon (INV3) |
| D3 soft-fail | patch one region's backend readiness to a bogus path | pool demoted ≤3 min, traffic 100% other region; revert → re-promotion (INV4) |
| D4 rollback | re-point hostname at GA IPs, then back | service continuous within DNS TTL each way (INV5) |
| D5 deploy | one routine deploy-fleet cycle during bake | pool health stays green throughout (INV6) |
| D6 surface | external port scan of all node public IPs post-phase-2 | no open port; terraform plan shows only `cluster_self` + `redis_auth_cross_region` ingress (INV2) |
| D7 SSE | browser EventSource session; restart serving-region cloudflared mid-stream | heartbeats observed; reconnect re-subscribes (INV7) |

## §8 Cost ledger (binding numbers, on-demand list prices)

| Item | Monthly |
|---|---|
| OUT: GA fixed | −$18.25 |
| OUT: GA DT-Premium (est. at current volume) | −$2–5 |
| OUT: 2× t4g.small ingress (us-west-2 + ap-northeast-2) | −$27.5 |
| OUT: 2× public IPv4 | −$7.3 |
| IN: LB add-on | +$5 |
| IN: Traffic Steering add-on (verify at §3 gate) | +$10 |
| **Net** | **≈ −$40/mo** |
| Bake overlap (2 weeks dormant GA) | one-time ≈ $9 |

## §9 Reference (session derivations, non-binding)

- Korea PoP: free-plan Korean traffic enters at Japanese PoPs (transit economics); both GA and
  LB steer on the CF-egress location, so the design preserves the Japan-PoP→Seoul mapping.
  A future zone-plan upgrade improves Korean latency under this design unchanged.
- Zero-Downtime Failover (Business+) likely does not cover tunnel error codes (530/1033) —
  monitor demotion is the real failover clock on this plan.
- Single-tunnel replicas were rejected on the docs' own terms: "no guarantee about which
  connection is chosen," no health input, no dials (Cloudflare tunnel-availability docs).
- A↔B convertibility: switching between LB-per-region-tunnels and single-tunnel-replicas is a
  Cloudflare-side config change; the k8s workload is identical except token count.
