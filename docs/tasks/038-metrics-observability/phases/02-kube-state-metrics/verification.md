# Phase 02: kube-state-metrics + etcd snapshot CR — PASS

Scope: render-local halves only. Live/drill halves (`up{job=...}==1`, head-series delta,
node-orphan drills, CRS runtime metric population) are DEFERRED-OUT-OF-SCOPE to phases 09/10.

### Suite

kustomize binary absent on this machine (confirmed: `which kustomize` → not found). Used
`kubectl kustomize` (kubectl v1.36.2, embedded kustomize v5.8.1) per Brief.

```
$ kubectl kustomize deploy/overlays/oregon > oregon.yaml   # exit=0, stderr empty, 853 lines
$ kubectl kustomize deploy/overlays/seoul  > seoul.yaml    # exit=0, stderr empty, 851 lines
```

Both renders exit 0, stderr empty, and tail ends at `origin-client-ca`:
```
spec:
  clientAuth:
    clientAuthType: VerifyClientCertIfGiven
    secretNames:
    - origin-client-ca
```

Manifest under test: `deploy/base/kube-state-metrics.yaml`, registered at
`deploy/base/kustomization.yaml:17`. Both regions render identically for the KSM slice
(oregon/seoul line numbers differ only by earlier-manifest offsets).

### Trace

| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| One Deployment per cluster, both regions identical | Mechanics §1 | MET | `deploy/base/kube-state-metrics.yaml:60-106` single KSM Deployment; arrives via `deploy/base/kustomization.yaml:17` | Exactly one KSM image line per render: oregon.yaml:346, seoul.yaml:333 |
| Image pinned `v2.19.1` | Mechanics §1 | MET | `kube-state-metrics.yaml:83` | oregon.yaml:346 / seoul.yaml:333 `registry.k8s.io/kube-state-metrics/kube-state-metrics:v2.19.1` |
| Placement `nodeSelector: role: data`, single replica | Mechanics §1 | MET | `kube-state-metrics.yaml:79-80,65` | render `role: data` (oregon:364 / seoul:351) + `replicas: 1` on KSM Deployment |
| Resources req 10m/32Mi, limit 64Mi | Mechanics §1 | MET | `kube-state-metrics.yaml:97-102` | oregon.yaml:344-345,342 (`cpu: 10m`,`memory: 32Mi`,`memory: 64Mi` limit); seoul.yaml:344-345,342 |
| Scope args `--resources=nodes,pods,daemonsets` | Mechanics §1 | MET | `kube-state-metrics.yaml:85` | oregon.yaml:343 / seoul.yaml:330 `--resources=nodes,pods,daemonsets` (not widened) |
| Allowlist (8 core entries) | Mechanics §1 | MET | `kube-state-metrics.yaml:87` | oregon.yaml:345 / seoul.yaml:332 — all 8 present: kube_node_status_condition, kube_node_spec_taint, kube_pod_status_phase, kube_pod_status_ready, kube_pod_container_status_restarts_total, kube_pod_container_status_waiting_reason, kube_daemonset_status_desired_number_scheduled, kube_daemonset_status_number_ready |
| Scrape wiring annotations (scrape/port/job) | Mechanics §1 | MET | `kube-state-metrics.yaml:73-76` | oregon.yaml:335-337 / seoul.yaml:322-324 `prometheus.io/job: kube-state-metrics`, `prometheus.io/port: "8080"`, `prometheus.io/scrape: "true"` |
| RBAC: SA + ClusterRole (list/watch nodes,pods,daemonsets) + Binding, extended with etcdsnapshotfiles | Mechanics §1 | MET | `kube-state-metrics.yaml:2-33` | oregon render ClusterRole `kube-state-metrics`: apiGroups `""`→nodes,pods (list/watch); `apps`→daemonsets (list/watch); `k3s.cattle.io`→etcdsnapshotfiles (list/watch, lines 45-47) |
| CRS config: ETCDSnapshotFile / group k3s.cattle.io / metricNamePrefix kube_etcd / `status.creationTime`→Gauge | Mechanics §4 | MET | `kube-state-metrics.yaml:35-58` ConfigMap `kube-state-metrics-crs-config` | oregon.yaml:173-186 (seoul:177-190): `group: k3s.cattle.io`, `kind: ETCDSnapshotFile`, `metricNamePrefix: kube_etcd`, `path: [status, creationTime]`, `type: Gauge` |
| `--custom-resource-state-config-file` path RESOLVES to the mounted ConfigMap (wiring join b) | Mechanics §4 | MET | arg path `/etc/kube-state-metrics/crs-config.yaml` (`:86`) == mountPath `/etc/kube-state-metrics` (`:90`) + ConfigMap data key `crs-config.yaml` (`:40`); volumeMount name `crs-config` (`:89`) == volume name `crs-config` (`:104`) → ConfigMap `kube-state-metrics-crs-config` (`:106`==`:38`) | render arg oregon.yaml:344 / seoul.yaml:331; full mount chain flattened in render |
| 9th allowlist entry EQUALS the name KSM produces from CRS (wiring join a — the linchpin) | Brief expected-verdict / Mechanics §4 | MET | KSM emits `metricNamePrefix`+`_`+`metrics[].name` = `kube_etcd` (`:48`) + `snapshot_creation_timestamp_seconds` (`:53`) = `kube_etcd_snapshot_creation_timestamp_seconds`; allowlist 9th token (`:87`) is byte-identical → allowlist does NOT silently drop the CRS metric | both render allowlist lines end with `...,kube_etcd_snapshot_creation_timestamp_seconds` (oregon:345 / seoul:332) |
| KSM scope is all namespaces (no `--namespaces` restriction) | Mechanics §1 / INV3 | MET | no `--namespaces` arg in source | `grep -- '--namespaces'` empty in both renders + source → unrestricted namespace scope |
| INV1 (local): KSM pod carries `prometheus.io/job: kube-state-metrics` | requirements INV1 | MET | `kube-state-metrics.yaml:76` | oregon.yaml:335 / seoul.yaml:322 | 
| INV3 (local): allowlist present + `--resources` not widened beyond nodes,pods,daemonsets | requirements INV3 | MET | `kube-state-metrics.yaml:85,87` | render lines above; allowlist + narrow resources confirm cardinality guard |
| INV11 (local): no account-id / S3 bucket / secret in KSM render or source | requirements INV11 | MET | symmetric scan both surfaces: `grep -E '[0-9]{12}'` → no match in either render AND source; `grep -iE 's3\|bucket'` → no match in either render AND source (only `ServiceAccount` matched `account`, benign) | S3 fallback route not taken — no bucket/account leak; primary etcd CR route confirmed |
| Test Plan: both overlays build cleanly; output contains KSM allowlist flags (+ KSM resources/annotations/RBAC/CRS) | Test Plan | MET | see Suite + all rows above | both overlays exit 0; allowlist flag + resources + annotations + RBAC + CRS all present in render |

### Deferred (out of scope, not gaps)

- INV1 live `up{job="kube-state-metrics"}==1` — phase 10.
- INV3 live head-series delta (`prometheus_tsdb_head_series`, ≤~2k budget) — phase 10.
- etcd snapshot dead-man alert rule (past 1.5× interval) — phase 09.
- CRS runtime metric population (`status.creationTime` field path is best-effort from KSM/k3s
  docs; render is blind to whether KSM actually emits the metric) — phase 10 live-verify.
- Node-orphan / drill scenarios — phase 09/10.

### Gaps

None. Every in-scope render-local item is MET; both overlays render exit 0 and clean.
