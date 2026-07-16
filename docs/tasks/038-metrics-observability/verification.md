# Verification Report — 038 Amendment (KSM, Coverage Expansion & Alert Delivery)

## Overall: PASS

Final task-level verification. The task's local scope — kustomize manifest rendering plus
authored operational runbooks — is fully met. Every live/infra item is legitimately
DEFERRED-LIVE (or DEFERRED-STEP2/STEP3 per the two binding spec rulings in `requirements.md`),
carries a named owning runbook, and is not a gap. No UNMET, PARTIAL, or UNTESTABLE items.

Verifier scope note: this pass ALSO statically certifies the two runbooks that deferred their
static certification here — phase 10 (`phases/10-ksm-prereq-live-verify/ledger.md`) and phase 11
(`phases/11-mysqld-live-verify/ledger.md`). Both are internally coherent and every metric name,
secret key, job label, image tag, and endpoint they reference matches the committed manifests
(cross-checked below). Certified.

## Full Suite

Test Plan runner: kustomize render check (no application test runner applies — this task ships
manifests, workflow config, and operational wiring, not application code). `kustomize` binary is
absent on this host; `kubectl kustomize` is the working invocation used by every phase.

```
$ kubectl kustomize deploy/overlays/oregon   → exit 0   (960 lines rendered, no stderr)
$ kubectl kustomize deploy/overlays/seoul    → exit 0   (955 lines rendered, no stderr)
```

Both overlays build cleanly. Render assertions confirmed in output:
- `external_labels.cluster: ${CLUSTER_NAME}` present in the prometheus ConfigMap (oregon L202,
  seoul L206); `--enable-feature=expand-external-labels` deployment arg present (oregon L473,
  seoul L457); `CLUSTER_NAME` env resolves region-correct — oregon → `oregon`, seoul → `seoul`.
- `prometheus_io_job` relabel present (source `__meta_kubernetes_pod_annotation_prometheus_io_job`,
  action replace, target `job`).
- Self-scrape `job_name: prometheus` present, plus dedicated `apiserver` (bearer-token https) and
  `etcd` (`:2381`) jobs (phase-04 additive edit).
- KSM allowlist flag present with all 8 handoff metrics + `kube_etcd_snapshot_creation_timestamp_seconds`;
  `--resources=nodes,pods,daemonsets`; image `registry.k8s.io/kube-state-metrics/kube-state-metrics:v2.19.1`.
- mysqld_exporter DSN via ExternalSecret (`mysqld-exporter-dsn`, keys username/password/primary-endpoint),
  no literal credential in either rendered manifest.

## Static Set

No lint/format/deep-static-analyzer is named for this task — the settled local verification class
is manifest rendering (Test Plan), which the Full Suite above certifies clean over both overlays.

INV11 repo-wide secret scan (whole deploy tree + deploy-fleet.yml + cp.sh.tftpl), patterns:
Slack/Discord webhook URLs, Grafana `glc_` tokens, AWS `AKIA…` keys, 12-digit account IDs / ECR
hosts, inline DSN passwords → **0 matches (clean)**. Secrets reach pods only via ExternalSecret →
Secrets Manager. INV11 (local half) holds.

Two-runbook static certification (deferred here by phases 10 & 11):
- Phase 10 ledger: metric names (`up{job="kube-state-metrics"}`, `kube_node_status_condition`,
  `kube_pod_status_phase`, `kube_etcd_snapshot_creation_timestamp_seconds`) all resolve against
  the shipped KSM allowlist; ordering trap (capture head-series before promotion) and the
  metric-half/firing-half split (firing deferred to step-3) are correctly stated. Coherent — certified.
- Phase 11 ledger: three-separate-string-secret model matches the phase-03 ExternalSecret
  (bare-slash-path `key`, no `property:`); job label `mysqld`, image `prom/mysqld-exporter:v0.19.0`,
  args `--collect.perf_schema.eventsstatements`/`--collect.perf_schema.tableiowaits`, Seoul literal
  endpoint `mysql-replica.seoul.danteplanner.internal:3306` all match the committed manifest;
  grants (PROCESS, REPLICATION CLIENT, SELECT ON performance_schema.*) match mechanics §6; INV7
  memory-gate decision rule authored with primary→escalate / replica→primary-only-fallback. Coherent — certified.

## Benchmark

None named for this task.

## Trace

| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| Overlays render clean; region-correct CLUSTER_NAME; KSM + mysqld + relabel + self-scrape | Done-When 1 / Test Plan | MET | render output oregon L202/L473/L475, seoul L206/L457/L459; `kube-state-metrics.yaml`, `mysqld-exporter.yaml` registered in `deploy/base/kustomization.yaml` | `kubectl kustomize` both overlays exit 0 (Full Suite) |
| mysqld DSN via ExternalSecret, no literal credential | Test Plan / INV11 local | MET | rendered ExternalSecret `mysqld-exporter-dsn` L802-815; env via secretKeyRef L426-439 | literal-cred grep 0 matches |
| handoff.md amended: new sections + alerts #7-9 + dev→main | Done-When 8 | MET | `handoff.md:36` "main branch … live GitOps branch"; §J KSM L144; prereqs L157-160; alerts 7/8/9 L186-188 | grep verified |
| Unique `job` label per scrape class (render half) | INV1 | MET | relabel oregon L221; job annotations kube-state-metrics L363, mysqld L412; jobs apiserver/etcd/argocd/eso/coredns wired | render + cp.sh.tftpl L120-122 |
| No secret in repo | INV11 | MET | secrets only via ExternalSecret/Secrets Manager | repo-wide grep 0 matches (Static Set) |
| Observer placement on `role=data` (KSM, mysqld) | plan Phase Summary | MET | render: kube-state-metrics nodeSelector role:data L392; mysqld-exporter role:data L452 | render inspection |
| Additive shared-registration edits reconciled | plan Phase Summary | MET | base kustomization lists both new resources; overlays list all patches; both render | render exit 0 (no duplicate/clobber) |
| Gap-cluster scrape wiring (ArgoCD/ESO/CoreDNS annotations + apiserver/etcd jobs, --etcd-expose-metrics) | Done-When 4 render half / mechanics §4 | MET | `cp.sh.tftpl:46,120,121,122`; rendered apiserver/etcd jobs oregon L228-252 | render + grep |
| Deploy-marker POST after successful rollout, tagged region+SHA, token from GH secret | Done-When 7 render half / mechanics §4 | MET | `deploy-fleet.yml:296-313`, gated after `wait_cluster` rollout-status both regions; tags `cluster:$cluster`/`sha:$GIT_SHA` | workflow inspection |
| KSM etcd-snapshot CRS config + RBAC (etcdsnapshotfiles) | phase-02 contract / mechanics §4 | MET | rendered CRS config path `[status, creationTime]` L177-190; ClusterRole `k3s.cattle.io etcdsnapshotfiles` L45-47; `--custom-resource-state-config-file` L372 | render inspection |
| All existing tests pass (global gate) | Done-When 9 | MET | no application test runner; render is the suite | both overlays exit 0 |
| `cluster` label {oregon,seoul} on remote-written series | INV2 | DEFERRED-LIVE | external_labels render foundation MET (oregon=oregon/seoul=seoul) | live grouped Grafana query — step-3 remote_write (phase 09 runbook) |
| KSM head-series delta < ~2k per cluster | INV3 / Done-When 3 | DEFERRED-LIVE | allowlist render present | phase 10 ledger STEP 0/2 (before/after per region) |
| Orphaned/dead node detected | INV4 | DEFERRED-LIVE | KSM `kube_node_status_condition` shipped | metric-half phase 10 STEP 4; firing-half phase 09 (DEFERRED-STEP3) |
| Routine deploys never page | INV5 | DEFERRED-LIVE | #7 `for:15m` authored | phase 09 runbook surge drill — step-3 |
| Notification path proven (Discord AND Slack) | INV6 / Done-When 6 | **PASS (live, 2026-07-16)** | drill-fired scratch rule routed through the Default policy; the single firing reached Discord AND Slack (operator-confirmed); scratch rule deleted | executed via deploy/grafana/drill-alert-delivery.sh |
| §G instrumentation within instance memory budgets | INV7 / Done-When 5 | DEFERRED-LIVE | mysqld_exporter shipped (digest+table_io only) | phase 11 ledger STEP 3 (FreeableMemory before/after, primary + replica) |
| GitOps drift observable | INV8 | DEFERRED-LIVE | ArgoCD scrape wiring MET (cp.sh.tftpl) | phase 09/10 drill — firing half step-3 |
| Silently failing etcd snapshot detected | INV9 | DEFERRED-LIVE | CRS metric + G-etcd rule authored | phase 09 runbook; phase 10 STEP 5 runtime confirm |
| Remote_write breakage cannot blind alerting | INV10 | DEFERRED-LIVE | M staleness meta-alert authored on self-scrape `up`+cluster | phase 09 runbook — step-3 |
| Post-promotion KSM metrics match kubectl | Done-When 2 | DEFERRED-LIVE | KSM Deployment shipped | phase 10 ledger STEP 1/3 |
| ArgoCD/ESO scraped under own job labels (live) | Done-When 4 live half | DEFERRED-LIVE | annotations wired (cp.sh.tftpl) | phase 10 (both regions, live) |
| mysqld up + digest/QPS visible (live) | Done-When 5 live half | DEFERRED-LIVE | manifest shipped | phase 11 ledger STEP 4/5/6 |
| Alert rules #7-9 + meta + Seoul CW rule exist, routed, drill-fired | Done-When 6 | DEFERRED-LIVE | rules authored in phase 07/08/09 ledgers; #7 15m/#8 5m/#9 10m consistent | rule creation+routing consent-gated; INV6 drill phase 07/08; rule-eval drills DEFERRED-STEP3 (binding ruling) |
| Deploy marker appears on next rollout (live) | Done-When 7 live half | DEFERRED-LIVE | workflow POST authored | phase 05 (next production rollout) |
| Cert-expiry + clock-skew alerts | Decisions cluster (4) | DEFERRED-STEP2 | blackbox/node_exporter not deployed (handoff step-2) | binding spec ruling: deferred to step-2 with their exporters |
| Rule-evaluation drills (INV4/INV5/INV9/INV10 firing, INV2) | Done-When 6 tail | DEFERRED-STEP3 | rules authored (phase 09) | binding spec ruling: Grafana-managed rules evaluate on remote_written data (step-3) |

