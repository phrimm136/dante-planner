# Phase 01 (local-tdd)

## Rows
- sort-popular-collapses-to-recent: org.danteplanner.backend.integration.PublishedPlannerSortIT#popularParamFallsBackToRecent — list requested with sort=popular
- sort-unknown-falls-back-recent: org.danteplanner.backend.integration.PublishedPlannerSortIT#unknownSortFallsBackToRecent — unknown sort value requested
- list-excludes-nonpublic: org.danteplanner.backend.integration.PublishedPlannerListIT#seedMembershipOnlyPublishedVisible — list requested
- list-field-parity: org.danteplanner.backend.integration.PublishedPlannerListIT#summaryFieldParity — list requested
- takedown-unpublishes-entity: org.danteplanner.backend.planner.entity.PlannerEntityTest#takeDownUnpublishes — takeDown()
- republish-blocked-while-taken-down: org.danteplanner.backend.planner.entity.PlannerEntityTest#togglePublishedThrowsWhenTakenDown — togglePublished()
- logout-revocations-atomic: org.danteplanner.backend.integration.LogoutRevocationIT#luaRevokesAllThreeAtomically — logout
- logout-invalid-token-fast-path: org.danteplanner.backend.integration.LogoutRevocationIT#absentTokensSilentSuccess — logout
- resttemplate-timeouts-bounded: org.danteplanner.backend.shared.config.HttpClientConfigTest#timeoutsConfigured — bean built
- oolfe-maps-to-409-concurrent: org.danteplanner.backend.integration.PlannerUpsertConflictIT#concurrentUpsertYields409ConcurrentWrite — second flush hits the stale version
- stale-client-409-reason: org.danteplanner.backend.integration.PlannerUpsertConflictIT#staleSyncVersion409StaleClient — upsert
- counters-immune-to-entity-save: org.danteplanner.backend.integration.PlannerCounterGuardIT#entitySavePreservesBulkIncrements — entity flush
- promotion-counter-registered-at-boot: org.danteplanner.backend.integration.MetricsRegistrationIT#replicaMissPromotedPresentOnScrape — scrape

## Touches
- backend/src/main/resources/db/migration/V0*__swap_published_planner_index.sql
- backend/src/main/java/org/danteplanner/backend/planner/repository/PlannerRepository.java
- backend/src/main/java/org/danteplanner/backend/planner/controller/PublishedPlannerController.java
- backend/src/main/java/org/danteplanner/backend/planner/dto/PlannerSummaryRow.java
- backend/src/main/java/org/danteplanner/backend/planner/dto/PublicPlannerResponse.java
- backend/src/main/java/org/danteplanner/backend/planner/entity/Planner.java
- backend/src/main/java/org/danteplanner/backend/auth/facade/AuthenticationFacade.java
- backend/src/main/java/org/danteplanner/backend/auth/token/TokenBlacklistService.java
- backend/src/main/java/org/danteplanner/backend/auth/token/RefreshRotationService.java
- backend/src/main/java/org/danteplanner/backend/shared/config/HttpClientConfig.java
- backend/src/main/java/org/danteplanner/backend/shared/exception/GlobalExceptionHandler.java
- backend/src/main/java/org/danteplanner/backend/shared/readpath/PrimaryReCheck.java

## DECISION SLOTS
ordering-rationale: Sort behavior (sort-popular-collapses-to-recent, sort-unknown-falls-back-recent) tests the index swap (V0NN) and D10's catalog index first; then published-list seam (list-excludes-nonpublic, list-field-parity) validates the DTO projection (D12) and D18's taken-down predicate; then entity lifecycle (takedown-unpublishes-entity, republish-blocked-while-taken-down) enforces INV3; logout seam (logout-revocations-atomic, logout-invalid-token-fast-path) and config (resttemplate-timeouts-bounded, promotion-counter-registered-at-boot) run independently; finally conflict/counter edge cases (oolfe-maps-to-409-concurrent, stale-client-409-reason, counters-immune-to-entity-save) stress D14/D15 last. Migrations (index swap, settings backfill via V0NN+) are fixture setup preceding all database tests.

batch-boundary: Five seam-aligned red batches, ordered per the ordering-rationale seam sequence: B1 list/sort (sort-popular-collapses-to-recent, sort-unknown-falls-back-recent, list-excludes-nonpublic, list-field-parity — MySQL catalog index seam); B2 entity (takedown-unpublishes-entity, republish-blocked-while-taken-down — pure in-memory PlannerEntityTest, INV3); B3 logout/Redis (logout-revocations-atomic, logout-invalid-token-fast-path — @Primary auth Redis seam); B4 conflict/counter (oolfe-maps-to-409-concurrent, stale-client-409-reason, counters-immune-to-entity-save — D14/D15); B5 config/observability (resttemplate-timeouts-bounded unit + promotion-counter-registered-at-boot). The rows do not share one fixture surface — PlannerEntityTest and HttpClientConfigTest are non-Spring unit tests, and the logout rows hit auth Redis rather than MySQL — so a single all-or-nothing batch would couple unrelated seams' failures into one muddied red and yield zero green if implementation stalls mid-timebox. Per-seam batches isolate red feedback and give clean timeout attribution.

fallback-scope: Descope edge-case and observability rows via stubAllow matching `*unknown*`, `*invalid*`, `*promoted*`, `*stale*` if the 45-minute timebox expires: drop sort-unknown-falls-back-recent, logout-invalid-token-fast-path, stale-client-409-reason, promotion-counter-registered-at-boot (4 rows, ~8 min saved). Focus green on core paths: sort-popular (D11), published-list filtering/parity (D10/D12/D18), entity lifecycle (INV3), logout atomicity (D8), counter guard (D15), and basic 409 conflict detection (D14 raised but reason field stubbed), preserving 9 rows and all migrations.
