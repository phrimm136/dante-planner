# Task: Search Plans by Content Items

## Decisions

- **Reverse index table over JSON queries** — MySQL JSON functions are unused in the codebase; the content column is opaque. A dedicated index table avoids parsing JSON at query time and enables standard SQL filtering.
- **Single index table over 8-9 normalized tables** — Only searchable entity references (IDs) are indexed, not the full content structure. One flat table with entity_type ENUM instead of per-type tables.
- **Composite PK `(entity_type, entity_id, planner_id)`** — Clusters data by the search predicate (hot path: read). Write path (DELETE + INSERT on publish) uses a secondary index on `planner_id`. Read-heavy/write-rare trade-off.
- **EXISTS subqueries over JOINs for Specifications** — EXISTS maps cleanly to composable Spring Data Specifications (each filter is self-contained). JOINs would require coordinated aliases across specs. TODO: Revisit with JOINs if query performance becomes a bottleneck at scale.
- **Index only published plans on BE** — Drafts are not indexed server-side. Index on publish, re-index on published plan update, delete on unpublish, CASCADE on soft-delete, no action on moderator takedown (query filters handle visibility).
- **Personal plan search is FE-only against IndexedDB** — Personal plans live in IndexedDB. The filter pane reads plans from IndexedDB and filters locally. Same filter categories, same AND semantics, same URL param format.
- **Both search modes use URL params** — Published and personal plan search both serialize filter state to URL params. Filters are shareable and bookmarkable on both pages.
- **Collapsible filter pane over autocomplete** — Matches the site's filter-by-category model. Users scan and select icons rather than typing into a single mixed-type autocomplete bar.
- **FIND_IN_SET over LIKE for keywords (BE)** — Exact set membership matching instead of substring matching. Fixes latent bug where LIKE could match keyword substrings.
- **Spring Data Specifications (additive)** — New search uses `JpaSpecificationExecutor`. Existing 8 query methods in PlannerRepository are untouched; migration to Specifications is deferred.

## Description

### Backend

#### Database Schema

Create `planner_content_index` table (V039 migration):

