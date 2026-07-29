# Task: Keyword Browser — Initialization

## Decisions

- **Naming: "Keyword"** — the game uses "키워드" (KR), "キーワード" (JP), "关键词" (CN), "Keyword" (EN) as the official in-game term. Route: `/keyword`, `/keyword/$id`. Internal domain: `keyword`.
- **Data split: battleKeywords.json** — currently pure i18n (name, desc, iconId, buffType all in language files). Split into `static/data/battleKeywordSpecList.json` (iconId, buffType, backlinks) + trimmed `static/i18n/{LANG}/battleKeywords.json` (name, desc only). Matches the project's spec/i18n separation pattern.
- **Backlink generation: build-time derivation** — `identity.py`, `ego.py`, `gift.py` scan normalized `[BracketedKeyword]` patterns in skill/passive descriptions and output reverse-index arrays in `battleKeywordSpecList.json`. Bracket-only scope (no raw text matching). Normalization handles older entities without brackets via `NORMALIZATION_TARGET_DATE`.
- **All keywords shown** — all ~1100 battle keywords displayed with progressive rendering. No arbitrary categorization filtering.
- **Layout mirrors ego gift** — list page uses `FilterPageLayout` + `ResponsiveCardGrid`. Detail page uses `DetailPageLayout` with left/right columns. Card is icon + name + colored border (~96×96).
- **Searchable dropdowns from feat branch** — copy `SearchableMultiSelect` from `feat/theme-ab-event-pages` into `main`-based branch. Create wrappers for identity/ego/ego gift dropdowns.
- **Branch off main** — new feature branch, not based on `feat/theme-ab-event-pages`.

## Description

### Data Pipeline (Python)

Modify `identity.py`, `ego.py`, `gift.py` in `static/scripts/`:

1. After the keyword normalization step, collect reverse mappings: for each `[KeywordID]` found in an entity's normalized skill/passive descriptions, record that entity's ID.
2. Aggregate across all three entity types into a single output: `static/data/battleKeywordSpecList.json`
3. Trim `static/i18n/{LANG}/battleKeywords.json` to contain only `name` and `desc` (remove `iconId` and `buffType`).

**battleKeywordSpecList.json structure:**
```json
{
  "Combustion": {
    "iconId": "CombustionIcon",
    "buffType": "Negative",
    "identities": ["10101", "10203"],
    "egos": ["20101"],
    "egoGifts": ["9001"]
  },
  "BloomingThorn": {
    "iconId": null,
    "buffType": "Positive",
    "identities": ["10512"],
    "egos": [],
    "egoGifts": []
  }
}
```

- Keywords without `iconId` use their key name as the icon path (existing behavior in `getBattleKeywordIconPath`).
- Backlink arrays contain entity IDs as strings.
- Sorted by key name for stable diffs.

**Trimmed battleKeywords.json structure:**
```json
{
  "Combustion": {
    "name": "Burn",
    "desc": "At the end of the turn, take fixed damage..."
  }
}
```

### Frontend — Data Layer

**Schemas** (`frontend/src/schemas/KeywordSchemas.ts`):
- `BattleKeywordSpecEntrySchema` — iconId (string nullable), buffType (string), identities (string[]), egos (string[]), egoGifts (string[])
- `BattleKeywordSpecListSchema` — z.record(string, BattleKeywordSpecEntrySchema)
- Update existing `BattleKeywordEntrySchema` in `BattleKeywordsSchemas.ts` — remove iconId and buffType fields (now in spec)
- `BattleKeywordNameListSchema` — z.record(string, string) for name-only lookup

**Types** (`frontend/src/types/KeywordTypes.ts`):
- Add `BattleKeywordSpecEntry`, `BattleKeywordListItem` interfaces
- `BattleKeywordListItem`: id (key name), iconId, buffType, name (optional, populated from i18n)

**Hooks** (`frontend/src/hooks/useKeywordListData.ts`):
- `keywordListQueryKeys` factory — spec(), i18n(language), nameList(language)
- `useKeywordListSpec()` — suspending, loads `battleKeywordSpecList.json`
- `useKeywordListI18n()` — suspending, loads trimmed `battleKeywords.json` by language
- `useKeywordListI18nDeferred()` — non-suspending, for search filtering
- `useKeywordNameList()` — extracts name-only map from i18n data

