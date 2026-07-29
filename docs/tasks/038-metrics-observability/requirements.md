# Task: 038 Amendment — Kubernetes Object-State Metrics, Coverage Expansion & Alert Delivery

Archives the design debate of the 2026-07-15 session, amending the settled 038 design in
`handoff.md` (2026-07-13). Where this document and `handoff.md` disagree, this document wins;
everything in `handoff.md` not amended here remains binding. Implementation-grade mechanics live
in `mechanics.md` — read it before building any scrape wiring, alert rule, exporter, or
provisioning step. The `docs/spec.md` Data-Driven Features sections are N/A: this task consumes
no raw game data. Dashboard composition is NOT in scope — it remains `handoff.md` step-3
territory, unchanged.

## Decisions

- Add a Kubernetes object-state layer via kube-state-metrics (KSM), one per cluster, restricted
  to an incident-driven metric allowlist — Pod/Node conditions exist only in the apiserver
  (node_exporter and kubelet metrics cannot see them), and the fleet has a documented unwatched
  gap: no cloud-controller-manager, so orphaned NotReady nodes are never garbage-collected
  (evidence: `docs/tasks/034-multi-region-k8s-architecture/phase-15-notes.md` deviation #1;
  node deletion in the deploy pipeline is best-effort — `.github/workflows/deploy-fleet.yml:354`
  swallows failure with `|| true`; allowlist per the cardinality discipline in
  `handoff.md:167-168`; scope confirmed by user).
- Two Prometheus prerequisites ride along and ship FIRST: (a) a `cluster` external label per
  region — without it, remote_write (step 3) merges Oregon and Seoul into one indistinguishable
  stream (evidence: `deploy/base/prometheus.yaml` global block has no `external_labels`);
  (b) annotation-driven job identity — today every annotated pod lands under `job="backend"`
  (`prometheus.yaml:39-55`), which poisons the planned `absent(up{job=...})` dead-man alerts
  (conflict resolved: a generic `prometheus.io/job` relabel wins over per-exporter static jobs,
  because 038 step 2 adds 3+ exporters and the base ConfigMap must not grow per exporter).
- Alert rules #7–9 join the handoff's minimum alert set now, activated with step-3 Grafana Cloud
  wiring (user answer): Node not Ready 15m, backend DaemonSet ready==0 5m, container waiting on
  CrashLoopBackOff/ImagePullBackOff 10m. The 15m `for:` on #7 outlasts a routine surge window so
  deploys do not page.
- Alert evaluation happens in Grafana Cloud, never in-cluster — an alert engine inside the
  region it monitors dies with that region (evidence: `deploy/base/prometheus.yaml:1-2`
  "survives Oregon's death"; no Alertmanager pods on 2GiB nodes).
- Decision (taste): notifications route to Discord (primary contact point) with Slack as the
  fallback on the same Grafana notification policy — a single webhook channel is itself a silent
  point of failure: a revoked webhook or provider outage drops firing alerts with no symptom, so
  the primary contact point is always backed by an independent second channel (user answer).
- Discord scope is Grafana-evaluated alerts only. CW-native alarms (billing, EC2 auto-recovery)
  stay on SNS→email until §A absorbs them — no SNS→Lambda→Discord shim gets built for plumbing
  already scheduled for demolition (user answer). Existing CW alarms remain governed by the
  setup-cloudwatch workflow and are NOT mutated by this task (prior decision honored:
  meme `LimbusPlanner/decision-in-limbusplanner-alarm-mutations-adding-changing`).
- The deadline-bound Seoul replica alert (`DatabaseConnections == 0`, due 2026-07-27) is born as
  a Grafana CloudWatch-datasource rule, Discord-native — the CW datasource needs only the
  Grafana stack plus read-only AWS credentials, not remote_write (evidence: `handoff.md:138-139`).
- The live GitOps branch is `main`; the handoff's "deploys ride dev" line is wrong and must be
  corrected in this task (user answer). Everything here is inert on `dev` until main promotion.
- Four coverage-gap clusters join the selected metrics (user answer) — the unifying rationale:
  control planes fail silent, data planes fail loud. (1) GitOps plane: ArgoCD sync/health +
  ESO sync failures — the secrets path is a fail-open security surface (`deploy/CLAUDE.md`).
  (2) CP/etcd health + a dead-man on the S3 etcd snapshots — the only CP restore path
  (`terraform/modules/fleet/s3.tf:1-6`). (3) Alert-pipeline self-monitoring: Prometheus
  self-scrape + per-cluster staleness meta-alert, so remote_write breakage cannot blind alerting
  silently. (4) Deploy markers (Grafana annotations from the deploy workflow — both motivating
  postmortems were deploy-window incidents) + free riders on already-selected exporters
  (blackbox cert expiry, node_exporter clock skew, CoreDNS scrape). The cert-expiry and
  clock-skew alerts ride with their exporters, which are handoff step-2 and not yet deployed —
  both alerts are deferred to step-2; only the CoreDNS scrape lands in this task.
