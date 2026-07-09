# Results: Search Plans by Content Items

## What Was Done

All 8 build phases + backfill migration + test suites + code review fixes.

### Backend
- V039: `planner_content_index` table with PK `(entity_type, entity_id, planner_id)`
- V040: backfill migration using MySQL JSON_TABLE to index existing published plans (idempotent via INSERT IGNORE)
- `ContentEntityType` enum, `PlannerContentIndex` entity with `@IdClass` + `Persistable`
- `PlannerIndexService`: JSON extraction + DELETE/INSERT index population, 4 integration points in PlannerService
- `PlannerSpecifications`: 7 composable specs with LIKE wildcard escaping
- Controller: structured query params on published/recommended endpoints
- `JpaSpecificationExecutor` added to PlannerRepository (additive)

### Frontend
- `PlannerSearchTypes.ts`, `usePlannerSearchFilters.ts` (URL param sync)
- `plannerContentExtractors.ts`: 4 extraction functions + `matchesPlannerFilters`
- `PlannerFilterPane.tsx`: progressive loading (keywords eager, heavy sections lazy via Suspense)
- `usePlannerKeywordsI18n.ts` + `plannerKeywords.json` (EN/KR/JP/CN) with sin affinity names
- Custom router search serializer (preserves commas, numeric coercion only for `page`)
- Published list: filter pane → URL params → BE query
- Personal list: filter pane → URL params → IndexedDB local filtering
- Always-visible "Reset" button using `common:reset`

### Tests
- `PlannerIndexServiceTest.java`: 9 tests
- `PlannerSpecificationsTest.java`: 9 tests
- `plannerContentExtractors.test.ts`: 30 tests

### Code Review
- Verdict: ACCEPTABLE (4.3/5)
- S1 fixed: LIKE wildcard escaping in `titleContains`
- S4 fixed: spec path corrected to `static/i18n/`

## Files Changed

### New (BE)
- `backend/src/main/resources/db/migration/V039__create_planner_content_index.sql`
- `backend/src/main/resources/db/migration/V040__backfill_planner_content_index.sql`
- `backend/src/main/java/org/danteplanner/backend/entity/ContentEntityType.java`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerContentIndex.java`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerContentIndexId.java`
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerContentIndexRepository.java`
- `backend/src/main/java/org/danteplanner/backend/service/PlannerIndexService.java`
- `backend/src/main/java/org/danteplanner/backend/specification/PlannerSpecifications.java`
- `backend/src/test/java/org/danteplanner/backend/service/PlannerIndexServiceTest.java`
- `backend/src/test/java/org/danteplanner/backend/specification/PlannerSpecificationsTest.java`

### New (FE)
- `frontend/src/types/PlannerSearchTypes.ts`
- `frontend/src/hooks/usePlannerSearchFilters.ts`
- `frontend/src/hooks/usePlannerKeywordsI18n.ts`
- `frontend/src/lib/plannerContentExtractors.ts`
- `frontend/src/components/plannerList/PlannerFilterPane.tsx`
- `frontend/src/lib/__tests__/plannerContentExtractors.test.ts`
- `static/i18n/{EN,KR,JP,CN}/plannerKeywords.json`

### Modified (BE)
- `backend/.../PlannerRepository.java` — added JpaSpecificationExecutor
- `backend/.../PlannerService.java` — index lifecycle calls (4 points)
- `backend/.../PlannerController.java` — new query params
- `backend/.../PlannerServiceTest.java` — added PlannerIndexService mock

### Modified (FE)
- `frontend/src/lib/router.tsx` — custom search serializer, new route search params
- `frontend/src/types/MDPlannerListTypes.ts` — filter params
- `frontend/src/hooks/useMDGesellschaftFilters.ts` — expose raw filter params
- `frontend/src/hooks/useMDGesellschaftData.ts` — forward filter params to API
- `frontend/src/hooks/useMDUserPlannersData.ts` — content filter via IndexedDB
- `frontend/src/hooks/usePlannerStorage.ts` — `listFullPlanners()`
- `frontend/src/hooks/usePlannerSaveAdapter.ts` — `listLocalFull()`
- `frontend/src/components/plannerList/PublishedPlannerList.tsx` — filter props
- `frontend/src/components/plannerList/PersonalPlannerList.tsx` — filter props
- `frontend/src/routes/PlannerMDGesellschaftPage.tsx` — filter pane integration
- `frontend/src/routes/PlannerMDPage.tsx` — filter pane integration
- `static/i18n/{EN,KR,JP,CN}/planner.json` — filterPane i18n keys

## Verification
- BE build: pass
- FE build: pass
- BE tests: 18/18 pass
- FE tests: 30/30 pass
- Code review: ACCEPTABLE
- Manual: filter pane rendering, icon selection, scrolling, URL params, progressive loading verified via screenshots

## Issues & Resolutions
- ScrollArea not constraining → native `overflow-y-auto`
- Icon glow clipped → `p-0.5` on scroll container
- Inconsistent icon sizes → standardized `selectable size-10 rounded overflow-hidden p-0`
- Category scroll moved page → scroll within pane via ref
- Commas encoded as `%2C` → custom router search serializer
- Path merged with params → `?` prefix in stringifier
- ID parsed as number → numeric coercion restricted to `page` key
- Affinity labels were color names → updated to sin names in all locales
- LIKE wildcards unescaped → escape `%`, `_`, `\` in `titleContains`
- Pane slow to open → progressive loading via outer/inner component split
- New i18n keys for "Reset" → reused `common:reset`
- Missing backfill → V040 migration with JSON_TABLE

## Spec Divergence

### What Changed
- **60vh pane height** → 30vh. User requested "reduce by half."
- **Category nav placement** → Inside pane after chips (not a fixed row). Iterated during UI review.

### What Was Added (Not in Spec)
- **V040 backfill migration** — existing published plans need indexing. Not foreseeable without considering deploy-time state.
- **Custom router search serializer** — TanStack Router encodes commas by default. Only discoverable at runtime.
- **Progressive loading** — performance issue not apparent from spec. Emerged during manual testing.
- **"Reset" button** — UX addition during iteration.
- **Sinner name in chips** — disambiguation need visible only with real data.
- **LIKE wildcard escaping** — caught during code review, not in spec.

### What Was Dropped
- **Keyword counts ("Burst (42)")** — discussed in brainstorm, not implemented. Requires aggregate query.

### Wrong Assumptions
- **i18n path**: spec said `static/data/`, actual is `static/i18n/`
- **ScrollArea constrains height**: it doesn't without explicit height; native overflow works
- **TanStack Router preserves commas**: it doesn't; default serializer encodes them
- **All data can load synchronously**: ~1000 items across 5 categories causes visible delay

### Prompting Retrospective
- **Router encoding**: "How does TanStack Router serialize commas in URL search params?"
- **i18n paths**: "Show me `ls static/` — where do locale files live?"
- **Load perf**: "The filter pane loads ~1000 game items. What's the latency? Should we lazy-load?"
- **Existing translations**: "What keys exist in common.json? Can we reuse them?"
- **Backfill**: "When V039 deploys, existing published plans have no index rows. How do we populate them?"

### Spec Process Takeaway
Specs for UI features systematically miss runtime interaction details (encoding, load performance, scroll behavior) and deploy-time data state (backfill). Include a "deploy checklist" section and budget for one iteration round on interactive elements.

## Session State

### Uncommitted changes
All work uncommitted. ~30 new files, ~15 modified files.

### Next steps
1. Manual QA with running backend + frontend against real data
2. Commit
3. Deploy — V039 + V040 run automatically via Flyway