**Hooks** (`frontend/src/hooks/useKeywordDetailData.ts`):
- `useKeywordDetailSpec(id)` — returns single keyword spec entry
- `useKeywordDetailI18n(id)` — returns name + desc for current language

**Asset paths** (`frontend/src/lib/assetPaths.ts`):
- `getBattleKeywordIconPath` already exists — verify it handles both iconId and key-name fallback correctly

### Frontend — Routing and Navigation

**Router** (`frontend/src/lib/router.tsx`):
- Add `/keyword` route → lazy-loaded `KeywordPage`
- Add `/keyword/$id` route → lazy-loaded `KeywordDetailPage`
- Pending components: `KeywordPagePending`, `KeywordDetailPagePending`
- Detail route loader: fetch keyword name for page title

**Header nav** (`frontend/src/components/HeaderNav.tsx`):
- Add `{ key: 'keyword', path: '/keyword', labelKey: 'header.nav.keyword' }` to `NAV_STRUCTURE.database.items`

**i18n** — add nav label to all 4 language `common.json` files:
- EN: `"header.nav.keyword": "Keyword"`
- KR: `"header.nav.keyword": "키워드"`
- JP: `"header.nav.keyword": "キーワード"`
- CN: `"header.nav.keyword": "关键词"`

Also add filter/label keys to `database.json`:
- `"keyword.buffType"`: Positive/Negative/Neutral labels
- `"keyword.relatedIdentities"`, `"keyword.relatedEgos"`, `"keyword.relatedEgoGifts"`
- `"keyword.searchPlaceholder"`: search bar placeholder
- `"keyword.filterIdentity"`, `"keyword.filterEgo"`, `"keyword.filterEgoGift"`: dropdown labels

### Frontend — List Page

**Page** (`frontend/src/routes/KeywordPage.tsx`):
- Three-layer structure: outer `<Suspense>` → `KeywordPageShell` (spec-only) → `KeywordList` (grid)
- `KeywordPageShell` manages filter state:
  - `searchQuery: string`
  - `selectedBuffTypes: Set<string>` (Positive/Negative/Neutral toggle)
  - `selectedIdentities: Set<string>`
  - `selectedEgos: Set<string>`
  - `selectedEgoGifts: Set<string>`

**Filter layout** (`FilterPageLayout`):
- Primary filters: buffType toggle (3 icon/text buttons: Positive, Negative, Neutral)
- Secondary filters: `IdentitySearchDropdown`, `EGOSearchDropdown`, `EGOGiftSearchDropdown` (using `SearchableMultiSelect`)
- Search bar: matches keyword display name

**Filter logic**:
- buffType: OR within selection
- Entity dropdowns: keyword's backlink arrays checked — if any selected identity appears in `keyword.identities`, it matches. OR within each entity type, AND across entity types.
- Search: case-insensitive substring match on localized keyword name
- All filter types AND together

**Card** (`frontend/src/components/keyword/KeywordCard.tsx`):
- Memoized presentational component
- Icon centered via `getBattleKeywordIconPath(iconId ?? keyName)`
- Name below (wrapped in Suspense for i18n — `KeywordName` component)
- Colored border based on buffType: use existing `colorCode.json` mappings (Positive=yellow, Negative=red, Neutral=brown)
- Size: ~96×96 icon area, similar to EGO gift card grid dimensions
- Click navigates to `/keyword/$id`

**Grid**: `ResponsiveCardGrid` with progressive rendering (start 50, add 50 per frame via `requestAnimationFrame`)

**Skeleton**: `ListPageSkeleton` with `preset: "keyword"` — add keyword preset to existing skeleton component or reuse `"egoGift"` preset if dimensions match

### Frontend — Detail Page

**Page** (`frontend/src/routes/KeywordDetailPage.tsx`):
- Two-layer: outer `<Suspense fallback={DetailPageSkeleton}>` → `KeywordDetailContent`
- Uses `useParams` to get keyword ID
- Uses `useKeywordDetailSpec(id)` + `useKeywordDetailI18n(id)`

**Layout** (`DetailPageLayout`, 4:6 column ratio):

