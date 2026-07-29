# Phase 01: Prometheus prerequisites — PASS

### Suite

Kustomize binary absent; per Brief used the kubectl fallback (both must exit 0).

```
$ kubectl kustomize deploy/overlays/oregon   # exit=0, 716 lines, empty stderr
$ kubectl kustomize deploy/overlays/seoul    # exit=0, 714 lines, empty stderr
```

Both overlays render clean (exit 0, no stderr).

### Trace

| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| Both overlays render clean w/ region-correct `CLUSTER_NAME`, `prometheus_io_job` relabel, self-scrape job (render half; KSM+mysqld_exporter deferred to ph02/03) | Done-When #1 | MET | oregon+seoul both `kubectl kustomize` exit=0; oregon env `CLUSTER_NAME: oregon` (render L253-256), seoul `CLUSTER_NAME: seoul` (render L240-243); relabel present both; self-scrape `prometheus` job present both | Render-grep is the pinning test for this phase: `grep CLUSTER_NAME`, `grep prometheus_io_job`, `grep localhost:9090` on both renders all hit; base source deploy/base/prometheus.yaml, patches deploy/overlays/{oregon,seoul}/prometheus-cluster-patch.yaml |
| `prometheus_io_job` relabel present in rendered pod-discovery job (live `up{}==1` half is ph10, out of scope) | INV1 | MET | Appended as last rule of `backend` job `relabel_configs`, both overlays: `source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_job]`, `action: replace`, `target_label: job`, `regex: (.+)` — oregon render L144-147, seoul render L148-151 | render-grep `prometheus_io_job` hits inside `job_name: backend` block in both renders |
| `external_labels.cluster` carries `${CLUSTER_NAME}` placeholder per region; env var resolves to {oregon, seoul} (grouped-query half out of scope) | INV2 | MET | ConfigMap `global.external_labels.cluster: ${CLUSTER_NAME}` LITERAL, nested under `global:` matching the contract's dotted path — oregon render L122 `global:`/L124-125, seoul render L126 `global:`/L128-129; env resolves oregon→oregon, seoul→seoul on `prometheus` Deployment; `--enable-feature=expand-external-labels` arg present both (oregon L253, seoul L240) | render-grep: literal `${CLUSTER_NAME}` present (not a region value, per mechanics §2); `CLUSTER_NAME` env value oregon/seoul distinct per overlay |
| Both overlays build cleanly; output has `external_labels` w/ region-correct cluster (via env) + `prometheus_io_job` relabel (KSM allowlist flags are ph02, out of scope) | Test-Plan "Tests to Write" #1 | MET | Same evidence as above: both exit 0; `external_labels` block present both; relabel present both; region-correct env resolution | render-grep on both renders |

### Vacuity guard

PASS. oregon render `CLUSTER_NAME: oregon` and contains no `seoul` token anywhere (grep `seoul` → NONE). seoul render `CLUSTER_NAME: seoul`; the two `oregon` tokens in the seoul render are unrelated redis `replicaof` host references (render L112, L333, L342), NOT the `CLUSTER_NAME` env — seoul's `CLUSTER_NAME` value is `seoul`, not `oregon`. Guard holds.

### Out-of-phase-scope (deferred per Brief/manifest — NOT UNMET)

- Live `up{job=...}==1` half of INV1 → phase 10 live-verify.
- Grouped remote_write query half of INV2 (step-3) → phase 09/10.
- KSM + mysqld_exporter resources and KSM allowlist flags in Done-When #1 / Test-Plan #1 → phases 02/03.

### Gaps

None. All in-scope render-assertable items MET.