- Decision (taste): §G query telemetry (QPS, digests, slow queries, skew) runs on BOTH RDS
  instances, memory-gated per instance — observability must follow the read path, not the data:
  in a read-local replicated topology, query telemetry must run on every instance that serves
  reads, because primary-only telemetry structurally cannot see replica-only pathologies
  (user answer). Replica gate failure falls back to primary-only as a recorded blind spot.
- §G mechanism settled: mysqld_exporter per region pointing at its region-local endpoint —
  app-side perf_schema polling was rejected (couples the backend to monitoring and dies with
  every surge redeploy); RDS Performance Insights stays rejected pending the handoff's
  instance-class check (evidence: `handoff.md:107-110`).
- EXPLAIN ANALYZE is dropped from the design — it is an on-demand drill, not a recorded metric;
  §G digests are the census that finds the query, EXPLAIN ANALYZE the targeted drill that finds
  the plan, mirroring §C's jcmd-census/JFR-drill split (user correction). Query counts were
  already covered: QPS via exporter global status, per-query via digest `COUNT_STAR`.
- Rejected: EBS BurstBalance monitoring (all fleet volumes are gp3 — no burst credits exist;
  evidence: `terraform/modules/fleet/*.tf` `volume_type`); GA/CloudFlare edge exporters
  (blackbox §F probes + per-region Traefik request-share derivation suffice); direct ECR
  replication-lag polling (the `waiting_reason` metric catches the ImagePullBackOff symptom);
  product analytics (outside the incident-driven charter, `handoff.md:20-22`).
- (default) KSM image from `registry.k8s.io`, pinned to the current stable v2 minor at
  implementation time — public registry satisfies the no-account-ID rule (`handoff.md:160-163`).
- (default) All new observers (KSM, mysqld_exporter) run on the `role=data` node — the settled
  observer home, untouched by the surge pipeline (`prometheus.yaml` nodeSelector; handoff gotcha).

## Description

Extend task 038 with the layers its selected list left blind:

1. **Kubernetes object-state metrics** — KSM per cluster exposing the allowlisted node/pod/
   daemonset state metrics (exact list in `mechanics.md` §1), scraped by the region-local
   Prometheus.
2. **Prometheus prerequisites** — per-region `cluster` external label and the generic
   `prometheus.io/job` annotation relabel, plus a Prometheus self-scrape job.
3. **Alert set expansion** — rules #7–9 defined alongside the handoff's six, the staleness
   meta-alert, and the Seoul replica rule as a Grafana CW-datasource rule; delivery via Discord
   (primary) + Slack (fallback) contact points on one notification policy.
4. **Coverage-gap clusters** — ArgoCD sync/health, ESO sync errors, CP/etcd health + etcd
   snapshot dead-man, deploy markers, and the free riders (cert expiry, clock skew, CoreDNS).
5. **§G refinement** — mysqld_exporter per region against the region-local RDS endpoint, both
   instances instrumented behind per-instance memory gates.
6. **Handoff amendment** — fold the above into `handoff.md`'s selected-metrics and alert
   sections and correct the dev→main branch claim.

## Scope

