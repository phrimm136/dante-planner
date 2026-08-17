# Phase 11: mysqld_exporter deploy + memory gate

- Kind: infra (GitOps-synced deploy after main promotion; consent-gated operational provisioning;
  metric-verified)
- Files: none new in-repo (the manifest is phase 03; this phase is the live deploy + gate + the
  operational RDS monitoring-user/DSN provisioning per §5)
- Tests: metric INV7 below (no local suite)
- External contract: mysqld_exporter reports `up == 1` in both regions; digest metrics
  (`events_statements_summary_by_digest`) and QPS (`mysql_global_status_*`) are visible; the
  per-instance `FreeableMemory` gate result is recorded.
- Mechanics sections: `mechanics.md` §6 (grants, perf_schema scope, memory gate); `mechanics.md` §5
  (RDS monitoring user + DSN).
- Implementation methods: none new in-repo.
- Considerations:
  - `requirements.md` INV7 / `mechanics.md` §6 memory gate: measure RDS `FreeableMemory` before/after
    enabling instruments, on primary AND replica INDEPENDENTLY; replica gate failure → primary-only
    fallback, recorded as a known blind spot.
  - `requirements.md` Decisions (taste — follow the read path): telemetry runs on every instance that
    serves reads; primary-only telemetry structurally cannot see replica-only pathologies.
  - `mechanics.md` §5 / §6 grants: monitoring user (`PROCESS`, `REPLICATION CLIENT`,
    `SELECT ON performance_schema.*`) created on primary, replicates to Seoul; DSN via Secrets Manager
    → ESO → k8s Secret; never in repo (INV11).
  - `handoff.md:107-110`: RDS Performance Insights likely unsupported on this instance class — verify
    before relying on it; digest + table_io instruments only.
- Depends on: 03 (manifest)
- Verify: Done When item 5 — mysqld_exporter `up` in both regions; digest + QPS metrics visible;
  per-instance FreeableMemory gate results recorded (INV7). Record in the phase verification doc.
