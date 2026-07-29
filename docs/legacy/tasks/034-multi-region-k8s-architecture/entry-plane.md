# Entry plane — Global Accelerator + Traefik failover + Cloudflare

The two-region front door (Phase 14 chunk 5). GA is authored as code
(`terraform/global-accelerator`); the Traefik failover and Cloudflare mTLS pieces
are specified here because they can't be schema-validated in this repo (Traefik
CRDs aren't in the kustomize base; no Cloudflare provider is wired) and shipping
unvalidatable CRDs into `deploy/base` would risk the live Oregon ArgoCD sync.

## Layering

```
client → Cloudflare (proxy + mTLS Authenticated Origin Pulls, the real gate)
       → Global Accelerator (anycast, proximity routing, ~30s regional failover)
       → regional ingress EC2 :443 (client IP preserved)  [SG: CF + GA-health only]
       → Traefik (Gateway API) → local Spring  ── connection-fail only ──▶ other region's Spring
```

## 1. Global Accelerator — DONE (terraform/global-accelerator)

- TCP:443 listener, `SOURCE_IP` affinity, two endpoint groups (EC2 instance
  endpoints, `client_ip_preservation_enabled = true`).
- Health check = HTTPS `/healthz-local` — LOCAL Spring readiness only. The
  fallback route is excluded from `/healthz-local` (see the base `healthz-local`
  HTTPRoute) so a region limping along on cross-region fallback reads unhealthy
  and GA steers clients to the healthy region directly.
- Seoul endpoint group is `count`-gated on `seoul_ingress_instance_id` — GA runs
  single-region (Oregon) until Seoul is provisioned, then add the tfvar.
- Apply once (durable edge, like `oregon-edge`); feed both regions'
  `ingress_instance_id` outputs into its tfvars.

## 2. Traefik cross-region failover — SPECIFIED (author per-overlay when Seoul is live)

A Traefik `failover` service: local Spring is `service`, the other region's Spring
is `fallback`, tripped by **connection failure ONLY** — never latency or 5xx
(cascade prevention). Alert when sustained fallback > 0.

The base `backend` HTTPRoute (`deploy/base/traefik-gateway.yaml`) points at the
`backend` Service; in each region's overlay, repoint it at a `TraefikService`:

```yaml
# deploy/overlays/<region>/traefik-failover.yaml  (Traefik CRD — needs traefik.io CRDs present)
apiVersion: traefik.io/v1alpha1
kind: TraefikService
metadata:
  name: backend-failover
spec:
  failover:
    service: backend-local          # local Spring (ClusterIP backend:8080)
    fallback: backend-remote         # other region's Spring, reached over the peering
    healthCheck: {}                  # connection-failure trip only — no 5xx/latency criteria
---
# backend-remote is an ExternalName / Endpoints Service pointing at the OTHER
# region's ingress or app node over the Seoul<->Oregon peering. The remote
# endpoint IP is injected at sync (dynamic), same mechanism as REDIS_AUTH_HOST.
```

Oregon's fallback = Seoul's Spring; Seoul's fallback = Oregon's Spring. The remote
target rides the Seoul↔Oregon peering (chunk 4). Because `/healthz-local` excludes
this fallback, GA never sees a fallback-serving region as healthy.

## 3. Cloudflare mTLS Authenticated Origin Pulls — SPECIFIED (runbook step)

- Cloudflare proxies `api.dante-planner.com` and presents a **client certificate**
  on origin pulls; the ingress Traefik requires+verifies it (Authenticated Origin
  Pulls). This is the REAL origin gate — the SG is defense-in-depth.
- **SG allowlist** = Cloudflare edge ranges + GA health-check ranges ONLY. This is
  the module's `ingress_allowed_cidrs` var (already the production value for
  Oregon; set the same for Seoul). No `0.0.0.0/0`.
- Point Cloudflare's `api` record at the **GA DNS name / anycast IPs**
  (`terraform/global-accelerator` outputs) once both regions are healthy — GA, not
  a single ingress EIP, becomes the durable front door. The per-region ingress
  EIP (chunk earlier) remains the Oregon-first cutover primitive until GA is cut in.
- Alert on origin-pull **client cert expiry** (a silently expired cert 5xxs every
  request).

## Cutover ordering (ties to oregon-cutover-runbook.md)

1. Oregon-first: Cloudflare `api` → Oregon ingress EIP (chunk: durable EIP). Prove prod.
2. Provision Seoul (chunks 3–4), add its GA endpoint group + `backend-remote`.
3. Cut Cloudflare `api` → GA anycast. Now proximity routing + ~30s failover are live.
4. Drill the region-kill (stopwatch GA failover) — Phase 15.