**Left column:**
- Keyword icon (large, centered)
- Keyword name — colored by buffType (Positive=yellow/gold, Negative=red, Neutral=brown)
- Metadata sections:
  - "Related Identities" — list of identity names as links to `/identity/$id`. Display full identity name from `identityNameList` (unique per identity — e.g., "LCB Sinner Yi Sang" vs "Seven Assoc. South Section 6 Yi Sang"). Wrapped in own Suspense boundary.
  - "Related E.G.O" — list of EGO names as links to `/ego/$id`. Wrapped in own Suspense.
  - "Related E.G.O Gifts" — list of ego gift names as links to `/ego-gift/$id`. Wrapped in own Suspense.

**Right column:**
- Description text — rendered through existing `keywordFormatter` / `FormattedKeyword` to resolve nested `[BracketedKeyword]` references within the description

### Frontend — Searchable Dropdown (Copied from feat branch)

Copy from `feat/theme-ab-event-pages`:
- `frontend/src/components/common/SearchableMultiSelect.tsx`

Create new wrappers:
- `frontend/src/components/filter/IdentitySearchDropdown.tsx` — options from `identitySpecList` + `identityNameList`, `Intl.Collator` sorting
- `frontend/src/components/filter/EGOSearchDropdown.tsx` — options from `egoSpecList` + `egoNameList`
- (EGOGiftSearchDropdown already exists in feat branch — copy it too)

Each wrapper:
- Loads spec + i18n via existing list hooks
- Maps to `{ value: id, label: name }` options
- Sorts by `Intl.Collator(i18n.language, { sensitivity: 'base' })`
- Progressive rendering (batch 50 items)

### Constants

**`frontend/src/lib/constants.ts`:**
- Add `KEYWORD` entry to `CARD_GRID.WIDTH` and `CARD_GRID.HEIGHT` (values TBD — start with 96/120 matching EGO gift)
- Add `BUFF_TYPES` constant: `['Positive', 'Negative', 'Neutral'] as const`
- Add `BuffType` type

## Research

- [ ] Read `frontend/src/routes/EGOGiftPage.tsx` — list page pattern reference
- [ ] Read `frontend/src/routes/EGOGiftDetailPage.tsx` — detail page pattern reference
- [ ] Read `frontend/src/components/egoGift/EGOGiftCard.tsx` — card component reference
- [ ] Read `frontend/src/hooks/useEGOGiftListData.ts` — data hook pattern reference
- [ ] Read `frontend/src/hooks/useEGOGiftDetailData.ts` — detail hook pattern reference
- [ ] Read `frontend/src/components/common/DetailPageLayout.tsx` — layout component
- [ ] Read `frontend/src/components/common/FilterPageLayout.tsx` — filter layout component
- [ ] Read `frontend/src/components/common/ResponsiveCardGrid.tsx` — grid component
- [ ] Read `frontend/src/lib/assetPaths.ts` — verify `getBattleKeywordIconPath` behavior
- [ ] Read `frontend/src/hooks/useBattleKeywords.ts` — existing battleKeywords hook (will be refactored)
- [ ] Read `frontend/src/schemas/BattleKeywordsSchemas.ts` — existing schema (will be updated)
- [ ] Read `frontend/src/lib/keywordFormatter.ts` — description rendering
- [ ] Read `static/scripts/identity.py` — keyword normalization step
- [ ] Read `static/scripts/ego.py` — keyword normalization step
- [ ] Read `static/scripts/gift.py` — keyword scanning step
- [ ] Read `feat/theme-ab-event-pages:frontend/src/components/common/SearchableMultiSelect.tsx` — component to copy

## Scope