```sql
CREATE TABLE planner_content_index (
    planner_id BINARY(16) NOT NULL,
    entity_type ENUM('IDENTITY', 'EGO', 'EGO_GIFT', 'THEME_PACK') NOT NULL,
    entity_id VARCHAR(20) NOT NULL,

    PRIMARY KEY (entity_type, entity_id, planner_id),
    INDEX idx_planner (planner_id),
    CONSTRAINT fk_pci_planner FOREIGN KEY (planner_id)
        REFERENCES planners(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

#### JPA Entity

`PlannerContentIndex` with `@IdClass` + `Persistable` pattern, matching PlannerVote/PlannerView.

#### Index Population

In service layer, on upsert of published plans:

1. Parse content JSON (already parsed in `PlannerContentValidator`)
2. Extract entity IDs:
   - `IDENTITY`: `equipment[*].identity.id`
   - `EGO`: `equipment[*].egos[*].id`
   - `EGO_GIFT`: `selectedGiftIds` + `observationGiftIds` + `comprehensiveGiftIds` + `floorSelections[*].giftIds`
   - `THEME_PACK`: `floorSelections[*].themePackId`
3. `DELETE FROM planner_content_index WHERE planner_id = ?`
4. Batch `INSERT` extracted entries
5. On unpublish: `DELETE FROM planner_content_index WHERE planner_id = ?`

#### Search API

Extend existing published planner endpoints with structured query params:

```
?q={title}&keyword={csv}&identity={csv}&ego={csv}&gift={csv}&themePack={csv}
```

- All filters AND-composed
- Multiple values within a category: AND (plan must contain ALL specified items)
- `q`: `LIKE` on `title` column
- `keyword`: `FIND_IN_SET` on `selected_keywords` column
- `identity`, `ego`, `gift`, `themePack`: `EXISTS` subquery on `planner_content_index`

Visibility filters applied to all queries: `published = true AND deleted_at IS NULL AND taken_down_at IS NULL`.

#### Specifications

Add `JpaSpecificationExecutor<Planner>` to `PlannerRepository`. Build composable specifications:

- `isPublished()` — published, not deleted, not taken down
- `isRecommended(threshold)` — extends isPublished, not hidden, upvotes >= threshold
- `hasCategory(category)`
- `titleContains(text)` — LIKE on title
- `hasKeyword(keyword)` — FIND_IN_SET on selected_keywords
- `hasContentEntity(entityType, entityId)` — EXISTS on content index

### Frontend

#### Filter Pane Component

Collapsible pane toggled by clickable "Filter options" text with up/down arrow icon:

- **Row 1**: Search input to narrow all filter items across categories
- **Row 2**: Selected item chips (removable)
- **Row 3**: Category navigation buttons — `[Keywords] [Identity] [EGO] [Gift] [Theme Pack]` — click scrolls to section, shows match count from row 1 search
- **Section content**: Icon-button grids per category
  - Keywords: icon + label buttons
  - Identity: portrait icon buttons, grouped by sinner
  - EGO: portrait icon buttons, grouped by sinner
  - EGO Gift: portrait icon buttons
  - Theme Pack: square-cropped image buttons (CSS `object-fit: cover`)
- Click icon toggles selection, reflected in chip bar
- Pane starts collapsed
- Mobile: cap height at 60vh with internal scroll

#### Filter State Type

```typescript
type PlannerSearchFilters = {
  title: string | null;
  keywords: string[];       // internal keyword names, e.g. ["Burst", "Breath"]
  identityIds: string[];    // e.g. ["10212"]
  egoIds: string[];         // e.g. ["20301"]
  giftIds: string[];        // e.g. ["19001"]
  themePackIds: string[];   // e.g. ["1001"]
}
```

#### URL Params (both pages)

Both published and personal plan search serialize filter state to URL params:

```
?q={title}&keyword=Burst,Breath&identity=10212&ego=20301&gift=19001&themePack=1001
```

- Comma-separated within category
- Shareable and bookmarkable on both pages
- Filter pane reads initial state from URL on mount
- Filter changes update URL

#### Published Plan Search (BE Query)

- URL param changes trigger TanStack Query refetch to BE
- BE applies Specifications, returns paginated results

#### Personal Plan Search (FE Local Filtering against IndexedDB)

Personal plans are stored in IndexedDB. All filtering runs locally.

1. Read all user's plans from IndexedDB
2. Parse `MDPlannerContent` from each plan's content
3. Apply active filters from URL params:

| Filter | Source in `MDPlannerContent` | Match logic |
|--------|---------------------------|-------------|
| `title` | `plan.metadata.title` | Case-insensitive substring match |
| `keywords` | `plan.content.selectedKeywords` | ALL selected keywords present in the Set |
| `identityIds` | `plan.content.equipment[sinnerId].identity.id` for all sinners | ALL selected IDs found across equipment entries |
| `egoIds` | `plan.content.equipment[sinnerId].egos[egoType].id` for all sinners, all ego types | ALL selected IDs found across equipment entries |
| `giftIds` | Union of: `plan.content.selectedGiftIds` + `plan.content.observationGiftIds` + `plan.content.comprehensiveGiftIds` + `plan.content.floorSelections[*].giftIds` | ALL selected IDs found in the union |
| `themePackIds` | `plan.content.floorSelections[*].themePackId` (non-null values) | ALL selected IDs found across floors |

All filters AND-composed: a plan passes only if it matches every active filter.

Entity ID extraction utilities (reusable across filter matching):

```typescript
function extractIdentityIds(content: MDPlannerContent): Set<string>
function extractEgoIds(content: MDPlannerContent): Set<string>
function extractGiftIds(content: MDPlannerContent): Set<string>
function extractThemePackIds(content: MDPlannerContent): Set<string>
```

#### i18n

- Create `plannerKeywords.json` per locale for keyword display names (internal name -> localized label + icon path)
- Identity/EGO/gift display names from existing static game data
- Theme pack display names from existing static game data

### Deck Plan (future)

- Same index table, same Specifications
- FE hides keyword, gift, and theme pack filters when searching deck plans
- Local filtering for personal deck plans uses only identity + EGO extractors
- No backend branching needed

## Scope

Read for context:

- `backend/src/main/java/org/danteplanner/backend/entity/Planner.java`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerVote.java` (composite PK pattern)
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerView.java` (composite PK pattern)
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerRepository.java`
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` (upsert flow)
- `backend/src/main/java/org/danteplanner/backend/validation/PlannerContentValidator.java` (JSON parsing)
- `backend/src/main/java/org/danteplanner/backend/converter/KeywordSetConverter.java`
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java`
- `frontend/src/types/PlannerTypes.ts` (MDPlannerContent structure)
- `frontend/src/hooks/usePlannerStorage.ts` (IndexedDB operations)

