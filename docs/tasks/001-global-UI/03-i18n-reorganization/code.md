# Code Documentation: i18n Reorganization

## What Was Done

- **Phase 1 Complete**: Split monolithic `common.json` into domain-specific namespaces
- Created `database.json` for entity pages (Identity, EGO, EGO Gift) - 4 languages
- Created `planner.json` for planner pages (MD planner, planner list) - 4 languages
- Updated `i18n.ts` with new namespace imports and removed `fallbackNS` cascade
- Fixed 35+ components with explicit namespace declarations (`useTranslation(['ns', 'common'])`)
- Added `scripts/validate_i18n_keys.py` for build-time key validation
- **Phase 2 Deferred**: Dynamic namespace loading moved to `docs/TODO.md` (PERF-001)

## Files Changed

**i18n Configuration:**
- `frontend/src/lib/i18n.ts`

**New Namespace Files (x4 languages each):**
- `static/i18n/{EN,JP,KR,CN}/database.json`
- `static/i18n/{EN,JP,KR,CN}/planner.json`

**Modified Namespace Files:**
- `static/i18n/{EN,JP,KR,CN}/common.json` (keys extracted)

**Components with namespace fixes:**
- `frontend/src/routes/EGOGiftPage.tsx`
- `frontend/src/routes/PlannerListPage.tsx`
- `frontend/src/routes/PlannerMDNewPage.tsx`
- `frontend/src/components/identity/TraitsDisplay.tsx`
- `frontend/src/components/noteEditor/*.tsx` (3 files)
- `frontend/src/components/plannerList/*.tsx` (5 files)
- `frontend/src/components/startBuff/*.tsx` (2 files)
- `frontend/src/components/startGift/*.tsx` (2 files)
- `frontend/src/components/skillReplacement/*.tsx` (3 files)
- `frontend/src/components/floorTheme/*.tsx` (5 files)
- `frontend/src/components/deckBuilder/*.tsx` (3 files)
- `frontend/src/components/egoGift/*.tsx` (4 planner-related files)
- `frontend/src/components/planner/ConflictResolutionDialog.tsx`

**Validation Script:**
- `scripts/validate_i18n_keys.py`

## Verification Results

- Checkpoint Phase 1: **PASS** - All pages render correctly (KR verified)
- Build: **Pre-existing errors** (unrelated to i18n changes)
- i18n Key Validation: **PASS** - All 4 languages have matching keys
- Console: **PASS** - No missing translation warnings

## Issues & Resolutions

- **Raw keys showing instead of translations** → Added `fallbackNS` initially, then removed and fixed components with explicit namespaces
- **40% components missing namespace** → Code review flagged HIGH-C1, fixed via code-writer agents
- **Mirror Dungeon translations** → Used official game translations from raw localization files
- **Phase 2 complexity vs benefit** → User deferred to TODO.md after cost/benefit analysis
