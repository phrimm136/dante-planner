# Results: Keyword Browser — Initialization

## What Was Done

- Data split: `battleKeywords.json` split into spec (`battleKeywordSpecList.json`) + i18n (name+desc only)
- Python pipeline: `identity.py`, `ego.py`, `gift.py` modified to generate backlinks and trim i18n output
- Frontend data layer: `KeywordSchemas.ts`, types, `useKeywordListData.ts`, `useKeywordDetailData.ts`
- `useBattleKeywords` refactored to merge spec + i18n (adapter pattern, zero consumer changes)
- `SearchableMultiSelect` copied from feat branch + 3 entity dropdown wrappers
- `CompactBuffTypeFilter` for Positive/Negative/Neutral toggle
- Keyword list page: `KeywordPage.tsx` + `KeywordList.tsx` + `KeywordCard.tsx` + `KeywordCardLink.tsx` + `KeywordName.tsx`
- Keyword detail page: `KeywordDetailPage.tsx` with icon+name row, bordered backlinks, bordered description
- `keyword` presets added to `ListPageSkeleton` and `DetailPageSkeleton`
- Routes `/keyword` and `/keyword/$id` registered
- "Keyword" nav item added to Database dropdown (all 4 languages)
- i18n labels added to `common.json` and `database.json` (all 4 languages)
- Constants: `CARD_GRID.KEYWORD`, `BUFF_TYPES`
- Removed `pendingComponent` from all database routes (identity, ego, egoGift, keyword) to fix double-skeleton
- Test suites: 5 test files, 37 tests (schemas + hooks)
- Code review: ACCEPTABLE (41/50), all should-fix items addressed

## Files Changed

### Modified (keyword-specific)
- `frontend/src/hooks/useBattleKeywords.ts` — refactored to merge spec + i18n
- `frontend/src/schemas/BattleKeywordsSchemas.ts` — removed iconId/buffType from entry schema
- `frontend/src/schemas/index.ts` — added keyword schema exports
- `frontend/src/types/KeywordTypes.ts` — added BattleKeywordSpecEntry, BattleKeywordListItem
- `frontend/src/types/StartBuffTypes.ts` — added BattleKeywordI18nEntry, restored BattleKeywordEntry
- `frontend/src/lib/constants.ts` — added CARD_GRID.KEYWORD, BUFF_TYPES
- `frontend/src/lib/router.tsx` — added keyword routes, removed all pendingComponents
- `frontend/src/components/HeaderNav.tsx` — added keyword nav item
- `frontend/src/components/common/DetailPageSkeleton.tsx` — added keyword preset
- `frontend/src/components/common/ListPageSkeleton.tsx` — added keyword card preset
- `static/scripts/identity.py` — backlink collection + trimmed i18n output
- `static/scripts/ego.py` — ego backlink appending + trimmed i18n output
- `static/scripts/gift.py` — gift backlink appending + spec list generation + trimmed i18n output
- `static/i18n/{EN,JP,KR,CN}/battleKeywords.json` — trimmed to name+desc
- `static/i18n/{EN,JP,KR,CN}/common.json` — added header.nav.keyword
- `static/i18n/{EN,JP,KR,CN}/database.json` — added keyword filter/label keys

### Created
- `static/data/battleKeywordSpecList.json` — 370 keywords with iconId, buffType, backlinks
- `frontend/src/schemas/KeywordSchemas.ts`
- `frontend/src/hooks/useKeywordListData.ts`
- `frontend/src/hooks/useKeywordDetailData.ts`
- `frontend/src/routes/KeywordPage.tsx`
- `frontend/src/routes/KeywordDetailPage.tsx`
- `frontend/src/components/keyword/KeywordCard.tsx`
- `frontend/src/components/keyword/KeywordCardLink.tsx`
- `frontend/src/components/keyword/KeywordList.tsx`
- `frontend/src/components/keyword/KeywordName.tsx`
- `frontend/src/components/common/SearchableMultiSelect.tsx`
- `frontend/src/components/ui/command.tsx`
- `frontend/src/components/filter/IdentitySearchDropdown.tsx`
- `frontend/src/components/filter/EGOSearchDropdown.tsx`
- `frontend/src/components/filter/EGOGiftSearchDropdown.tsx`
- `frontend/src/components/filter/CompactBuffTypeFilter.tsx`
- `frontend/src/schemas/__tests__/KeywordSchemas.test.ts`
- `frontend/src/schemas/__tests__/BattleKeywordsSchemas.test.ts`
- `frontend/src/hooks/__tests__/useKeywordListData.test.tsx`
- `frontend/src/hooks/__tests__/useKeywordDetailData.test.tsx`
- `frontend/src/hooks/__tests__/useBattleKeywords.test.tsx`

## Verification

- TypeScript: pass (zero errors)
- Tests: pass (37/37)
- Manual: list page loads with cards, filters work, detail page shows icon+name+backlinks+description

## Issues & Resolutions

