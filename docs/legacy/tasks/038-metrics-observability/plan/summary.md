# Execution Plan — 038 Amendment: KSM, Coverage Expansion & Alert Delivery

## Phase Summary

Strategy: ship the observability layers the handoff's selected list left blind, in the order the
Decisions fix — Prometheus prerequisites FIRST (cluster identity + generic job relabel), then the
metric emitters that depend on the job relabel (KSM, mysqld_exporter, gap-cluster scrape wiring),
then Grafana Cloud alert delivery + rules, and finally the live/infra verifications that can only
prove out after main promotion. Everything is inert on `dev` until main promotion; the live GitOps
branch is `main` (the handoff's dev claim is corrected in phase 06).

Two verification classes carry the whole task (no application test runner applies): local **kustomize
render** assertions (the `local-tdd` phases) and **live drills** (the `infra` / `live-only` phases,
runbook in `mechanics.md` §7).

Cross-cutting considerations (apply to every phase, not owned by one):
- INV11 (no secret in repo): every authoring phase passes a repo-wide grep + review gate before
  commit; secrets reach pods only via Secrets Manager -> ESO -> k8s Secret (`deploy/CLAUDE.md`).
- INV1 (unique `job` per scrape class): the `prometheus.io/job` relabel (phase 01) is the single
  mechanism; every new exporter MUST carry the annotation (`mechanics.md` §2).
- Public-repo rule (`handoff.md:160-163`): no account IDs / instance IDs / internal endpoints in
  committed manifests — registry host and RDS endpoints are injected at sync, not committed.
- Observer placement: all new observers (KSM, mysqld_exporter) run on `role=data` — the settled
  observer home untouched by the surge pipeline (`prometheus.yaml` nodeSelector; handoff gotcha).
- Done When item 9 ("all existing tests pass") is a global gate, not a phase.
- Shared registration files (`deploy/base/kustomization.yaml` edited by 02 & 03; overlay
  kustomizations edited by 01 & 03) take **additive** lines only — parallel authors append their
  own resource/patch entries and reconcile at integration; the shared edit is not a true ordering
  dependency, but authors must not clobber each other's lines.
- `deploy/base/prometheus.yaml` is owned by phase 01 (committed at HEAD b9002587) but phase 04
  ALSO edits it — appending a dedicated apiserver/etcd scrape job. That edit is strictly additive to
  the committed phase-01 config: append to `scrape_configs`; do NOT disturb the `backend`/`prometheus`
  jobs, the `prometheus_io_job` relabel, or the `external_labels` block.

### Spec defect gating (see planner report)
Two named alert deliverables cannot complete within this task's Target, on the spec's own terms:
- **Remote_write not wired:** `requirements.md` Decisions activates rules #7-9 "with step-3 Grafana
  Cloud wiring", yet Done When item 6 asks them "drill-fired once" — an internal contradiction
  (corroborated by the tree: no remote_write overlay patch; `deploy/README.md:72` "when wired").
  Grafana-managed rules evaluate on remote_written data, so DW6's drill-fire, INV2, INV5, INV10, and
  the rule-firing halves of INV4/INV9 have no home here. Phase 09 **authors** the rules (completable);
  drill-fire is deferred to step-3.
- **Cert/skew exporters not deployed:** the cert-expiry and clock-skew alerts (Decisions cluster (4))
  ride on blackbox (§F) and node_exporter (§E) — both handoff step-2, "NOT deployed"
  (`handoff.md:32-33`), absent from `deploy/` (verified), not in this task's Create list. They have no
  metric source and are deferred to step-2 (phase 09 BLOCKED-B).

The Seoul CW-datasource rule (08), delivery + contact-point test (07, INV6), and all local-Prometheus
checks (10) do NOT depend on remote_write or step-2 exporters and remain fully completable.

## Phase Index
| id | Slug | Phase | Kind | External contract (one line) | Depends on | Test Plan items |
|----|------|-------|------|------------------------------|------------|-----------------|
| 01 | prometheus-prereqs | Prometheus prerequisites | local-tdd | Rendered config carries `external_labels.cluster=${CLUSTER_NAME}`, expand-external-labels flag, `prometheus_io_job` relabel, self-scrape job; overlays render region-correct CLUSTER_NAME | none | Render: external_labels + prometheus_io_job relabel (INV1 render half) |
| 02 | kube-state-metrics | kube-state-metrics + etcd snapshot CR | local-tdd | Rendered KSM Deployment: allowlist + scope args, scrape annotations `job=kube-state-metrics`, RBAC (incl. `k3s.cattle.io etcdsnapshotfiles`), ETCDSnapshotFile custom-resource-state config | 01 | Render: KSM allowlist flags present |
| 03 | mysqld-exporter | mysqld_exporter manifest | local-tdd | Rendered Deployment on `role=data`, `job=mysqld`, DSN via ExternalSecret (no literal cred), per-region endpoint patch | 01 | Render: DSN via ExternalSecret, no literal credential (INV11 local half) |
| 04 | gap-scrape-wiring | Gap-cluster scrape wiring | infra | `cp.sh.tftpl` annotates ArgoCD (`:8082`, `argocd`), ESO (`:8080`, `external-secrets`), CoreDNS (`:9153`, `coredns`) + adds `--etcd-expose-metrics=true`; NEW dedicated `prometheus.yaml` scrape job for apiserver (`:6443`, bearer-token) + etcd (`:2381`) | 01 | Drill INV8 (argocd_app_info OutOfSync visible) |
| 05 | deploy-markers | Deploy-marker annotations | live-only | `deploy-fleet.yml` POSTs Grafana annotations API after successful rollout, tagged region + image SHA, token from GH secret | none | (marker appears on next rollout) |
| 06 | handoff-amendment | Handoff amendment | local-tdd | `handoff.md` gains new metric/alert sections (KSM, prereqs, gap clusters, §G) + alerts #7-9/meta/Seoul + dev->main correction | none | (grep: dev->main corrected, sections present) |
| 07 | alert-delivery | Alert delivery pipeline | infra | One Grafana notification policy; Discord (primary) + Slack (fallback) contact points | none | Drill INV6 (test-fire lands in Discord AND Slack) |
| 08 | seoul-replica-alert | Seoul replica silent-zero rule | infra | Grafana CW-datasource rule: RDS `DatabaseConnections==0` (Seoul replica) for 15m, Discord-native | 07 | (deadline 2026-07-27) |
| 09 | grafana-alert-rules | Grafana-managed alert rules | infra | Rules #7-9, staleness meta, argocd/ESO/etcd gap alerts authored in Grafana Cloud on the delivery policy (cert/skew deferred to step-2) | 01, 02, 04, 07 | Drills INV4/INV5/INV9/INV10 (drill-fire BLOCKED — remote_write step-3) |
| 10 | ksm-prereq-live-verify | Post-promotion local metric verify | live-only | Local Prometheus answers allowlisted KSM metrics matching `kubectl`; head-series delta recorded; `up{job=ksm}==1` | 01, 02 | Drill INV4 (Ready flips) + Metric INV3 (head-series delta) |
| 11 | mysqld-live-verify | mysqld_exporter deploy + memory gate | infra | mysqld_exporter `up` both regions; digest + QPS metrics visible; per-instance FreeableMemory gate recorded | 03 | Metric INV7 (FreeableMemory before/after per instance) |

Ids are stable strings; insertions take letter suffixes, remediation continues the sequence.

## Phase Dependencies
Group A (parallel, no deps): 01, 05, 06, 07
Group B (after A): 02 (needs 01), 03 (needs 01), 04 (needs 01), 08 (needs 07)
Group C (after B): 09 (needs 01, 02, 04, 07), 10 (needs 01, 02), 11 (needs 03)

Note: 05, 06, 07 are independent of the manifest chain and can start immediately; their live/infra
verifications gate on external provisioning (§5) and main promotion, not on phase order.
