# Phase 09: Grafana-managed alert rules — PASS

Scope: STATIC certification only. This is an infra, OPERATIONAL-ONLY phase — it authors NO
repo code and NO local tests (mechanics.md §5 provisioning ledger: "operational budget —
nothing here touches the repo"; plan/phase-09.md Files: "none in-repo"). The behavioral
deliverable — seven Grafana-managed alert rules created in Grafana Cloud, their routing to
the phase-07 dual-channel policy, and their firing drills — is entirely USER-executed under
the infra consent boundary. No live Grafana/AWS/kubectl call attempted. Every live/drill item
is classified DEFERRED-LIVE or DEFERRED-STEP3 (with its exact user step), NOT UNTESTABLE — the
phase is operational by design, not a spec defect. Phase closes `authored`, not `done`.
Structure mirrors phase 08's committed verification.md.

### Suite
No project test runner applies (Test Plan: "No project test runner applies — this task ships
manifests, workflow config, and operational wiring, not application code"). The sole
executable static check is the INV11 negative-leak grep over the phase-09 artifact:

```
$ grep -rniE "discord\.com/api/webhooks|hooks\.slack\.com/services|glsa_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|snapshot.interval.*[0-9]{3,}" \
    docs/tasks/038-metrics-observability/phases/09-grafana-alert-rules/
EXIT=1
```

Exit 1, zero matches. The phase authored a single docs artifact (`ledger.md`) and no repo
code; the runbook deliberately keeps the k3s snapshot interval, exporter endpoint label
confirmations, and all Grafana routing as live/user-owned values OUT of the repo (ledger
:161, :200–203). No webhook URL, Slack service hook, Grafana service-account token, AWS
access-key id, or committed snapshot-interval literal appears anywhere in the phase-09
directory → INV11 (negative-leak, local half) MET.

### Trace
| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| INV11 — no secret (webhook URL, DSN, API token, account id, snapshot interval) appears in the phase-09 artifact; runbook keeps all live values out of the repo | INV11 (local half) | MET | Leak grep EXIT 1 / zero matches over `phases/09-grafana-alert-rules/`. Phase authored only `ledger.md` (no repo code — ledger "Nature of phase" :12–26). Snapshot interval a user-pinned placeholder never committed (:137, step 2 :171–176); exporter label confirmations user-owned (:119, :128); routing values user-owned (:161–163, :200–203) | grep evidence in Suite block above |
| #7 Node not Ready — expression / threshold / `for` / datasource / route present + consistent | mechanics §3 row 7 | MET | Ledger :79–85: `kube_node_status_condition{condition="Ready",status="true"} == 0` per node; threshold `== 0`; `for` **15m**; datasource Grafana Cloud hosted Prometheus (:77 blanket, step 1 :165–170); routes via shared policy (§68–74). Metric name confirmed present in KSM allowlist `kube-state-metrics.yaml:87` | Document-completeness review vs Brief contract; metric-name resolution independently confirmed against shipped allowlist |
| #8 Backend DaemonSet unready — expression / threshold / `for` / datasource / route present + consistent | mechanics §3 row 8 | MET | Ledger :87–93: `kube_daemonset_status_number_ready{daemonset="backend"} == 0`; threshold `== 0`; `for` **5m**; Prometheus datasource; shared route. Daemonset name `backend` SETTLED — independently confirmed at `spring-daemonset.yaml:6` (`name: backend`). Metric in KSM allowlist :87 | Document-completeness review; daemonset name + metric name independently confirmed |
| #9 Container stuck waiting — expression / threshold / `for` / datasource / route present + consistent | mechanics §3 row 9 | MET | Ledger :95–101: `kube_pod_container_status_waiting_reason{reason=~"CrashLoopBackOff\|ImagePullBackOff"} == 1`; threshold `== 1`; `for` **10m**; Prometheus datasource; shared route. Markdown `\|` = doc escape, rule uses literal `|` (:148). Metric in KSM allowlist :87 | Document-completeness review; regex alternation validity + metric-name resolution confirmed |
| M Per-cluster staleness meta — absent-detector expression pinned AND creation sequenced with step-3 | mechanics §3 row M / INV10 | MET | Ledger :103–110: `absent_over_time(up{cluster="oregon"}[10m])` + `seoul` twin; threshold `== 1`; short `for`; datasource Prometheus self-scrape `up` carrying `external_labels.cluster` — independently confirmed at `prometheus.yaml:40–41` (`cluster: ${CLUSTER_NAME}`). Sequencing row (:110) + step 3 (:177–180) flag CREATE-WITH-step-3 (absent-detector fires on pre-wiring absence); INV10 surviving-vantage rationale stated | Document-completeness review; `up`/`cluster` series independently confirmed in shipped prometheus.yaml |
| G-argo ArgoCD OutOfSync/Degraded — expression / `for` 30m / job / confirm-at-creation flag present | mechanics §4 ArgoCD row | MET | Ledger :112–119: `argocd_app_info{sync_status="OutOfSync"} == 1` OR `{health_status="Degraded"} == 1`; `for` **30m**; job `argocd` (phase 04); shared route. Confirm-at-creation flag present (:119, step 2 :171–174) — exporter-owned label spellings verified against live `/metrics` (`:8082`) before pinning, correctly NOT resolved in-repo | Document-completeness review; exporter-label deferral is correct-by-design (not a gap) |
| G-eso ESO ExternalSecret not-Ready — expression / sustained `for` / job / confirm-at-creation flag present | mechanics §4 ESO row | MET | Ledger :121–128: `externalsecret_status_condition{condition="Ready",status="False"} == 1` sustained (default ~15m, pin at creation); job `external-secrets` (phase 04); shared route. Confirm-at-creation flag present (:128, step 2) — exporter-owned labels verified against live ESO `/metrics` (`:8080`) before pinning | Document-completeness review; exporter-label deferral correct-by-design |
| G-etcd snapshot dead-man — expression / threshold / per-cluster / KSM-primary realization / placeholder present | mechanics §4 etcd row | MET | Ledger :130–137: `(time() - kube_etcd_snapshot_creation_timestamp_seconds) > (1.5 * <snapshot_interval_seconds>)` per cluster; threshold last snapshot older than 1.5× interval; short `for`; shipped realization of §4 PRIMARY route (KSM allowlist, NO S3 fallback) — `kube_etcd_snapshot_creation_timestamp_seconds` independently confirmed at `kube-state-metrics.yaml:87`. `<snapshot_interval_seconds>` a user-pinned live placeholder (INV11, never committed) | Document-completeness review; metric-name resolution independently confirmed against shipped allowlist |
| Shared routing — Default policy → `alert-dual-channel` stated ONCE, applied to all seven; no lone match-all child | phase-07 INV6 | MET | Ledger "Common delivery contract" (:68–74): every rule routes to the phase-07 Default notification policy → `alert-dual-channel` (Discord primary + Slack fallback, redundancy not failover); explicit "Do NOT add a lone match-all nested child … delivers one-channel-only and FAILS INV6"; folder/group override-resolution caveat present. Re-applied in step 4 (:181–183) | Document-completeness review; single statement applied to all seven, INV6 trap honored |
| Datasource + No-Data honesty — all seven query hosted Prometheus (not CloudWatch); six threshold rules → non-paging No-Data; M created with step-3 | mechanics §5 / BLOCKED-A | MET | Ledger "Query-datasource + No-Data reality" (:51–66): all seven on Grafana Cloud hosted Prometheus (NOT CloudWatch, that was phase-08 rule S); remote_write UNWIRED (BLOCKED-A) → matched series absent → six threshold rules sit in **No Data**, handling set to a **non-paging state** (Keep Last State / OK, NOT Alerting; steps 1–2 :168, :176); M absent-detector created WITH step-3 so absence is a real signal. Boundary is what makes `authored` honest | Document-completeness review; datasource + No-Data boundary explicit |
| PromQL static check — recorded honestly (promtool ABSENT stated) + manual structural validation with metric names resolving against shipped series | Test Plan / mechanics | MET | Ledger "PromQL static sanity-check" (:139–159): promtool/prometheus/docker-image ABSENT stated (not silently skipped); manual check records brace/paren/quote balance, operator/regex validity, label-matcher shape, and metric-name resolution against the SHIPPED KSM allowlist (`kube-state-metrics.yaml:87`) + phase-01 `up`/`cluster`. All four KSM names + `up`/`cluster` independently re-confirmed present; exporter-owned `argocd_app_info`/`externalsecret_status_condition` correctly flagged confirm-at-creation. Semantic/type validity DEFERRED-LIVE (needs remote_written TSDB) | Document-completeness review; metric-name resolution independently confirmed |
| Scope exclusions — CP/etcd-health scrape, deploy markers, CoreDNS, cert-expiry, clock-skew excluded WITH reasons | plan §/requirements ruling | MET | Ledger "Scope filter" (:37–49): CP/etcd-health scrape, deploy markers, CoreDNS excluded as scrape/wiring owned by phases 04/05, not alerts; cert-expiry (`probe_ssl_earliest_cert_expiry`) + clock-skew (`node_timex_offset_seconds`) BLOCKED-B — exporters (blackbox §F, node_exporter §E) are handoff step-2, NOT deployed (`handoff.md:32–33`), no metric seam. Correctly deferred, not UNMET | Document-completeness review; exclusions present with binding-spec reasons |

### Gaps
- DEFERRED-STEP3 INV4 (#7 rule-firing half): stop a disposable app node's k3s agent; `#7`
  fires after 15m. Requires remote_written data (BLOCKED-A). Ledger :188.
- DEFERRED-STEP3 INV5 (#7 surge silence): observe a full surge (1→2→1) with rules active;
  ZERO firings from `#7`. Requires remote_written data. Ledger :189.
- DEFERRED-STEP3 INV9 (G-etcd firing): suspend the snapshot schedule; `G-etcd` fires past
  1.5× interval. Requires remote_written data + user-pinned live interval. Ledger :190–191.
- DEFERRED-STEP3 INV10 (M firing): block one region's Prometheus egress; `M` fires from the
  surviving vantage. Gated on M's own step-3 creation. Ledger :192–193.
- DEFERRED-STEP3 INV8 (G-argo firing half): the `argocd_app_info` observability half is
  scrape-side (phase 04); the `G-argo` 30m-sustained → page leg needs remote_written argocd
  series. Ledger :194–196. (INV6 contact-point delivery was drill-defined in phase 07/08 and
  is NOT re-opened here — ledger :197–198.)
- DEFERRED-LIVE rule creation + routing (runbook steps 1–4, ledger :165–183): create #7/#8/#9
  and the three gap rules on the hosted-Prometheus datasource with tabled expressions/`for`/
  thresholds and non-paging No-Data; confirm exporter labels at creation for G-argo/G-eso;
  substitute the live k3s snapshot interval for G-etcd (kept out of repo); create M with
  step-3; route every rule to `alert-dual-channel`. Live, consent-gated.

All statically-certifiable items — INV11 (negative-leak local half), the seven rules'
completeness + internal consistency (expression / threshold / `for` / datasource / routing),
the shared dual-channel routing stated once, the datasource + No-Data honesty boundary, the
PromQL manual structural check with every KSM/self-scrape metric name resolving against the
shipped allowlist and prometheus.yaml, and the scope exclusions — are MET. The only open
items are DEFERRED-STEP3 (rule-evaluation firing drills, gated on remote_write / BLOCKED-A)
and DEFERRED-LIVE (rule creation + routing, consent-gated) by design — this is an
operational-only phase closing `authored`, not a defect. Verdict: PASS.
