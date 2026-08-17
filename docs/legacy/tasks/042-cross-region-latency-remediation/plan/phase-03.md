# Phase 03 (local-tdd)

## Rows
- stats-dual-write-consistent: org.danteplanner.backend.integration.PlannerStatsDualWriteIT#incrementAdvancesBothStores — increment applies
- stats-flag-on-reads-stats: org.danteplanner.backend.integration.PlannerStatsCutoverIT#flagOnServesStatsValues — read with flag on
- stats-flag-off-reads-legacy: org.danteplanner.backend.integration.PlannerStatsCutoverIT#flagOffServesLegacyValues — read with flag off

## Touches
- backend/src/main/resources/db/migration/V0*__create_planner_stats.sql
- backend/src/main/java/org/danteplanner/backend/planner/entity/PlannerStats.java
- backend/src/main/java/org/danteplanner/backend/planner/repository/PlannerStatsRepository.java
- backend/src/main/java/org/danteplanner/backend/planner/service/**
- backend/src/test/resources/db/seed/migration-test-seed.sql
- deploy/overlays/oregon/configmap-patch.yaml
- deploy/overlays/seoul/configmap-patch.yaml

## DECISION SLOTS
ordering-rationale: Test `stats-dual-write-consistent` first to verify the migration (V0*__create_planner_stats.sql) and unconditional dual-write in `backend/src/main/java/org/danteplanner/backend/planner/service/**` correctly populate both `planners` and `planner_stats` (D16 requirement). Then test `stats-flag-on-reads-stats` and `stats-flag-off-reads-legacy` to verify reads correctly switch between stores based on flag state. All three share the migration and entity fixtures; write consistency must be established first.

batch-boundary: Batch 1: `stats-dual-write-consistent` as a standalone red-green cycle, with the red assertion pinned to the case the dead stats spike exposed: seed a legacy planner that has NO planner_stats row of its own, then assert both stores converge after migrations + increment. Do not phrase the assertion around a pre-existing stats row — a pre-existing-row assertion passes against a plain-UPDATE dual-write and ships the false-clean; the absent-row form fails plain-UPDATE and passes under both amendment mechanisms (creation-time row or upsert increments) without picking one. Batch 2: `stats-flag-on-reads-stats` and `stats-flag-off-reads-legacy` together, since both test flag-gated read behavior in `backend/src/main/java/org/danteplanner/backend/planner/repository/PlannerStatsRepository.java` and service layer. Separating by seam (write vs. flag-gated read) keeps flag state concerns isolated from dual-write verification.

fallback-scope: Descope only the two flag-reading rows (`stats-flag-on-reads-stats`, `stats-flag-off-reads-legacy`) and the ConfigMap overlays (`deploy/overlays/*/configmap-patch.yaml`), with the flag left off everywhere. The absent-stats-row dual-write convergence assertion (Batch 1) AND the `migration-test-seed.sql` change seeding a legacy no-stats-row planner are non-negotiable: deferring that seed while stubbing reads to legacy would make the planner_stats write path write-only-and-unread with its riskiest case unverified — exactly the false-clean the spike found.
