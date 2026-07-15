# Mechanics — 038 Amendment

Companion to `requirements.md`. Transcribed from the 2026-07-15 design session. Everything here
is **binding contract** unless a row is marked *reference*; derivations and failure walkthroughs
live in the session record, not here.

## 1. kube-state-metrics contract

One Deployment per cluster (arrives via `deploy/base`, so both regions get it identically).

| Aspect | Contract |
|---|---|
| Image | `registry.k8s.io/kube-state-metrics/kube-state-metrics:<current stable v2 minor, pinned at implementation>` |
| Placement | `nodeSelector: role: data`; single replica |
| Resources | requests `10m` / `32Mi`, limit `64Mi` |
| Scope args | `--resources=nodes,pods,daemonsets` |
| Allowlist (`--metric-allowlist`) | `kube_node_status_condition`, `kube_node_spec_taint`, `kube_pod_status_phase`, `kube_pod_status_ready`, `kube_pod_container_status_restarts_total`, `kube_pod_container_status_waiting_reason`, `kube_daemonset_status_desired_number_scheduled`, `kube_daemonset_status_number_ready` |
| Scrape wiring | Pod annotations `prometheus.io/scrape: "true"`, `prometheus.io/port: "8080"`, `prometheus.io/job: "kube-state-metrics"` |
| RBAC | Own SA + ClusterRole (`list`,`watch` on nodes, pods, daemonsets) + Binding — copy the block shape from `deploy/base/prometheus.yaml:3-28`; extend with the `k3s.cattle.io` `etcdsnapshotfiles` CR if §4's primary route is taken |

Cardinality budget: ≤ ~2k head series per cluster (INV3). All namespaces — the cluster is small
enough that namespace filtering buys nothing.

## 2. Prometheus config amendments (`deploy/base/prometheus.yaml`)

**Cluster identity** — base ConfigMap gains:

```yaml
global:
  external_labels:
    cluster: ${CLUSTER_NAME}
```

Deployment args gain `--enable-feature=expand-external-labels`. Each overlay patches one env var
on the Deployment (`deploy/overlays/<region>/prometheus-cluster-patch.yaml`):

| Overlay | `CLUSTER_NAME` |
|---|---|
| oregon | `oregon` |
| seoul | `seoul` |

Rationale (*reference*): strategic-merge cannot deep-merge inside the `prometheus.yml` string;
duplicating the whole config per overlay was rejected for drift risk.

**Job identity** — one relabel appended to the existing pod-discovery job:

```yaml
- source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_job]
  action: replace
  target_label: job
  regex: (.+)
```

Pods without the annotation keep the job default (backend today) — every NEW exporter MUST carry
`prometheus.io/job`. This rule ships before or with the first non-backend scrape target.

**Self-scrape** — a static job `prometheus` against `localhost:9090`, feeding the staleness
meta-alert (§3).

## 3. Alert ledger (extends handoff alerts 1–6)

All rules are **Grafana-managed**, evaluated in Grafana Cloud, created at handoff step 3 —
except the Seoul replica rule, which is created as soon as the CW datasource exists (deadline
2026-07-27, independent of remote_write).

| # | Signal | Expression sketch | `for` | Why this threshold |
|---|---|---|---|---|
| 7 | Node not Ready | `kube_node_status_condition{condition="Ready",status="true"} == 0` per node | 15m | Outlasts a routine surge window (taint→drain→scale-in→node delete); catches orphans and kubelet death |
| 8 | Backend DaemonSet unready | `kube_daemonset_status_number_ready{daemonset="<backend name — verify at impl>"} == 0` | 5m | Control-plane truth complementing the scrape-truth `absent(up)` dead-man |
| 9 | Container stuck waiting | `kube_pod_container_status_waiting_reason{reason=~"CrashLoopBackOff\|ImagePullBackOff"} == 1` | 10m | Tolerates transient pull retries; catches Seoul ECR replication lag stalling a rollout |
| M | Cluster gone stale (meta) | Grafana-side no-recent-data on `up{cluster="<each>"}` | ~10m | Remote_write breakage or region egress death must fire from the surviving vantage |
| S | Seoul replica silent-zero | CW datasource: RDS `DatabaseConnections == 0` (Seoul replica) | 15m | Postmortem action item; Discord-native from birth |

**Delivery contract**: one notification policy; contact points Discord webhook (primary) and
Slack incoming webhook (fallback (taste — a single webhook channel is a silent point of
failure; always back the primary with an independent second channel)). CW-native alarms
(billing, EC2 auto-recovery) stay SNS→email until §A migrates them; the setup-cloudwatch
workflow remains their only mutation path.

