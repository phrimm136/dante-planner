# Phase 03: mysqld_exporter manifest (LOCAL/RENDER scope) — PASS

### Suite

Render-grep phase; no persisted test harness (kustomize binary absent — used `kubectl kustomize`
from repo root).

```
$ kubectl kustomize deploy/overlays/oregon   → OREGON EXIT: 0
$ kubectl kustomize deploy/overlays/seoul     → SEOUL EXIT: 0
```

Both overlays render clean. The mysqld-exporter Deployment + ExternalSecret `mysqld-exporter-dsn`
appear in both overlays (ExternalSecret object confirmed identical in oregon.yaml and seoul.yaml),
region-correctly patched.

### Trace

| Item | Source | Status | Code evidence | Test evidence (render) |
|------|--------|--------|---------------|------------------------|
| Topology: one Deployment per region on `role=data`; Oregon→primary, Seoul→Seoul-replica (read-local symmetry) | mechanics §6 | MET | `deploy/base/mysqld-exporter.yaml:20-21` (`nodeSelector: role: data`), `:2-6` (Deployment `mysqld-exporter`); Oregon patch `deploy/overlays/oregon/mysqld-endpoint-patch.yaml:11-15` (MYSQLD_ADDRESS ← secretKeyRef primary-endpoint); Seoul patch `deploy/overlays/seoul/mysqld-endpoint-patch.yaml:11-12` (literal replica host) | Oregon render: `nodeSelector role: data`, `MYSQLD_ADDRESS` valueFrom secretKeyRef `mysqld-exporter-dsn/primary-endpoint`. Seoul render: same Deployment, `MYSQLD_ADDRESS value: mysql-replica.seoul.danteplanner.internal:3306` |
| DSN delivery: Secrets Manager → ESO → k8s Secret (never in-repo) | mechanics §6; deploy/CLAUDE.md | MET | `deploy/base/mysqld-exporter.yaml:51-73` ExternalSecret `mysqld-exporter-dsn`, `secretStoreRef aws-secrets-manager`, remoteRefs `danteplanner/mysqld-exporter/{username,password,primary-endpoint}`; container creds via secretKeyRef `:31-40` Both renders: ExternalSecret `mysqld-exporter-dsn` with 3 remoteRefs (username/password/primary-endpoint) — object identical in oregon.yaml and seoul.yaml; container `MYSQL_EXPORTER_USER`/`MYSQLD_EXPORTER_PASSWORD` both `secretKeyRef` to `mysqld-exporter-dsn`. No hand-authored Secret |
| perf_schema scope: digest + table_io instruments only | mechanics §6 (handoff.md:107-110) | MET | `deploy/base/mysqld-exporter.yaml:27-28`: exactly `--collect.perf_schema.eventsstatements` + `--collect.perf_schema.tableiowaits`, no other `--collect.*`/`--no-collect` | Both renders: args carry only those two perf_schema flags (all other collectors default-false; slave_status left at default by design) |
| Job label `mysqld` (cluster external label = phase-01 prior-art) | mechanics §6 | MET | `deploy/base/mysqld-exporter.yaml:18` `prometheus.io/job: mysqld` | Both renders: annotation `prometheus.io/job: mysqld` (unquoted — phase-02-ratified kubectl serialization quirk; not a gap) |
| Per-region exporter at region-local endpoint; observers run on `role=data` | requirements.md §G mechanism | MET | Same as Topology row: Oregon→primary secretKeyRef, Seoul→replica literal; `nodeSelector role: data` in base | Both renders confirm region-local address + `role: data` placement |
| Done-When (render half): mysqld_exporter resources render in both overlays region-correctly | requirements.md Done When | MET | Base + both endpoint patches registered: `deploy/base/kustomization.yaml:18`, `deploy/overlays/oregon/kustomization.yaml:22`, `deploy/overlays/seoul/kustomization.yaml:22` | Both overlays exit 0; Deployment + ExternalSecret present in each; addresses region-differentiated |
| Render assertion: DSN via ExternalSecret — no literal credential in any rendered manifest | requirements.md Tests to Write (INV11 local half) | MET | Creds only via secretKeyRef; no `value:` credential in base/patches | Full-render grep of both overlays for plaintext credential values: NO plaintext credential VALUES in either render |
| INV11: no literal cred/DSN/primary RDS endpoint in committed manifests; Seoul replica host is sanctioned Route53 private-zone name | INV11 local half | MET | Repo-wide grep of the 3 committed phase-03 manifests for `password=`/`DATA_SOURCE_NAME`/account-id/`amazonaws.com`/`rds.`/IPv4: NO matches. Seoul host `mysql-replica.seoul.danteplanner.internal` matches existing app `MYSQL_REPLICA_HOST` in `deploy/overlays/seoul/configmap-patch.yaml:32` (sanctioned committable form) | Grep: "NO literal credential/endpoint matches"; parity confirmed against configmap-patch |
| Grants (`PROCESS`, `REPLICATION CLIENT`, `SELECT ON performance_schema.*`) + Memory gate (FreeableMemory before/after) | mechanics §6 / INV11 §5 | DEFERRED-OUT-OF-SCOPE | Operational/live; phase 11 | n/a — not renderable |
| Per-instance FreeableMemory gate | requirements.md §G | DEFERRED-OUT-OF-SCOPE | Live; phase 11 | n/a |
| mysqld_exporter up in both regions; digest & QPS metrics visible; per-instance FreeableMemory gate recorded | requirements.md Done When (infra) | DEFERRED-OUT-OF-SCOPE | Live; phase 11 | n/a |

### Gaps

None. Every in-scope render item is MET with evidence; all live/operational items are correctly
classified DEFERRED-OUT-OF-SCOPE (phase 11), not UNMET.
