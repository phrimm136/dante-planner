# Phase 02: kube-state-metrics + etcd snapshot custom-resource-state

- Kind: local-tdd (kustomize render assertions)
- Files:
  - `deploy/base/kube-state-metrics.yaml` (create: SA, ClusterRole, ClusterRoleBinding, Deployment, Service)
  - `deploy/base/kustomization.yaml` (modify: register the resource — additive line)
- Tests: `kustomize build deploy/overlays/{oregon,seoul}` render assertions on the KSM resources
- External contract: rendered KSM Deployment on `nodeSelector role=data`, single replica, image
  `registry.k8s.io/kube-state-metrics/kube-state-metrics:<current stable v2 minor, pinned at impl>`,
  args `--resources=nodes,pods,daemonsets` + the eight-metric `--metric-allowlist`, pod annotations
  `prometheus.io/scrape:"true"` / `prometheus.io/port:"8080"` / `prometheus.io/job:"kube-state-metrics"`,
  and RBAC (`list`,`watch` on nodes/pods/daemonsets) extended with the `k3s.cattle.io`
  `etcdsnapshotfiles` CR; a custom-resource-state config emitting `ETCDSnapshotFile` age.
- Mechanics sections: `mechanics.md` §1 (KSM contract) and §4 row "etcd snapshot dead-man" (primary route).
- Implementation methods:
  - RBAC block shape copied from the Prometheus SA/ClusterRole/Binding trio — exemplar
    `deploy/base/prometheus.yaml:3-28`; extend the ClusterRole with the `etcdsnapshotfiles` CR.
  - Scrape-annotation contract copied verbatim from the DaemonSet exemplar
    `deploy/base/spring-daemonset.yaml:17-20` (adds `prometheus.io/job`).
  - etcd dead-man PRIMARY route (`ETCDSnapshotFile` CRs via KSM custom-resource-state) is viable:
    the fleet pins k3s `v1.31.0` ≥ 1.27 (`terraform/modules/fleet/variables.tf:179`). Preferred over
    the S3 object-age fallback because the snapshot bucket name embeds the account id
    (`terraform/modules/fleet/s3.tf:5`) and must not be committed (INV11).
- Considerations:
  - `requirements.md` INV3 (cardinality budget ≤ ~2k head series/cluster) — the allowlist is the
    guard; do not widen `--resources` or drop the allowlist. Resources `10m`/`32Mi` req, `64Mi` limit.
  - `mechanics.md` §1: all namespaces (cluster too small for namespace filtering to buy anything).
  - `requirements.md` Decisions (KSM rationale): Pod/Node conditions live only in the apiserver; the
    fleet has no cloud-controller-manager, so orphaned NotReady nodes are never GC'd — KSM is the only
    eye on that gap.
  - INV1: the `prometheus.io/job` annotation only takes effect once phase 01's relabel is present.
- Depends on: 01 (job relabel must exist before this non-backend target scrapes)
- Verify: both overlays `kustomize build` cleanly; rendered output contains the allowlist flags,
  scope args, scrape annotations, and the etcdsnapshotfiles RBAC + CR-state config.
