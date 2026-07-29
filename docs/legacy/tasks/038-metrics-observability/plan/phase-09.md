# Phase 09: Grafana-managed alert rules

- Kind: infra (Grafana Cloud provisioning; consent-gated; drill-verified — see BLOCKED note)
- Files: none in-repo (operational — Grafana-managed alert rules; §5)
- Tests: none local
- External contract: the following rules exist in Grafana Cloud, evaluated in Grafana Cloud, routed to
  the phase-07 dual-channel policy. All ride on metrics whose series exist in this task's local
  Prometheus (KSM from 02; argocd/ESO/etcd from 04; self-scrape from 01):
  - #7 Node not Ready: `kube_node_status_condition{condition="Ready",status="true"} == 0` per node, `for: 15m`
  - #8 Backend DaemonSet unready: `kube_daemonset_status_number_ready{daemonset="backend"} == 0`, `for: 5m`
  - #9 Container stuck waiting: `kube_pod_container_status_waiting_reason{reason=~"CrashLoopBackOff|ImagePullBackOff"} == 1`, `for: 10m`
  - M Staleness meta: Grafana-side no-recent-data on `up{cluster="<each>"}`, ~10m
  - Gap-cluster alerts: `argocd_app_info` OutOfSync/Degraded 30m; ESO ExternalSecret not-Ready
    sustained; etcd-snapshot dead-man past 1.5× interval.
- Mechanics sections: `mechanics.md` §3 (rows 7–9, M); `mechanics.md` §4 (gap-cluster alert rows).
- Implementation methods: none in-repo. Rule #8 daemonset name resolves to `backend`
  (`deploy/base/spring-daemonset.yaml:6`) — the mechanics "verify at impl" is settled.
- Considerations:
  - `requirements.md` Decisions (alerts #7–9): the 15m `for:` on #7 outlasts a routine surge window
    (taint→drain→scale-in→node-delete) so deploys do not page (INV5); #8 is control-plane truth
    complementing the `absent(up)` dead-man; #9 tolerates transient pull retries / Seoul ECR lag.
  - `requirements.md` Decisions (evaluation in Grafana Cloud): an alert engine inside the region it
    monitors dies with that region — never in-cluster.
  - **BLOCKED-A — remote_write not wired (spec defect):** Rules #7–9, M, and the gap alerts are
    Grafana-managed and evaluate on remote_written data, but remote_write is out of this task's Target
    — the Decisions line activates rules #7–9 "with step-3 Grafana Cloud wiring", and DW6's
    "drill-fired once" contradicts that same deferral. So DW6's drill-fire, INV5, INV10, and the
    rule-firing halves of INV4/INV9 cannot complete here. This phase AUTHORS the rules; drill-fire is
    deferred to step-3.
  - **BLOCKED-B — cert/skew exporters not deployed (spec defect):** the cert-expiry
    (`probe_ssl_earliest_cert_expiry`, rider on §F blackbox) and clock-skew (`node_timex_offset_seconds`,
    rider on §E node_exporter) alerts named in `requirements.md` Decisions cluster (4) have NO metric
    source — blackbox and node_exporter are handoff step-2, "NOT deployed" (`handoff.md:32-33`),
    absent from `deploy/` (verified), and not in this task's Create list. These two alerts cannot even
    be authored against a real seam here; deferred to step-2 exporter deployment.
- Depends on: 07 (delivery), 01 (self-scrape for meta + cluster label), 02 (KSM metrics for #7–9),
  04 (argocd/ESO/etcd scrape for gap alerts)
- Verify (completable): rules #7–9, M, argocd/ESO/etcd alerts exist in Grafana Cloud, routed to the
  dual-channel policy, thresholds as specified. Drill-fire (INV4 rule half / INV5 / INV9 alert /
  INV10) is BLOCKED on step-3 remote_write; cert/skew alerts BLOCKED on step-2 exporters — flag both,
  do not invent the wiring.
