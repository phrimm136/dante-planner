# Execution Plan

## Phase Summary

Build the keyword browser feature in 6 sequential phases. Each phase produces verifiable output before the next begins. The critical integration risk is Phase 3 (refactoring `useBattleKeywords` without breaking existing consumers).

The Python pipeline changes for proper backlink generation are deferred — Phase 1 creates a placeholder `battleKeywordSpecList.json` with empty backlink arrays, populated from current `battleKeywords.json` data (extracting `iconId` and `buffType`). A follow-up task will integrate backlink generation into `identity.py`/`ego.py`/`gift.py`.

## Phases

### Phase 1: Data Files Generation
- **What**: Generate `static/data/battleKeywordSpecList.json` from current battleKeywords data. Trim all 4 language `battleKeywords.json` files to `{name, desc}` only.
- **Files**:
  - CREATE: `static/data/battleKeywordSpecList.json`
  - MODIFY: `static/i18n/EN/battleKeywords.json` (trim iconId, buffType)
  - MODIFY: `static/i18n/JP/battleKeywords.json`
  - MODIFY: `static/i18n/KR/battleKeywords.json`
  - MODIFY: `static/i18n/CN/battleKeywords.json`
- **Approach**: Write a Node.js script (`/tmp/generate-keyword-spec.js`) that reads all 4 language files, extracts `iconId` and `buffType` from the EN file (language-independent), creates the spec file with empty backlink arrays, and trims all 4 i18n files.
- **Depends on**: None
- **Verify**:
  - `battleKeywordSpecList.json` exists with all keyword entries, each having `iconId`, `buffType`, `identities: []`, `egos: []`, `egoGifts: []`
  - All 4 language `battleKeywords.json` have only `name` and `desc` per entry
  - Entry count matches between spec and i18n files

### Phase 2: Schemas + Types + Constants + i18n Labels
- **What**: Create Zod schemas for new spec data, update existing battle keyword schemas, add TypeScript types, constants, and i18n translation keys.
- **Files**:
  - CREATE: `frontend/src/schemas/KeywordSchemas.ts`
  - MODIFY: `frontend/src/schemas/BattleKeywordsSchemas.ts` (remove iconId, buffType from entry schema)
  - MODIFY: `frontend/src/schemas/index.ts` (export new schemas)
  - MODIFY: `frontend/src/types/KeywordTypes.ts` (add BattleKeywordSpecEntry, BattleKeywordListItem)
  - MODIFY: `frontend/src/types/StartBuffTypes.ts` (update BattleKeywordEntry — remove iconId, buffType)
  - MODIFY: `frontend/src/lib/constants.ts` (add CARD_GRID.KEYWORD, BUFF_TYPES)
  - MODIFY: `static/i18n/{EN,JP,KR,CN}/common.json` (add header.nav.keyword)
  - MODIFY: `static/i18n/{EN,JP,KR,CN}/database.json` (add keyword filter/label keys)
- **Depends on**: Phase 1 (needs spec file structure)
- **Verify**:
  - TypeScript compilation passes (`tsc --noEmit`)
  - Schemas parse sample data correctly
  - No import errors

### Phase 3: Data Hooks + useBattleKeywords Refactor
- **What**: Create keyword list/detail data hooks. Refactor `useBattleKeywords` to load spec + i18n and merge them, maintaining the same `BattleKeywords` return type for existing consumers.
- **Files**:
  - CREATE: `frontend/src/hooks/useKeywordListData.ts`
  - CREATE: `frontend/src/hooks/useKeywordDetailData.ts`
  - MODIFY: `frontend/src/hooks/useBattleKeywords.ts` (load spec + merge with i18n)
  - MODIFY: `frontend/src/types/KeywordTypes.ts` (update KeywordResolutionContext if needed)
- **Depends on**: Phase 2 (needs schemas and types)
- **Verify**:
  - TypeScript compilation passes
  - **CRITICAL**: `useKeywordFormatter` still returns the same `KeywordResolutionContext` shape (battleKeywords with name, desc, iconId, buffType)
  - Existing consumers (`formatBuffDescription.tsx`, `useStartBuffSelection.ts`, `useKeywordFormatter.ts`) compile without changes
  - `BattleKeywords` type still has `{ name, desc, iconId, buffType }` per entry

### Phase 4: SearchableMultiSelect + Filters + Routing + Navigation
- **What**: Copy `SearchableMultiSelect` from feat branch. Create entity search dropdown wrappers and buffType filter. Add routes and nav item.
- **Files**:
  - CREATE: `frontend/src/components/common/SearchableMultiSelect.tsx` (copy from feat branch)
  - CREATE: `frontend/src/components/filter/IdentitySearchDropdown.tsx`
  - CREATE: `frontend/src/components/filter/EGOSearchDropdown.tsx`
  - CREATE: `frontend/src/components/filter/EGOGiftSearchDropdown.tsx`
  - CREATE: `frontend/src/components/filter/CompactBuffTypeFilter.tsx`
  - MODIFY: `frontend/src/lib/router.tsx` (add keyword routes)
  - MODIFY: `frontend/src/components/HeaderNav.tsx` (add keyword to database nav)
- **Depends on**: Phase 3 (needs data hooks for dropdown data loading)
- **Verify**:
  - TypeScript compilation passes
  - Routes `/keyword` and `/keyword/$id` are registered (check router config)
  - Header nav shows "Keyword" under Database dropdown
  - SearchableMultiSelect compiles without missing dependencies

### Phase 5: Keyword List Page
- **What**: Build the keyword list page with card grid, progressive rendering, and filter integration.
- **Files**:
  - CREATE: `frontend/src/components/keyword/KeywordCard.tsx`
  - CREATE: `frontend/src/components/keyword/KeywordName.tsx`
  - CREATE: `frontend/src/components/keyword/KeywordList.tsx`
  - CREATE: `frontend/src/routes/KeywordPage.tsx`
- **Pattern source**: `EGOGiftPage.tsx`, `EGOGiftCard.tsx`, `EGOGiftName.tsx`, `EGOGiftList.tsx` (or equivalent list component)
- **Depends on**: Phase 4 (needs routes, filters, SearchableMultiSelect)
- **Verify**:
  - TypeScript compilation passes
  - `/keyword` route loads the page component
  - Cards render with icon + name + colored border
  - Search bar filters by localized name
  - BuffType filter narrows by type
  - Entity dropdowns filter by backlinks (empty for now)
  - Progressive rendering handles ~1100 items

### Phase 6: Keyword Detail Page
- **What**: Build the keyword detail page with icon, colored name, backlink sections, and description rendering.
- **Files**:
  - CREATE: `frontend/src/routes/KeywordDetailPage.tsx`
- **Pattern source**: `EGOGiftDetailPage.tsx`
- **Depends on**: Phase 5 (complete list page for navigation context)
- **Verify**:
  - TypeScript compilation passes
  - `/keyword/Combustion` loads detail page
  - Left column: icon, colored name, backlink sections (empty for now)
  - Right column: description rendered via `useKeywordFormatter` with resolved nested keywords
  - Direct URL navigation works
  - Non-existent keyword shows error page
  - Mobile layout stacks to single column

## Phase Dependencies

```
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6
```

All phases are sequential. Phase 3 is the highest-risk phase (breaking existing consumers).

## Post-Build Follow-up

- **Python pipeline integration**: Modify `identity.py`, `ego.py`, `gift.py` to generate real backlinks in `battleKeywordSpecList.json`. This is a separate task in the static submodule.
- **Regenerate data**: Run the Python pipeline to populate backlink arrays with actual entity IDs.