## 4. Gap-cluster mechanics

| Cluster | Contract |
|---|---|
| ArgoCD | Scrape application-controller metrics (`:8082`) via `prometheus.io/*` annotations added in the `cp.sh.tftpl` patch loop (pattern at `terraform/modules/fleet/user-data/cp.sh.tftpl:79-80`); idempotent — apply once manually to live CPs, converges on rebuild. Job label `argocd`. Alert (step 3): `argocd_app_info` OutOfSync/Degraded sustained 30m |
| ESO | Same annotation-patch route on the ESO controller (`:8080`); job label `external-secrets`. Alert: any ExternalSecret not Ready sustained |
| etcd snapshot dead-man | **Primary route**: `ETCDSnapshotFile` CRs via KSM custom-resource-state config — requires k3s ≥ 1.27 (**verify pinned version at implementation**). **Fallback**: S3 object-age probe on the snapshots bucket. Alert past 1.5× the snapshot interval |
| CP/etcd health | Scrape k3s server metrics for apiserver request latency + etcd fsync; wiring detail settled at implementation within the annotation/job conventions above |
| Deploy markers | `deploy-fleet.yml` POSTs to the Grafana annotations HTTP API after a successful rollout, tagged region + image SHA. Token from a GitHub Actions secret |
| Cert expiry (rider on §F) | Record `probe_ssl_earliest_cert_expiry` from blackbox; alert < 30d (default) |
| Clock skew (rider on §E) | `node_timex_offset_seconds` panel + alert (threshold default at impl) — JWT `exp` validation and §H trace skew both care |
| CoreDNS | **Verify first**: k3s's bundled CoreDNS may already carry scrape annotations and be silently swept into `job="backend"` today — one more reason §2's relabel ships first. Assign job `coredns` via bootstrap patch |

## 5. Provisioning ledger (operational budget — nothing here touches the repo)

| Item | Owner | Lives in | Needed before |
|---|---|---|---|
| Discord webhook URL | user | Grafana Cloud contact point only | step-3 alert wiring |
| Slack incoming-webhook URL | user | Grafana Cloud contact point only | step-3 alert wiring |
| Grafana Cloud API token (markers) | user | GitHub Actions secret | deploy-marker workflow change |
| Grafana CW-datasource AWS read credentials | user | Grafana Cloud datasource config | Seoul replica rule (by 2026-07-27) |
| RDS monitoring user + DSN | user (create on primary; replicates to Seoul) | Secrets Manager → ESO → k8s Secret | mysqld_exporter deploy |

## 6. mysqld_exporter contract (§G)

| Aspect | Contract |
|---|---|
| Topology | One Deployment per region on `role=data`; Oregon → primary endpoint, Seoul → Seoul replica endpoint (read-local symmetry) |
| Grants | `PROCESS`, `REPLICATION CLIENT`, `SELECT ON performance_schema.*` — read-only monitoring user |
| DSN delivery | Secrets Manager → ESO → k8s Secret (`deploy/CLAUDE.md` secrets rule; never in-repo) |
| perf_schema scope | digest + table_io instruments only (`handoff.md:107-110` memory constraint) |
| Memory gate | RDS `FreeableMemory` before/after enabling instruments, per instance; replica gate failure → primary-only fallback, recorded as a known blind spot (taste — observability must follow the read path: query telemetry runs on every instance that serves reads; primary-only telemetry structurally cannot see replica-only pathologies) |
| Job labels | `mysqld` (with the `cluster` external label separating regions) |

## 7. Drill runbook index (verification for INV4–INV6, INV8–INV10)

1. **Orphan node (INV4)**: stop `k3s-agent` on a disposable app node → Ready condition flips
   within one 30s scrape → rule #7 fires after 15m → clean up by deleting the node object.
2. **Surge silence (INV5)**: run one production deploy end-to-end with rules active; assert
   zero firings.
3. **Notification (INV6)**: Grafana contact-point Test on both channels, then one real
   drill-fired rule; a message must land in Discord AND Slack.
4. **GitOps drift (INV8)**: point a scratch Application at a broken manifest → OutOfSync/
   Degraded in `argocd_app_info` within one scrape.
5. **Snapshot dead-man (INV9)**: suspend the etcd snapshot schedule in a test window → alert
   past threshold → restore schedule.
6. **Staleness meta-alert (INV10)**: block Prometheus egress in one region (SG rule) → Grafana
   no-data alert fires for that `cluster` label → restore.