Files to READ for context:
- `frontend/src/routes/EGOGiftPage.tsx` — list page pattern
- `frontend/src/routes/EGOGiftDetailPage.tsx` — detail page pattern
- `frontend/src/components/egoGift/EGOGiftCard.tsx` — card pattern
- `frontend/src/components/egoGift/EGOGiftName.tsx` — i18n name pattern
- `frontend/src/hooks/useEGOGiftListData.ts` — data hook pattern
- `frontend/src/hooks/useEGOGiftDetailData.ts` — detail hook pattern
- `frontend/src/hooks/useBattleKeywords.ts` — existing hook to refactor
- `frontend/src/schemas/BattleKeywordsSchemas.ts` — existing schema
- `frontend/src/schemas/EGOGiftSchemas.ts` — schema pattern reference
- `frontend/src/types/KeywordTypes.ts` — existing keyword types
- `frontend/src/components/common/DetailPageLayout.tsx` — layout
- `frontend/src/components/common/FilterPageLayout.tsx` — filter layout
- `frontend/src/components/common/ResponsiveCardGrid.tsx` — grid
- `frontend/src/components/common/FormattedKeyword.tsx` — keyword rendering
- `frontend/src/components/HeaderNav.tsx` — nav structure
- `frontend/src/lib/router.tsx` — routing
- `frontend/src/lib/constants.ts` — constants
- `frontend/src/lib/assetPaths.ts` — asset paths
- `static/scripts/identity.py` — Python pipeline
- `static/scripts/ego.py` — Python pipeline
- `static/scripts/gift.py` — Python pipeline
- `static/i18n/EN/battleKeywords.json` — current data structure
- `static/i18n/EN/common.json` — nav i18n keys
- `static/i18n/EN/database.json` — filter i18n keys
- `static/data/colorCode.json` — buffType color mapping

## Target Code Area

### Python Pipeline (static submodule)

Files to MODIFY:
- `static/scripts/identity.py` — add backlink collection in keyword step
- `static/scripts/ego.py` — add backlink collection in keyword step
- `static/scripts/gift.py` — add backlink collection in keyword step

Files to CREATE:
- `static/data/battleKeywordSpecList.json` — generated output

Files to MODIFY (generated output):
- `static/i18n/{EN,JP,KR,CN}/battleKeywords.json` — trim to name+desc only

### Frontend

Files to CREATE:
- `frontend/src/routes/KeywordPage.tsx` — list page
- `frontend/src/routes/KeywordDetailPage.tsx` — detail page
- `frontend/src/components/keyword/KeywordCard.tsx` — card component
- `frontend/src/components/keyword/KeywordName.tsx` — i18n name component
- `frontend/src/components/keyword/KeywordList.tsx` — grid + filter logic
- `frontend/src/schemas/KeywordSchemas.ts` — Zod schemas for spec + i18n
- `frontend/src/hooks/useKeywordListData.ts` — list data hooks
- `frontend/src/hooks/useKeywordDetailData.ts` — detail data hooks
- `frontend/src/components/filter/IdentitySearchDropdown.tsx` — identity dropdown wrapper
- `frontend/src/components/filter/EGOSearchDropdown.tsx` — ego dropdown wrapper
- `frontend/src/components/filter/CompactBuffTypeFilter.tsx` — buffType toggle filter
- `frontend/src/components/common/SearchableMultiSelect.tsx` — copied from feat branch

Files to MODIFY:
- `frontend/src/lib/router.tsx` — add keyword routes
- `frontend/src/components/HeaderNav.tsx` — add keyword to database nav
- `frontend/src/lib/constants.ts` — add KEYWORD card grid dimensions, BUFF_TYPES
- `frontend/src/schemas/BattleKeywordsSchemas.ts` — remove iconId/buffType from i18n schema
- `frontend/src/hooks/useBattleKeywords.ts` — refactor to use trimmed i18n + spec
- `frontend/src/types/KeywordTypes.ts` — add new interfaces
- `static/i18n/EN/common.json` — add nav label
- `static/i18n/JP/common.json` — add nav label
- `static/i18n/KR/common.json` — add nav label
- `static/i18n/CN/common.json` — add nav label
- `static/i18n/EN/database.json` — add filter/label keys
- `static/i18n/JP/database.json` — add filter/label keys
- `static/i18n/KR/database.json` — add filter/label keys
- `static/i18n/CN/database.json` — add filter/label keys

## Impact Analysis

