# Execution Plan

## Phase Summary

Backend-first: schema → index population → search API. Then FE: foundation types → filter pane → integration with both published (BE) and personal (IndexedDB) search.

## Phases

### Phase 1: BE Schema Foundation
- Files:
  - CREATE `backend/src/main/resources/db/migration/V039__create_planner_content_index.sql`
  - CREATE `backend/src/main/java/org/danteplanner/backend/entity/PlannerContentIndex.java`
  - CREATE `backend/src/main/java/org/danteplanner/backend/entity/PlannerContentIndexId.java`
  - CREATE `backend/src/main/java/org/danteplanner/backend/repository/PlannerContentIndexRepository.java`
- Depends on: none
- Pattern sources: PlannerVote.java, PlannerVoteId.java, PlannerVoteRepository.java (composite PK + Persistable pattern)
- Verify: Backend compiles. Entity matches migration schema. Repository has deleteByPlannerId method.

### Phase 2: BE Index Population Service
- Files:
  - CREATE `backend/src/main/java/org/danteplanner/backend/service/PlannerIndexService.java`
  - MODIFY `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` (call indexing on publish/unpublish/update)
- Depends on: Phase 1
- Pattern sources: PlannerContentValidator.java (JSON parsing patterns), PlannerService.java (upsert flow)
- Verify: Backend compiles. Index service extracts IDs from all 4 entity types. PlannerService calls index on publish, re-index on published update, delete on unpublish.

### Phase 3: BE Search API with Specifications
- Files:
  - CREATE `backend/src/main/java/org/danteplanner/backend/specification/PlannerSpecifications.java`
  - MODIFY `backend/src/main/java/org/danteplanner/backend/repository/PlannerRepository.java` (add JpaSpecificationExecutor)
  - MODIFY `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` (new search method using specs)
  - MODIFY `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java` (accept new query params)
- Depends on: Phase 1
- Pattern sources: Existing search methods in PlannerRepository, existing controller endpoints
- Verify: Backend compiles. New query params accepted. Specifications compose correctly. Existing endpoints unchanged when params absent.

### Phase 4: FE Foundation — Types, URL Params, Extraction Utilities
- Files:
  - CREATE FE filter types (PlannerSearchFilters)
  - CREATE FE URL param sync hook
  - CREATE FE entity ID extraction utilities (extractIdentityIds, extractEgoIds, extractGiftIds, extractThemePackIds)
- Depends on: none
- Pattern sources: PlannerTypes.ts (MDPlannerContent), existing hooks
- Verify: TypeScript compiles. Extraction utilities handle MDPlannerContent structure. URL param hook serializes/deserializes filter state.

### Phase 5: FE i18n Keyword Data
- Files:
  - CREATE `static/data/{locale}/plannerKeywords.json` (at minimum en, ko)
- Depends on: none
- Pattern sources: KeywordSetConverter.java (VALID_KEYWORDS), existing static data files
- Verify: JSON valid. All 29 keywords mapped. Localized labels present.

### Phase 6: FE Filter Pane Component
- Files:
  - CREATE filter pane component(s)
- Depends on: Phase 4, Phase 5
- Pattern sources: Existing shadcn/ui component usage, existing planner list page
- Verify: TypeScript compiles. Component renders 5 categories. Icon selection toggles chips. Search narrows items. Category nav scrolls. Collapse/expand works.

### Phase 7: FE Published Plan Search Integration
- Files:
  - MODIFY FE published planner list page (add filter pane, wire URL params to BE query)
- Depends on: Phase 3, Phase 6
- Verify: URL params trigger BE query with correct params. Results update on filter change. Existing behavior preserved with empty filters.

### Phase 8: FE Personal Plan Search Integration
- Files:
  - MODIFY FE personal planner list page (add filter pane, wire URL params to IndexedDB local filtering)
- Depends on: Phase 4, Phase 6
- Pattern sources: usePlannerStorage.ts (IndexedDB operations)
- Verify: Plans read from IndexedDB. Local filtering applies all filter types with AND semantics. URL params shareable.

## Phase Dependencies

```
Phase 1 ──┬── Phase 2
           └── Phase 3 ──── Phase 7
Phase 4 ──┬── Phase 6 ──┬── Phase 7
           │             └── Phase 8
Phase 5 ──┘
Phase 4 ──────────────────── Phase 8
```

Group A (parallel): Phase 1, Phase 4, Phase 5
Group B (after Phase 1): Phase 2, Phase 3
Group C (after Phase 4 + 5): Phase 6
Group D (after Phase 3 + 6): Phase 7
Group E (after Phase 4 + 6): Phase 8
