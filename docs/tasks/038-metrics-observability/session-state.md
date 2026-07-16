# Session State — 2026-07-16 wrap

## Uncommitted / unpushed
- Local commits ahead of origin/dev at wrap time: 972890d5..1282ce23 range partially pushed —
  verify with `git log origin/dev..dev`; push + open/merge the next dev→main PR (carries the
  alloy username_file fix 4356dcc9, kit updates 1282ce23, log_slow_extra 38b3fd7a, pathology
  keep-list 8b735e76, etcd association 841315ec, ops scripts ab83aabf)
- Baseline dirt NOT belonging to this task (leave alone): .claude/*, backend/build.gradle.kts +
  integration test files, scripts/ops/lib/{dashboard,insights,retention}.sh, deploy/base/
  spring-daemonset.yaml, static submodule pointer, docs/RUNBOOK.md, various untracked docs/tasks/*
- requirements.md carries two in-repo spec rulings (rule-drill deferral wording, cert/skew
  deferral) — committed long ago; plan/ dir is untracked task scaffolding (user's call to commit)

## Current focus at wrap
Post-build live wiring complete except: alloy pods CrashLoopBackOff until the username_file fix
merges; WARN/ERROR list panel empty until Loki secrets provisioned + merge.

## Next steps (ordered)
1. `git push origin dev` + merge PR (alloy fix is the urgent passenger — pods crash-looping)
2. `scripts/ops/provision-grafana-logs-secrets.sh` (Loki numeric ID + logs:write token) — before
   or after merge; fresh ExternalSecret reconciles immediately
3. `terraform -chdir=terraform/rds apply` — log_slow_extra (dynamic, no reboot)
4. Re-import dashboard: `deploy/grafana/import-fleet-dashboard.sh` (33-panel version with
   per-cluster percentile columns, pathology panels, node memory, slow-query table)
5. Verify post-merge: `mysql_up` = 1 both regions; alloy pods Running; WARN/ERROR list populates;
   `up{job="node"}` rows per node; series count settles ~5–7k
6. **INV6 delivery drill by 2026-07-27**: `deploy/grafana/drill-alert-delivery.sh`
7. Remaining drills at leisure: INV4 (stop k3s-agent → node-not-ready fires), INV5 (quiet surge),
   INV9 (etcd dead-man threshold-edit drill), INV10 (egress block → rule M from survivor)
8. c2 "after" runs are MOOT for perf_schema (deferred) — rds-memory-gate.sh stays for the next
   memory-costly change
9. Someday: Dependabot 4 criticals; OTel tracing task (Tempo vs Jaeger debate recorded);
   clock-skew alert now unblocked by node_exporter; keep-list coverage check (dashboard queries
   vs regex) as a small CI idea

## Blockers
None hard. Two rulings intentionally parked: percentile columns need no further ruling
(recording-rule aggregates ship them); performance_schema digests deferred until instance upsize
(decision drafted with revisit trigger).