- **`useBattleKeywords` hook** — used by `keywordFormatter.ts` and `FormattedKeyword.tsx` for resolving `[BracketedKeyword]` in skill descriptions. Refactoring the schema (removing iconId/buffType from i18n) means this hook and its consumers must still work. The hook needs to merge spec (iconId, buffType) + i18n (name, desc) at query time, or consumers need to be updated to fetch from two sources.
- **`BattleKeywordsSchemas.ts`** — changing the schema affects all existing battleKeyword consumers. The `BattleKeywordEntrySchema` is used in `FormattedKeyword` popover display. Must ensure backward compatibility or update all consumers.
- **Existing keyword filters** — `KeywordFilter`, `CompactKeywordFilter`, `EGOGiftKeywordFilter` filter by the 7 STATUS_EFFECTS. These are unrelated to the new keyword page and should not be modified.
- **Static submodule** — modifying Python scripts and generated JSON files in `static/` requires committing to the submodule separately.

## Risk Assessment

- **Breaking existing keyword display**: The `FormattedKeyword` component uses battleKeywords data (name, desc, iconId, buffType) for skill description popovers. If the i18n file loses iconId/buffType, and the hook isn't updated to merge from spec, all keyword popovers in identity/ego/ego gift detail pages break.
  - Mitigation: Update `useBattleKeywords` hook to merge spec + i18n before returning, maintaining the same consumer interface.
- **Large list performance**: ~1100 cards with progressive rendering. Existing `ResponsiveCardGrid` handles this pattern for ego gifts (~300 items). 1100 is 3-4x larger.
  - Mitigation: Progressive rendering with larger batches (50 per frame), CSS `hidden` class toggling instead of re-rendering.
- **Search dropdown performance**: `SearchableMultiSelect` with 200+ identities, 100+ egos, 300+ ego gifts. cmdk handles client-side filtering efficiently; `requestAnimationFrame` batching for rendering.
- **Backlink data accuracy**: Depends on normalization quality. Older entities normalized via regex; newer ones have native brackets. Edge cases in regex normalization could produce incorrect backlinks.
  - Mitigation: The normalization pipeline has been battle-tested for existing keyword formatting. Same data, just reverse-indexed.

## Testing Guidelines

### Manual UI Testing

1. Navigate to `/keyword` — page loads with all ~1100 keyword cards in progressive grid
2. Cards show icon centered with name below and colored border (red/yellow/brown)
3. Search bar filters by keyword display name (localized)
4. BuffType filter toggles narrow by Positive/Negative/Neutral
5. Identity dropdown shows all identities sorted by current language; selecting one filters to keywords used by that identity
6. EGO dropdown filters similarly
7. EGO Gift dropdown filters similarly
8. Multiple entity selections within same type: OR logic (shows keywords from any selected entity)
9. Selections across types: AND logic (keywords must appear in all selected entity types)
10. Click a keyword card → navigates to `/keyword/$id` detail page
11. Detail page shows icon, colored name, description with resolved nested keywords
12. Detail page backlink sections show related identities/egos/gifts as clickable links
13. Click backlink → navigates to correct entity detail page
14. Switch language → names/descriptions update; search still works; sort order updates
15. Mobile: filter panel collapses; card grid scales; detail page stacks to single column
16. Header Database dropdown includes "Keyword" link

### Automated Functional Verification

- [ ] Route `/keyword` loads without error
- [ ] Route `/keyword/Combustion` loads detail page without error
- [ ] `battleKeywordSpecList.json` validates against `BattleKeywordSpecListSchema`
- [ ] Trimmed `battleKeywords.json` validates against updated `BattleKeywordsSchema`
- [ ] Existing `FormattedKeyword` popover still works (regression — most critical)
- [ ] All existing identity/ego/ego gift pages render correctly (no broken keyword display)
- [ ] Search filters correctly by localized name
- [ ] BuffType filter shows correct subset
- [ ] Entity dropdown filter matches backlink data
- [ ] Progressive rendering completes for full ~1100 card list

### Edge Cases

- [ ] Keyword with empty backlinks (no related entities) — detail page shows empty sections gracefully
- [ ] Keyword with null iconId — falls back to key name for icon path
- [ ] Keyword with very long description — right column scrolls or wraps
- [ ] Nested `[BracketedKeyword]` in description — resolves correctly via keyword formatter
- [ ] Search with special characters — no regex injection
- [ ] All entity types selected (identity + ego + gift) — AND logic filters correctly
- [ ] Language switch during active search — search resets or re-evaluates against new names
- [ ] Direct URL navigation to `/keyword/NonExistentKeyword` — shows error page
