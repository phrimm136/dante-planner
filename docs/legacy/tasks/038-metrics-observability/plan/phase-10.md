# Phase 10: Post-promotion local metric verification

- Kind: live-only (verification against each region's local Prometheus after main promotion)
- Files: none in-repo (records outcomes in the phase verification doc)
- Tests: drills/metrics below (no local suite)
- External contract: after main promotion, each region's local Prometheus answers the allowlisted KSM
  metrics with values matching `kubectl get nodes/pods`; `up{job="kube-state-metrics"} == 1` per
  region; the KSM head-series delta stays within budget.
- Mechanics sections: `mechanics.md` §1 (allowlist / cardinality budget); `mechanics.md` §7 step 1
  (orphan-node drill).
- Implementation methods: none in-repo.
- Considerations:
  - `requirements.md` INV3: `prometheus_tsdb_head_series` delta after rollout stays under ~2k per
    cluster — record before/after.
  - `requirements.md` INV1 (live half): `up{job="kube-state-metrics"} == 1` confirms the phase-01
    relabel gave KSM its own job label.
  - `requirements.md` INV4 (metric half): stopping `k3s-agent` on a disposable app node flips the
    Ready condition within one 30s scrape (visible locally); the rule-#7 firing half is Grafana-side
    and BLOCKED on remote_write (phase 09 / spec defect).
  - These checks use the region-local Prometheus and do NOT depend on remote_write — fully completable.
- Depends on: 01, 02 (+ main promotion)
- Verify: Done When items 2 and 3 — KSM metrics match `kubectl`; head-series delta recorded per
  cluster; `up{job=ksm}==1`; Ready-flip observed within one scrape. Record in the phase verification doc.