- **Backlink IDs as numbers** — Node.js temp script wrote entity IDs as numbers, Zod expected strings. Fixed by converting to strings, then deprecated temp script in favor of Python pipeline.
- **Invalid keyword keys with spaces** — bracket scanner captured non-keyword bracketed text (e.g., `[Blade Lineage Mentor Meursault Only]`). Fixed by filtering against raw game data keys in Python pipeline.
- **Double skeleton loading** — `pendingComponent` (route chunk loading) + `Suspense fallback` (data loading) showed skeleton twice. Fixed by removing all `pendingComponent` from database routes.
- **Border inconsistency on cards** — `selectable` CSS gold glow conflicted with buffType colored `border-2`. Fixed by using `border border-border` (subtle) for selectable, moved buffType color to name text.
- **Identity/EGO dropdown missing sinner names** — "LCB Sinner" repeated 12 times without distinction. Fixed by appending `- {sinnerName}` via `getSinnerFromId` + `sinnerNames` i18n.
- **Detail page layout wrong** — icon/name stacked vertically, missing bordered panels. Fixed to flex row + `border rounded p-4` on metadata and description.
- **Python i18n output not trimmed in identity.py/ego.py** — Zod `.strict()` would reject extra fields if run standalone. Fixed by trimming to name+desc in all 3 scripts.

## Learnings

- **Adapter pattern for data splits**: When splitting a data source, update the hook to merge both sources internally rather than changing all consumers. `useBattleKeywords` merged spec+i18n with zero downstream changes.
- **Pipeline ordering matters for appending**: Each Python script must own its domain and append only its field. gift.py doing everything breaks when identity.py runs alone.
- **`selectable` is the design system primitive**: Project has a CSS class for all interactive hover effects. Using custom hover styles creates visual inconsistency.
- **Bracket scanning is noisy**: Not everything in `[brackets]` is a keyword — game descriptions have arbitrary bracketed text. Must cross-reference against known keyword keys from raw game data.

## Spec Divergence

### What Changed
- **Spec: "Colored border based on buffType"** → Changed to buffType color on name text, `border border-border` on card. Reason: `selectable` CSS glow conflicted with colored borders.
- **Spec: "Progressive rendering start 50, add 50"** → Kept at 50/50 but matches EGOGiftList pattern of CSS hidden class toggling.
- **Spec: "`ListPageSkeleton` with preset 'keyword' or reuse 'egoGift'"** → Created dedicated `keyword` preset for both list and detail skeletons.

### What Was Added (Not in Spec)
- **`KeywordCardLink.tsx`** — separate navigation wrapper following EGOGiftCardLink pattern. Spec only mentioned KeywordCard.
- **Removed `pendingComponent` from ALL database routes** — spec only concerned keyword routes, but the double-skeleton bug affected identity/ego/egoGift too.
- **`keyword` preset in `DetailPageSkeleton`** — spec used egoGift preset; dedicated one needed for backlinks layout.
- **Sinner name enrichment in dropdowns** — spec mentioned "full identity name from identityNameList" but didn't specify appending sinner name for disambiguation.
- **Test suites** — 5 test files with 37 tests. Not in original spec.

### What Was Dropped
- **Sort order** — spec didn't define sort order. Currently uses `localeCompare` on key name (English internal key). Needs discussion for localized sorting.
- **Python pipeline execution** — scripts modified but not run via full pipeline. Current `battleKeywordSpecList.json` was generated by a temp Node.js script (now deleted). Needs regeneration via Python pipeline.

### Wrong Assumptions
- **"~1100 battle keywords"** → Actual count is 370 (after filtering to only keywords in raw game data). The 1100 number included non-keyword bracketed text.
- **"Keywords without iconId use their key name as icon path"** → Many keywords have null iconId AND their key name doesn't match any icon file. These show broken images.
- **"Data split is safe with Zod .strict()"** → identity.py and ego.py wrote full entries (with iconId/buffType) to i18n files, which Zod .strict() would reject. Had to trim output in all 3 scripts.

### Prompting Retrospective

- **Data validation**: "What happens if identity.py runs standalone — does the i18n output still pass Zod validation?"
  - Would have surfaced the strict schema vs un-trimmed output mismatch before implementation.
- **Bracket scanning scope**: "How many unique bracketed strings exist in all i18n files? Are they all valid keyword IDs?"
  - Would have revealed that ~240 bracketed strings are not keywords, preventing garbage data.
- **Double skeleton**: "When a lazily-loaded page also has Suspense fallback, what loading sequence does the user see?"
  - Would have caught the double-skeleton UX issue during spec, not after visual testing.
- **Icon availability**: "How many keywords in battleKeywords.json actually have corresponding icon files?"
  - Would have surfaced the broken icon issue for keywords with null iconId and no matching file.

### Spec Process Takeaway
The spec missed **pipeline execution semantics** — how scripts run independently vs in sequence, and what invariants each step must maintain for downstream consumers (Zod schemas, other scripts).
