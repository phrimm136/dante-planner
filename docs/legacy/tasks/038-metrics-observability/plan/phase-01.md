# Phase 01: Prometheus prerequisites

- Kind: local-tdd (kustomize render assertions; no red/green unit suite applies)
- Files:
  - `deploy/base/prometheus.yaml` (modify: `external_labels`, expand flag, job relabel, self-scrape job)
  - `deploy/overlays/oregon/prometheus-cluster-patch.yaml` (create: `CLUSTER_NAME=oregon` env patch)
  - `deploy/overlays/seoul/prometheus-cluster-patch.yaml` (create: `CLUSTER_NAME=seoul` env patch)
  - `deploy/overlays/{oregon,seoul}/kustomization.yaml` (modify: register the patch — additive)
- Tests: `kustomize build deploy/overlays/oregon` and `…/seoul` render assertions (kubectl kustomize fallback)
- External contract: rendered config carries `global.external_labels.cluster: ${CLUSTER_NAME}`, the
  Deployment arg `--enable-feature=expand-external-labels`, the `prometheus_io_job` relabel rule
  appended to the pod-discovery job, and a static `prometheus` self-scrape job against
  `localhost:9090`; the oregon overlay renders `CLUSTER_NAME=oregon`, seoul renders `seoul`.
- Mechanics sections: `mechanics.md` §2 (Prometheus config amendments — cluster identity, job identity, self-scrape).
- Implementation methods:
  - Env-var + `expand-external-labels` for cluster identity, per `mechanics.md` §2 — strategic-merge
    cannot deep-merge inside the `prometheus.yml` string, so each overlay patches one env var.
    Exemplar Deployment/ConfigMap to amend: `deploy/base/prometheus.yaml:35-99`.
  - Relabel rule appended to the existing pod-discovery `relabel_configs`, verbatim from `mechanics.md`
    §2. Exemplar existing relabel block: `deploy/base/prometheus.yaml:43-55`.
- Considerations:
  - `requirements.md` Decisions (prereqs ride FIRST): without `cluster` external label, remote_write
    merges Oregon+Seoul into one stream (INV2); without the generic `prometheus.io/job` relabel every
    annotated pod lands under `job="backend"`, poisoning `absent(up{job=…})` dead-men.
  - `mechanics.md` §2 (self-scrape): the `prometheus` static job feeds the staleness meta-alert (§3 row M).
  - This relabel MUST ship before/with the first non-backend scrape target (02, 03, 04 depend on it).
- Depends on: none
- Verify: both overlays `kustomize build` cleanly; rendered output greps show region-correct
  `cluster:` value, `expand-external-labels`, the `prometheus_io_job` relabel, and the self-scrape job.