## Coverage Audit

Every requirement, Done-When item, and touched mechanics contract traces to implementing code or
an authored runbook owned by a phase (table above). No requirement is un-owned. The two spec
rulings recorded in `requirements.md` (rule-evaluation drills → step-3; cert/skew alerts → step-2)
are honored as DEFERRED, not counted as gaps, per the binding adjudication.

Cross-phase items (plan Phase Summary) all verified first-hand this pass: INV11 repo-wide
(clean grep), INV1 single relabel mechanism (one relabel rule, every exporter carries
`prometheus.io/job`), public-repo rule, observer placement on role=data (KSM + mysqld both),
additive shared-registration edits (base + both overlay kustomizations render without clobber).

Public-repo rule — first-hand evidence (advisor-flagged, now confirmed not inferred): the
RDS-endpoint committability rule is documented at `deploy/CLAUDE.md:11-13` (never commit the
PRIMARY write endpoint — source via Secrets Manager/ExternalSecret; commit a region-local REPLICA
endpoint only as its Route53 private-zone `*.danteplanner.internal` name, only in the overlay that
reads it). Committed-tree endpoint scan (`rds.amazonaws.com` + `.internal`) returns only sanctioned
private-zone names — `mysql-replica.seoul.danteplanner.internal:3306` (Seoul overlay),
`redis-auth.oregon.danteplanner.internal`; zero account-hash `*.rds.amazonaws.com` endpoints
committed. The Seoul literal carries no account ID / instance ID / AWS-assigned hash — substantively
non-sensitive and rule-compliant. Public-repo row MET on first-hand evidence.

Reproducibility note: the render suite was run against the working tree, not the committed HEAD
`bb002182`. The tree carries one uncommitted 038-adjacent change (`deploy/base/spring-daemonset.yaml`,
not a 038 target) plus the pre-existing baseline-dirty set; neither is a render input that alters the
verdict (both overlays build clean), but the record reflects the working tree, not a clean checkout.

## Gaps

None. All non-local items are DEFERRED with a named owning runbook:
- DEFERRED-LIVE INV2/INV3/INV4/INV6/INV7/INV8/INV9/INV10 and Done-When 2-7 live halves — owning
  runbooks: phase 05 (marker), 07/08 (delivery + Seoul rule + INV6 drill), 09 (Grafana rules),
  10 (KSM/head-series live verify), 11 (mysqld deploy + memory gate). All gate on main promotion
  and user-consent-gated operational steps.
- DEFERRED-STEP3 rule-evaluation drills (INV4/INV5/INV9/INV10 firing halves, INV2) — binding
  ruling: require step-3 remote_write; owning runbook phase 09.
- DEFERRED-STEP2 cert-expiry + clock-skew alerts — binding ruling: ride with blackbox/node_exporter
  (handoff step-2, not deployed).
