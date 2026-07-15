# Phase 04: Gap-cluster scrape wiring — PASS

Infra phase. Runtime correctness (live scrape in both regions, INV8 GitOps-drift drill,
cross-node etcd reachability) is a consent-gated LIVE drill that cannot run here; per the
Brief those items are classified DEFERRED-LIVE (expected, not a failure). This report verifies
the STATIC contract: targets/ports/job labels/auth/RBAC wired, manifests render, syntax clean.

### Suite
```
$ bash -n terraform/modules/fleet/user-data/cp.sh.tftpl           # exit 0
$ shellcheck -S error terraform/modules/fleet/user-data/cp.sh.tftpl # exit 0
$ kubectl kustomize deploy/overlays/oregon                         # exit 0 (stderr empty)
$ kubectl kustomize deploy/overlays/seoul                          # exit 0 (stderr empty)
$ grep -E 'job_name: (apiserver|etcd)' <oregon render>  → 228:apiserver, 239:etcd
$ grep -E 'job_name: (apiserver|etcd)' <seoul render>   → 232:apiserver, 243:etcd
$ grep 'nonResourceURLs' <oregon render>  → line 69   ;  <seoul render> → line 69
```
All scoped commands exit 0; both overlays render; both new jobs and the nonResourceURLs
rule are present in both region renders.

### Trace
| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| C1: `--etcd-expose-metrics=true` inside `INSTALL_K3S_EXEC="server ..."` args | checklist §1 | MET | cp.sh.tftpl:46 (within the args block cp.sh.tftpl:39-51) | `bash -n` exit 0; `git diff HEAD` shows single-line additive insert into server args |
| C2a: ArgoCD patch — StatefulSet argocd-application-controller, ns argocd, pod-template annotations, port 8082, job argocd, scrape/port/path/job all set | checklist §2, contract target 1, mechanics §4 (ArgoCD) | MET | cp.sh.tftpl:120 — `kubectl -n argocd patch statefulset argocd-application-controller --type merge -p '{"spec":{"template":{"metadata":{"annotations":{...8082...argocd}}}}}'` (writes `spec.template.metadata.annotations`) | `bash -n`/`shellcheck` exit 0 |
| C2b: ESO patch — Deployment external-secrets, ns external-secrets, pod-template annotations, port 8080, job external-secrets | checklist §2, contract target 2, mechanics §4 (ESO) | MET | cp.sh.tftpl:121 — `kubectl -n external-secrets patch deployment external-secrets --type merge -p '{...8080...external-secrets}'` | `bash -n`/`shellcheck` exit 0 |
| C2c: CoreDNS patch — Deployment coredns, ns kube-system, pod-template annotations, port 9153, job coredns, OVERRIDE (merge = set) | checklist §2, contract target 3 (VERIFY-FIRST override), mechanics §4 (CoreDNS) | MET | cp.sh.tftpl:122 — `kubectl -n kube-system patch deployment coredns --type merge -p '{...9153...coredns}'`; `--type merge` SETS `prometheus.io/job`, overriding any bundled k3s CoreDNS annotation (rationale comment cp.sh.tftpl:115-119) | `bash -n`/`shellcheck` exit 0 |
| C3: job `apiserver` — role:endpoints, keep default/kubernetes/https, scheme https, ca_file=SA ca.crt, bearer_token_file=SA token | checklist §3, contract target 4 (apiserver) | MET | prometheus.yaml:67-77 (role endpoints :68-69, scheme https :70, ca_file :72, bearer_token_file :73, keep regex `default;kubernetes;https` :75-77) | rendered in both overlays (oregon L228, seoul L232) |
| C4: job `etcd` — role:node, keep control-plane node label, `__address__`→InternalIP:2381, scheme http, no tls/bearer | checklist §4, contract target 4 (etcd) | MET | prometheus.yaml:78-90 (role node :79-80, scheme http :81, keep control-plane label :83-85, replacement `$1:2381`→`__address__` :86-90; no tls_config/bearer_token_file present) | rendered in both overlays (oregon L239, seoul L243) |
| C5: separate ClusterRole rule `nonResourceURLs: ["/metrics"]` / `get`; existing resource rule UNCHANGED | checklist §5 | MET | prometheus.yaml:16-17 (new nonResourceURLs rule); resource rule prometheus.yaml:13-15 unchanged. `git diff HEAD` shows the resource rule untouched, only the nonResourceURLs rule added | rendered nonResourceURLs at oregon L69 / seoul L69; `nodes/metrics` resource rule at L61 in both |
| C6: additive-only — backend job, self-scrape job, pod relabel_configs, external_labels `cluster: ${CLUSTER_NAME}`, Deployment, Service UNCHANGED | checklist §6 | MET | `git diff HEAD deploy/base/prometheus.yaml` = only +nonResourceURLs rule and +apiserver/+etcd jobs; backend/self-scrape/relabel_configs/Deployment/Service hunks absent. `git diff HEAD cp.sh.tftpl` = only +etcd flag and +3 patch calls. external_labels `cluster: ${CLUSTER_NAME}` present in both renders (oregon L202, seoul L206) | both overlays render exit 0, stderr empty |
| Done-When: ArgoCD & ESO scraped under own job labels in both regions | requirements Done-When (live-only) | DEFERRED-LIVE | Static wiring MET via C2a/C2b + backend-job `prometheus.io/job`→`job` relabel (prometheus.yaml:60-63); live scrape presence is consent-gated | not runnable in this environment |
| INV8: GitOps drift observable — broken manifest → `argocd_app_info` OutOfSync/Degraded within one scrape | requirements INV8 | DEFERRED-LIVE | ArgoCD app-controller scrape wired (C2a, :8082 job argocd); drill requires a live cluster | consent-gated LIVE drill |
| etcd `:2381` data-node→CP-node reachability (security group) | Brief DEFERRED-LIVE | DEFERRED-LIVE | etcd job targets InternalIP:2381 (C4); SG reachability confirmable only live | consent-gated |

### Gaps
- None. All six STATIC contract items MET; the three runtime items are DEFERRED-LIVE (expected, not failures).
