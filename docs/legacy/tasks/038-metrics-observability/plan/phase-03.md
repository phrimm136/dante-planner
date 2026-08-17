# Phase 03: mysqld_exporter manifest

- Kind: local-tdd (kustomize render assertions)
- Files:
  - `deploy/base/mysqld-exporter.yaml` (create: Deployment + ExternalSecret for the DSN)
  - `deploy/overlays/oregon/mysqld-endpoint-patch.yaml` (create: primary endpoint)
  - `deploy/overlays/seoul/mysqld-endpoint-patch.yaml` (create: Seoul replica endpoint)
  - `deploy/base/kustomization.yaml` + both overlay kustomizations (modify: register — additive)
- Tests: `kustomize build deploy/overlays/{oregon,seoul}` render assertions
- External contract: rendered Deployment on `nodeSelector role=data`, one replica per region, job
  label `mysqld`, DSN sourced from a k8s Secret produced by an ExternalSecret (Secrets Manager → ESO);
  perf_schema scope limited to digest + table_io instruments; region-local endpoint applied by the
  overlay patch (Oregon → primary, Seoul → Seoul replica).
- Mechanics sections: `mechanics.md` §6 (mysqld_exporter contract).
- Implementation methods:
  - DSN delivery via ExternalSecret — exemplar `deploy/base/external-secret.yaml:16-31`
    (`ExternalSecret` → `SecretStore aws-secrets-manager`; `deploy/CLAUDE.md` secrets rule). No literal
    credential in any manifest (INV11 local half).
  - Region-local endpoint injected by overlay patch, never committed literally — internal hostnames
    go through the Route53 private zone (`handoff.md:160-163`, e.g. `mysql-replica.seoul.…internal`).
- Considerations:
  - `requirements.md` Decisions (§G mechanism): mysqld_exporter per region pointing at its
    region-local endpoint — read-local symmetry; app-side perf_schema polling and RDS PI were rejected.
  - `mechanics.md` §6 memory gate / `handoff.md:107-110`: perf_schema costs tens of MB on a 1GiB
    t4g.micro — enable digest + table_io instruments ONLY. The live before/after gate is phase 11.
  - INV11: DSN and monitoring-user grants never in repo; grants provisioned operationally (§5).
- Depends on: 01 (job relabel provides the `mysqld` job label)
- Verify: both overlays `kustomize build` cleanly; rendered output shows the ExternalSecret-backed
  DSN (no literal credential), `role=data` placement, and the region-correct endpoint patch applied.
