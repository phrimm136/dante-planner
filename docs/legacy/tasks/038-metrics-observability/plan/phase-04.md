# Phase 04: Gap-cluster scrape wiring

- Kind: infra (authored as code in the CP bootstrap + the Prometheus base config; apply is
  consent-gated — a manual, idempotent `kubectl patch` against the live CPs plus a GitOps-synced
  config change, converging automatically on the next rebuild)
- Files:
  - `terraform/modules/fleet/user-data/cp.sh.tftpl` (modify: (a) extend the patch loop with scrape
    annotations for ArgoCD application-controller, ESO controller, CoreDNS; (b) add
    `--etcd-expose-metrics=true` to the k3s server args so etcd metrics on `:2381` are emitted)
  - `deploy/base/prometheus.yaml` (modify: ADD dedicated scrape job(s) for the k3s server host —
    apiserver `:6443/metrics` (bearer-token auth) + etcd `:2381/metrics`. Additive to phase-01's
    committed config: append to `scrape_configs`; do NOT disturb the `backend`/`prometheus` jobs, the
    `prometheus_io_job` relabel, or the `external_labels` block)
- Tests: none local (infra); verification is the live drill below
- External contract: after the bootstrap patch loop + config change, all four gap targets are scraped
  under their own `job` labels in both regions:
  1. ArgoCD application-controller — `:8082`, job `argocd` (pod annotation)
  2. ESO controller — `:8080`, job `external-secrets` (pod annotation)
  3. CoreDNS — `:9153`, job `coredns` (pod annotation; VERIFY-FIRST override, see Considerations)
  4. CP/etcd health — k3s server host: apiserver request latency (`:6443/metrics`, job `apiserver`,
     bearer-token auth) + etcd fsync (`:2381/metrics`, job `etcd`), via the NEW dedicated Prometheus
     scrape job(s), NOT pod annotations.
- Mechanics sections: `mechanics.md` §4 (ArgoCD, ESO, CP/etcd health, CoreDNS rows).
- Implementation methods:
  - Idempotent `kubectl patch` in the existing patch-loop pattern — exemplar
    `terraform/modules/fleet/user-data/cp.sh.tftpl:79-80` (the ArgoCD deploy/statefulset patch loop)
    — for the ArgoCD/ESO/CoreDNS pod annotations.
  - `--etcd-expose-metrics=true` added to the `INSTALL_K3S_EXEC` server args block — insertion point
    `terraform/modules/fleet/user-data/cp.sh.tftpl:39-50`.
  - Dedicated Prometheus scrape job for the k3s server host: apiserver `:6443/metrics` uses the
    prometheus ServiceAccount bearer token (`tls_config` + `bearer_token_file`); etcd `:2381/metrics`
    is plaintext on the node once the flag is set. Discovery targets the control-plane node — k3s runs
    apiserver + embedded etcd as the server host PROCESS, not as pods, so pod-annotation discovery
    structurally cannot reach them; this is why the job is dedicated, not annotation-driven.
- Considerations:
  - `requirements.md` Decisions (coverage-gap clusters): control planes fail silent — GitOps plane
    (ArgoCD) and the secrets path (ESO, a fail-open surface per `deploy/CLAUDE.md`) must be observable.
  - Scope ruling (orchestrator, option A): the CP/etcd leg is unsatisfiable via pod annotations alone;
    it requires a dedicated `deploy/base/prometheus.yaml` scrape job + the etcd-metrics flag. RBAC is
    NOT a gap — phase-01's ClusterRole already grants `nodes/metrics` + `endpoints`
    (`prometheus.yaml:13-15`). If apiserver `/metrics` proves to need a nonResourceURL grant at impl,
    that is an additive rule on the existing `prometheus` ClusterRole, still within this file scope.
  - `mechanics.md` §4 CoreDNS row — VERIFY FIRST whether k3s's bundled CoreDNS already carries scrape
    annotations and is silently swept into `job="backend"` today; the patch must OVERRIDE the
    `prometheus.io/job` annotation to `coredns`, not merely add. One more reason phase 01's relabel ships first.
  - INV8 (GitOps drift observable): `argocd_app_info` OutOfSync/Degraded must appear within one scrape.
  - The etcd-SNAPSHOT metric emission (dead-man source) lives in phase 02 (KSM CR-state); the
    etcd-snapshot ALERT is phase 09. This phase owns the four SCRAPE targets only (incl. live etcd/
    apiserver health metrics), not snapshot state.
- Depends on: 01 (job relabel for the pod targets; the new scrape job is additive to phase-01's config)
- Verify: drill INV8 — point a scratch Application at a broken manifest; `OutOfSync`/`Degraded`
  visible in `argocd_app_info` in local Prometheus within one scrape (record in the phase verification
  doc). Confirm all four targets (ArgoCD, ESO, CoreDNS, apiserver+etcd) scraped under their own job
  labels in both regions (Done When item 4).