Read for context:
- `deploy/CLAUDE.md` — manifest constraints, secrets flow, base/overlay rules
- `deploy/base/prometheus.yaml` — scrape config being amended; RBAC/SA pattern to copy
- `deploy/base/kustomization.yaml` + `deploy/overlays/{oregon,seoul}/kustomization.yaml`
- `deploy/base/spring-daemonset.yaml` — the annotation contract exemplar
- `terraform/modules/fleet/user-data/cp.sh.tftpl` — CP bootstrap; ArgoCD/ESO install + patch loop
- `.github/workflows/deploy-fleet.yml` — surge pipeline, node deletion, marker insertion point
- `docs/tasks/038-metrics-observability/handoff.md` — the base design this amends
- `docs/tasks/034-multi-region-k8s-architecture/phase-15-notes.md` — CCM gap (deviation #1)
- `terraform/modules/fleet/s3.tf` — etcd snapshot restore path

## Target

Create:
- `deploy/base/kube-state-metrics.yaml` — SA, ClusterRole, Binding, Deployment, Service
- `deploy/base/mysqld-exporter.yaml` — Deployment + ExternalSecret for the DSN
- `deploy/overlays/{oregon,seoul}/prometheus-cluster-patch.yaml` — `CLUSTER_NAME` env patch
- Per-region mysqld_exporter endpoint config (overlay patch)

Modify:
- `deploy/base/prometheus.yaml` — external_labels, expand-external-labels flag, job relabel,
  self-scrape job
- `deploy/base/kustomization.yaml` + both overlay kustomizations — register new resources/patches
- `terraform/modules/fleet/user-data/cp.sh.tftpl` — scrape annotations for ArgoCD
  application-controller and ESO controller (existing patch-loop pattern)
- `.github/workflows/deploy-fleet.yml` — deploy-marker POST to the Grafana annotations API
- `docs/tasks/038-metrics-observability/handoff.md` — selected-metrics/alerts sections + branch
  correction

Manual (operational, never in-repo — owners and timing in `mechanics.md` §5):
- Discord + Slack webhooks; Grafana Cloud contact points, notification policy, alert rules,
  CW datasource credentials; Grafana API token as a GitHub Actions secret; RDS monitoring user
  + DSN in Secrets Manager.

## Invariants

- INV1: Every scrape-target class carries a unique `job` label — verify: rendered-manifest
  inspection (relabel rule present) + live `up{job="kube-state-metrics"} == 1` per region.
- INV2: Every remote-written series carries a `cluster` label with exactly the values
  {oregon, seoul} — verify: live grouped query in Grafana Cloud after step-3 wiring.
- INV3: The KSM allowlist holds — verify: metric — `prometheus_tsdb_head_series` delta after
  rollout stays under ~2k per cluster, recorded before/after.
- INV4: An orphaned or dead node is detected — verify: drill — stop the k3s agent on a
  disposable app node; the Ready condition flips within one scrape and rule #7 fires after 15m.
- INV5: Routine deploys never page — verify: drill — observe one full surge (1→2→1) with rules
  active; zero firings.
- INV6: The notification path is proven, not assumed — verify: drill — test-fire a rule; a
  message lands in the Discord channel AND the Slack channel.
- INV7: §G instrumentation stays within instance memory budgets — verify: metric — RDS
  `FreeableMemory` before/after enabling instruments, on primary and replica independently.
- INV8: GitOps drift is observable — verify: drill — push a deliberately broken manifest to a
  scratch app; `OutOfSync`/`Degraded` visible in `argocd_app_info` within one scrape.
- INV9: A silently failing etcd snapshot schedule is detected — verify: drill — suspend the
  snapshot schedule in a test window; the dead-man fires past the threshold.
- INV10: Remote_write breakage cannot blind alerting silently — verify: drill — block Prometheus
  egress in one region; the Grafana-side staleness meta-alert fires from the surviving vantage.
- INV11: No secret (webhook URL, DSN, API token, account ID) ever appears in the repo — verify:
  review gate + repo-wide grep before commit.

## Done When

- [ ] `kustomize build` renders both overlays with region-correct `CLUSTER_NAME`, the KSM and
      mysqld_exporter resources, the job relabel, and the self-scrape job (local-tdd)
- [ ] After main promotion, each region's Prometheus answers the allowlisted KSM metrics with
      values matching `kubectl get nodes/pods` (live-only)
- [ ] `prometheus_tsdb_head_series` delta measured and recorded per cluster (live-only)
- [ ] ArgoCD and ESO metrics scraped under their own job labels in both regions (live-only)
- [ ] mysqld_exporter up in both regions; digest and QPS metrics visible; per-instance
      FreeableMemory gate results recorded (infra)
- [ ] Alert rules #7–9, the staleness meta-alert, and the Seoul replica CW-datasource rule
      exist in Grafana Cloud, routed Discord-primary/Slack-fallback; the delivery path is
      drill-fired once via contact-point test (INV6); rule-evaluation drills are deferred to
      step-3 remote_write wiring, which Grafana-managed rules require (infra)
- [ ] A deploy marker annotation appears in Grafana on the next production rollout (live-only)
- [ ] `handoff.md` amended: new sections, alerts #7–9, dev→main correction (local-tdd)
- [ ] All existing tests pass

## Test Plan

> No project test runner applies — this task ships manifests, workflow config, and operational
> wiring, not application code. The local verification class settled in the debate is manifest
> rendering; behavioral verification is drills (declared per invariant above).

### Test Runner
- Framework: kustomize render check (no unit framework applies)
- Run command: `kustomize build deploy/overlays/oregon` and `kustomize build deploy/overlays/seoul`
  (`kubectl kustomize` as the fallback binary)

### Tests to Write
- [ ] Render assertion: both overlays build cleanly; output contains `external_labels` with the
      region-correct cluster value, the `prometheus_io_job` relabel, and the KSM allowlist flags
- [ ] Render assertion: mysqld_exporter DSN arrives via ExternalSecret — no literal credential
      in any rendered manifest (INV11, local half)
- [ ] Drills INV4–INV6, INV8–INV10 executed per the runbook steps in `mechanics.md` §7 and their
      outcomes recorded in the phase verification doc
- [ ] Metrics INV3 and INV7 measured before/after and recorded — every invariant above is
      realized here; none ships unverified
