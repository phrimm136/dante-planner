# Phase 02 (local-tdd)

## Rows
- view-dedup-composite-pk: org.danteplanner.backend.integration.PlannerViewPipelineIT#secondRecordSameViewerDayNoops — flush
- view-flush-replay-idempotent: org.danteplanner.backend.integration.PlannerViewPipelineIT#replayedFlushNoDoubleCount — second flush
- detail-read-holds-no-lock: org.danteplanner.backend.integration.PublishedPlannerDetailIT#concurrentDetailReadsDoNotSerialize — second reader hits the same planner
- detail-response-precedes-view-write: org.danteplanner.backend.integration.PublishedPlannerDetailIT#responseReturnsBeforeViewPersisted — detail requested
- publish-sse-after-commit-only: org.danteplanner.backend.integration.PlannerPublishEventIT#rollbackEmitsNoSseEvent — rollback completes
- settings-get-absent-row-defaults: org.danteplanner.backend.integration.UserSettingsReadIT#absentRowYieldsDefaultsNot500 — GET settings
- settings-null-sync-semantics: org.danteplanner.backend.integration.UserSettingsReadIT#nullSyncEnabledPreserved — GET settings
- settings-created-with-user: org.danteplanner.backend.integration.UserResolutionIT#newUserGetsSettingsRowAtomically — createOrRecover
- settings-backfill-covers-legacy: org.danteplanner.backend.integration.SettingsBackfillMigrationIT#legacyUsersGainRows — migrations applied
- oauth-race-loser-converges: org.danteplanner.backend.integration.UserResolutionIT#duplicateKeyLoserRetriesAndFinds — both attempt creation
- returning-login-no-insert: org.danteplanner.backend.integration.UserResolutionIT#returningUserNoWrites — resolve

## Touches
- backend/src/main/java/org/danteplanner/backend/planner/service/PublishedPlannerQueryService.java
- backend/src/main/java/org/danteplanner/backend/planner/service/PlannerViewRecorder.java
- backend/src/main/java/org/danteplanner/backend/planner/service/PlannerPublishingService.java
- backend/src/main/java/org/danteplanner/backend/planner/service/PlannerCommandService.java
- backend/src/main/java/org/danteplanner/backend/comment/service/CommentService.java
- backend/src/main/java/org/danteplanner/backend/user/service/UserSettingsService.java
- backend/src/main/java/org/danteplanner/backend/auth/**
- backend/src/main/resources/db/migration/V0*__backfill_user_settings.sql
- backend/src/test/resources/db/seed/migration-test-seed.sql
- backend/src/main/java/org/danteplanner/backend/CLAUDE.md

## DECISION SLOTS
ordering-rationale: Migrations (settings backfill V0XX + index swap + planner_stats DDL) run first as fixture setup. Then group by seam: view pipeline (rows 4–5, planner_views PK idempotence), detail-read path (rows 6–7, readOnly + async buffer), settings lifecycle (rows 9–12, creation-time atomicity + backfill coverage), and OAuth race handling (rows 13–14, uk_provider_provider_id unique key). Rows 4–5 and 6–7 share the same planner infrastructure; rows 9–10 and 13–14 test the same services independently.
batch-boundary: Batch 1: migrations as fixture (index + backfill + stats DDL). Batch 2: rows 4–5 together (both stress planner_views composite PK). Batch 3: rows 6–7 together (both exercise PublishedPlannerQueryService readOnly + view counter). Batch 4: row 8 alone (publish-sse-after-commit-only is orthogonal). Batch 5: rows 9–10 together (both test settings read defaults). Batch 6: rows 11–12 together (creation-time atomicity + backfill invariant share user_settings structure). Batch 7: rows 13–14 together (both OAuthUser resolution and race handling).
fallback-scope: If 60min timebox expires, descope row 8 (publish-sse-after-commit-only) — R10 (commit-only emission) is important but orthogonal to latency R1–R3; stubAllow `PlannerPublishingService.emitPublishEvent()`. If further time pressure: descope the explicit response-timing assertion in row 7 (detail-response-precedes-view-write) and rely on the async buffer's best-effort semantics. Rows 4–6 and 9–14 are non-negotiable; they enforce R1, R3, R4.