## Target

Create:

- `backend/src/main/resources/db/migration/V039__create_planner_content_index.sql`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerContentIndex.java`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerContentIndexId.java`
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerContentIndexRepository.java`
- `backend/src/main/java/org/danteplanner/backend/specification/PlannerSpecifications.java`
- `backend/src/main/java/org/danteplanner/backend/service/PlannerIndexService.java`
- `static/i18n/{locale}/plannerKeywords.json`
- FE filter pane component
- FE `PlannerSearchFilters` type and URL param sync hook
- FE entity ID extraction utilities
- FE local filter matching logic for personal plans

Modify:

- `backend/src/main/java/org/danteplanner/backend/repository/PlannerRepository.java` (add JpaSpecificationExecutor)
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` (call index population on publish/unpublish)
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java` (accept new query params)
- FE published planner list page (add filter pane, wire URL params to BE query)
- FE personal planner list page (add filter pane, wire URL params to IndexedDB local filtering)

## Impact Analysis

- **PlannerRepository**: Adding `JpaSpecificationExecutor` is additive. No existing method signatures change.
- **PlannerService.upsertPlanner**: Adding index population call after successful publish. Existing flow unchanged for drafts.
- **PlannerController**: Adding query params to existing published planners endpoint. Existing behavior preserved when params absent.
- **Flyway**: New migration V039. No schema changes to existing tables.
- **FE published planner list**: Adding filter pane, structured filters replace/extend existing text search.
- **FE personal planner list**: Adding filter pane with IndexedDB local filtering. Existing list rendering unchanged.

## Risk Assessment

### Edge cases
- Plan with empty content (no identities/EGOs) — index has zero rows, search excludes it from entity filters (correct behavior)
- Plan unpublished while search is in flight — query visibility filters handle this
- Content version upgrade changes JSON structure — index population must handle both old and new content formats, or re-index on version migration
- Concurrent upsert of same plan — DELETE + INSERT runs in same transaction as plan update, optimistic locking prevents conflicts
- Personal plan with deserialized Sets vs arrays — extraction utilities must handle both `Set<string>` (in-editor state) and `string[]` (IndexedDB storage format)
- IndexedDB read latency — for 1-5 plans this is negligible, but the read should be async and not block UI

### Performance
- Index table size: ~160 rows per plan, ~10K published plans = ~1.6M rows. PK scans remain fast.
- EXISTS subqueries: each is a PK point lookup O(log n). 10 filters = ~30 page reads per candidate. Acceptable.
- TODO: If query latency becomes an issue, migrate EXISTS to JOINs for better optimizer freedom.
- Local filtering: 1-5 plans from IndexedDB, extraction is O(sinners * ego_types) per plan. Negligible.

### Security
- Entity IDs in query params validated against expected formats before reaching SQL
- Specifications use parameterized queries, no raw SQL injection surface

## Done When

### Backend
- [ ] V039 migration creates `planner_content_index` table successfully
- [ ] Publishing a plan populates the content index with correct entity IDs
- [ ] Updating a published plan re-indexes correctly (idempotent DELETE + INSERT)
- [ ] Unpublishing a plan removes index rows
- [ ] Soft-deleting a plan cascades index deletion
- [ ] `GET /published?identity=10212` returns only plans containing that identity
- [ ] `GET /published?identity=10212&ego=20301` returns plans containing BOTH (AND semantics)
- [ ] `GET /published?keyword=Burst,Breath` returns plans with BOTH keywords (FIND_IN_SET)
- [ ] `GET /published?q=rush&keyword=Burst&identity=10212` composes all filters with AND
- [ ] Empty filters return all published plans (existing behavior preserved)
- [ ] Existing search endpoints work unchanged when new params are absent

### Frontend — Filter Pane
- [ ] Filter pane renders with all 5 categories, icons load correctly
- [ ] Clicking an icon toggles selection, adds/removes chip
- [ ] Filter search (row 1) narrows displayed items across all categories
- [ ] Category nav buttons scroll to section and show match counts
- [ ] Theme pack images render as square-cropped
- [ ] Keyword labels display in user's locale
- [ ] Pane starts collapsed, toggle works

### Frontend — Published Plan Search
- [ ] URL params reflect current filter state and are shareable
- [ ] Param changes trigger BE query via TanStack Query
- [ ] Results update to reflect active filters

### Frontend — Personal Plan Search
- [ ] Filter pane appears on personal plan list
- [ ] URL params reflect current filter state and are shareable
- [ ] Plans read from IndexedDB and filtered locally
- [ ] Title search filters plans by case-insensitive substring match
- [ ] Keyword filter matches plans where selectedKeywords contains ALL selected keywords
- [ ] Identity filter matches plans where equipment contains ALL selected identity IDs
- [ ] EGO filter matches plans where equipment contains ALL selected EGO IDs
- [ ] Gift filter matches plans where union of all 4 gift paths contains ALL selected gift IDs
- [ ] Theme pack filter matches plans where floorSelections contains ALL selected theme pack IDs
- [ ] Multiple filter categories compose with AND
- [ ] Filtering is instant (no loading state needed for 1-5 plans)

## Verification

### Automated
- [ ] Index population: Unit test that publishes a plan with known content, asserts correct rows in `planner_content_index`
- [ ] Index idempotency: Upsert same published plan twice, assert row count unchanged
- [ ] Unpublish cleanup: Unpublish plan, assert zero index rows
- [ ] Specification composition: Integration test with multiple filters, verify AND semantics
- [ ] FIND_IN_SET keyword matching: Test exact match, verify no substring false positives
- [ ] FE extraction utilities: Unit tests for extractIdentityIds, extractEgoIds, extractGiftIds, extractThemePackIds with known MDPlannerContent
- [ ] FE local filter: Unit test composing multiple filters against mock plan array, verify AND semantics

### Manual
1. Publish a plan with identities, EGOs, gifts, and theme packs
2. Open published planner list, expand filter pane
3. Select a keyword — verify results narrow
4. Select an identity icon — verify results narrow further (AND)
5. Copy URL, open in new tab — verify same filters and results
6. Unpublish the plan, refresh search — verify plan no longer appears
7. Open personal plan list, expand filter pane
8. Select an identity that exists in one of your plans — verify only matching plans shown
9. Add a keyword filter on top — verify AND composition
10. Copy personal list URL with filters, open in new tab — verify same filters applied
11. Test on mobile viewport — verify 60vh cap and category scrolling

### Edge Cases
- [ ] Plan with no equipment (all empty): publishes successfully, not returned by entity filters
- [ ] Filter by identity that no plan uses: returns empty results (published) or no matches (personal), not an error
- [ ] 10+ filters simultaneously: BE query completes within acceptable latency
- [ ] Concurrent publish of two plans: both indexed correctly without conflicts
- [ ] Personal plan with partially filled content: extraction handles missing equipment/floor entries gracefully
