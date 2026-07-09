# Task: Theme Pack & Abnormality Event Pages - Initialization

Phase 1 of theme pack and abnormality event database pages. Covers data pipeline changes, type definitions, schemas, image extraction, route scaffolding, and the shared SearchableMultiSelect component.

## Decisions

- **Layout follows ego gift pattern** — List pages use `FilterPageLayout` + `ResponsiveCardGrid` with CSS-based visibility filtering. Detail pages use `DetailPageLayout` (4:6 desktop, stacked mobile).
- **Searchable dropdown via shadcn Command** — New `SearchableMultiSelect` using cmdk-based `Command` inside `Popover`. Separate concern from existing `ThemePackDropdown`/`SeasonDropdown` which use `DropdownMenuCheckboxItem`.
- **Build-time data aggregation** — `abEventSpecList.json` computed by Python script, includes `relatedEgoGifts[]`, `relatedThemePacks[]`, `hasImage`. Avoids loading 103 individual theme pack files on the list page.
- **Script execution order** — `themePack.py` → `gift.py` → `aggregateAbevents.py`. Each depends on the previous output.
- **Fusioned gift theme pack injection** — `gift.py` step 3 modified to detect recipes where all materials come from `specificEgoGiftPool` and inherit the theme pack(s).
- **All sections always visible** — No accordions or collapsible sections in detail pages. Users found click-to-expand tedious on other sites.
- **Difficulty includes Infinity** — Four tiers: Normal (dungeonIdx 0), Hard (1), Infinity (2), Extreme (3). Colors from existing `DIFFICULTY_COLORS`.
- **Ab-event card: title above image** — Wide landscape images (3:2) require title on y-axis. In theme pack detail context, title goes BELOW image for inline cards.
- **Ab-event detail: theme pack names, not images** — Left column shows related theme pack names as text for readability, not card images.
- **21 imageless events marked as potential** — May reuse story dungeon images; image field added to spec list as `hasImage: boolean` with `false` flagging these.

## Description

### 1. Data Pipeline Changes

#### 1a. `gift.py` - Fusioned Gift Theme Pack Injection

Modify step 3 (`theme_pack`) to also process fusioned gifts:
- After linking `specificEgoGiftPool` gifts to theme packs, iterate gifts with `recipe`
- If ALL recipe materials are in `specificEgoGiftPool`, inject the union of their theme pack IDs into the fusioned gift's `themePack[]`
- Currently 24 fusioned gifts are affected (e.g., 9212 materials=[9427,9428] from pack 1014)

#### 1b. `aggregateAbevents.py` - AbEvent Spec List + Extended Shared Data

Extend the script to produce:

**`static/data/abEventSpecList.json`** — Lightweight aggregate for list page:
```json
{
  "901001": {
    "relatedEgoGifts": ["9001", "991002"],
    "relatedThemePacks": ["1002", "1003"],
    "hasImage": true
  }
}
```

Build process:
1. Extract `relatedEgoGifts` from mechanics data: scan `choices[].directEffects[].reward.id` and `selectionEvents[].results[].effects[].reward.id`
2. Build reverse map from ALL individual `themePack/{id}.json` files: `nodeOption.eventPool[]` → event ID → theme pack ID list
3. Determine `hasImage`: check existence in both image source directories

**`_shared.json` extension** — Add `targets` and `keywords` sections from ChoiceEventTarget and ChoiceEventKeyword Localize files:
```json
{
  "effects": { ... },
  "targets": {
    "EveryAlly": "All Allies",
    "ChosenPersonality": "The Selected Identity",
    ...
  },
  "keywords": {
    "IncludeSkillAttribute": "with the [ATTRIBUTES] Sin Affinity Attack Skill",
    ...
  }
}
```

#### 1c. Image Extraction Script

New script `static/scripts/abEventImages.py`:
- Source 1: `raw/assets/Assets/Sprites/MirrorDungeon/Events/{id}_{name}.png` (1024x673, 74 files for 901xxx)
- Source 2: `raw/assets/Assets/Resources_moved/Sprite/ChoiceEvent/ChoiceEvent_{id}.png` (1367x898, 48 files for 971xxx + 6 for MD events)
- Output: `static/images/abEvent/{id}.webp` (converted, resized to consistent dimensions)
- Priority: MirrorDungeon/Events first, ChoiceEvent as fallback
- 21 events produce no image (902003, 902005, 902006, 911501, 911502, 971012-971017, 971021-971025, 971064, 971065, 971067, 971071, 971072, 971082)

### 2. Frontend Type Definitions

#### 2a. `AbEventTypes.ts`

```typescript
interface AbEventSpecListEntry {
  relatedEgoGifts: string[]
  relatedThemePacks: string[]
  hasImage: boolean
}

type AbEventSpecList = Record<string, AbEventSpecListEntry>

interface AbEventListItem {
  id: string
  name?: string
  relatedEgoGifts: string[]
  relatedThemePacks: string[]
  hasImage: boolean
}

// Mechanics (individual file)
interface AbEventChoice {
  index: number
  cantSelectInThisCase?: string
  nextEventId?: number
  directEffects?: AbEventEffect[]
}

interface AbEventEffect {
  effect: string
  target?: string
  condition?: string
  descId?: string
  reward?: { type: string; id: number; num: number; prob: number }
  nextBattleId?: number
}

interface AbEventJudgement {
  successThreshold: number
  bestThreshold: number
  affinities: string[]
}

interface AbEventResult {
  outcome: 'SUCCESS' | 'FAILURE'
  effects: AbEventEffect[]
}

interface AbEventSelectionEvent {
  canSkip: boolean
  eventType: string
  participantInfo?: { min: number; max: number }
  judgement?: AbEventJudgement
  results?: AbEventResult[]
}

interface AbEventData {
  canSkip: boolean
  eventType: string
  choices?: AbEventChoice[]
  selectionEvents?: Record<string, AbEventSelectionEvent>
  isHideHint?: boolean
}

// i18n (individual file)
interface AbEventI18n {
  name?: string
  desc?: string
  subDesc?: string
  options?: AbEventOption[]
  selectionTexts?: Record<string, AbEventSelectionText>
  choiceEffects?: Record<string, string[]>
}

interface AbEventOption {
  message: string
  messageDesc?: string
  result?: string[]
}

interface AbEventSelectionText {
  title?: string
  behaveDesc?: string
  successDesc?: string[]
  failureDesc?: string[]
}

// Shared resources
interface AbEventShared {
  effects: Record<string, string>
  targets: Record<string, string>
  keywords: Record<string, string>
}
```

#### 2b. `ThemePackTypes.ts` — No changes needed (already complete)

#### 2c. Constants additions (`constants.ts`)

```typescript
CARD_GRID.WIDTH.AB_EVENT: 308  // ~3 columns on 1024px desktop
CARD_GRID.HEIGHT.AB_EVENT: TBD // based on image aspect + title height
```

### 3. Zod Schemas

#### 3a. `AbEventSchemas.ts`

Runtime validation for:
- `AbEventSpecListSchema` — validates aggregated spec list
- `AbEventDataSchema` — validates individual mechanics file
- `AbEventI18nSchema` — validates individual i18n file
- `AbEventSharedSchema` — validates _shared.json with effects/targets/keywords

### 4. Asset Path Helpers

In `assetPaths.ts`:
```typescript
function getAbEventImagePath(eventId: string): string
  → resolveAsset(`/images/abEvent/${eventId}.webp`)
```

### 5. Route Scaffolding

In `router.tsx`:
- `/theme-pack` → `ThemePackPage` (lazy loaded)
- `/theme-pack/$id` → `ThemePackDetailPage` (lazy loaded)
- `/ab-event` → `AbEventPage` (lazy loaded)
- `/ab-event/$id` → `AbEventDetailPage` (lazy loaded)

In `HeaderNav.tsx` — add to `NAV_STRUCTURE.database.items`:
```typescript
{ key: 'themePack', path: '/theme-pack', labelKey: 'header.nav.themePack' },
{ key: 'abEvent', path: '/ab-event', labelKey: 'header.nav.abEvent' },
```

### 6. SearchableMultiSelect Component

New component: `frontend/src/components/common/SearchableMultiSelect.tsx`

Uses shadcn `Command` inside `Popover`:
- Search input with debounce
- Checkbox items for multi-selection
- `onSelect` prevents popover close
- Shows selected count on trigger button
- i18n-aware: displays localized names, searches against them
- Handles 340+ items (ego gifts) performantly via cmdk virtual list

Props:
```typescript
interface SearchableMultiSelectProps {
  options: { value: string; label: string }[]
  selectedValues: Set<string>
  onSelectionChange: (values: Set<string>) => void
  placeholder: string
  searchPlaceholder: string
  className?: string
}
```

### 7. Data Hooks (scaffolding)

- `useThemePackListData.ts` — already exists, may need extension for detail data
- `useThemePackDetailData.ts` — new, loads individual `themePack/{id}.json` + i18n
- `useAbEventListData.ts` — new, loads `abEventSpecList.json` + i18n name list
- `useAbEventDetailData.ts` — new, loads individual `abEvent/{id}.json` + i18n

## Scope (files to READ for context)

### Data Pipeline
- `static/scripts/gift.py` — step 3 theme_pack linking
- `static/scripts/aggregateAbevents.py` — current aggregation logic
- `static/scripts/themePack.py` — theme pack generation
- `static/scripts/langConfig.py` — shared config

### Frontend Patterns
- `frontend/src/routes/EGOGiftPage.tsx` — list page pattern
- `frontend/src/routes/EGOGiftDetailPage.tsx` — detail page pattern
- `frontend/src/components/common/DetailPageLayout.tsx` — two-column layout
- `frontend/src/components/filter/FilterPageLayout.tsx` — filter sidebar
- `frontend/src/components/common/ResponsiveCardGrid.tsx` — grid system
- `frontend/src/components/common/ThemePackDropdown.tsx` — existing dropdown (reference, not to modify)
- `frontend/src/components/common/SearchBar.tsx` — search bar pattern
- `frontend/src/hooks/useEGOGiftListData.ts` — data hook pattern
- `frontend/src/hooks/useEGOGiftDetailData.ts` — detail hook pattern
- `frontend/src/schemas/EGOGiftSchemas.ts` — schema pattern
- `frontend/src/types/EGOGiftTypes.ts` — type pattern
- `frontend/src/types/ThemePackTypes.ts` — existing theme pack types
- `frontend/src/lib/constants.ts` — CARD_GRID, DIFFICULTY_COLORS, DUNGEON_IDX
- `frontend/src/lib/assetPaths.ts` — asset path helpers
- `frontend/src/lib/router.tsx` — route definitions
- `frontend/src/components/HeaderNav.tsx` — nav structure

### Raw Data
- `raw/assets/Assets/Resources_moved/StaticData/static-data/abnormality-event/mirror-dungeon-action-choice-event.json`
- `raw/assets/Assets/Resources_moved/StaticData/static-data/abnormality-event/mirror-dungeon-personality-choice-event.json`
- `raw/game/LimbusCompany_Data/Assets/Resources_moved/Localize/en/EN_ChoiceEventEffect.json`
- `raw/game/LimbusCompany_Data/Assets/Resources_moved/Localize/en/EN_ChoiceEventTarget.json`
- `raw/game/LimbusCompany_Data/Assets/Resources_moved/Localize/en/EN_ChoiceEventKeyword.json`

## Target (files to CREATE or MODIFY)

### Create
- `frontend/src/types/AbEventTypes.ts`
- `frontend/src/schemas/AbEventSchemas.ts`
- `frontend/src/hooks/useAbEventListData.ts`
- `frontend/src/hooks/useAbEventDetailData.ts`
- `frontend/src/hooks/useThemePackDetailData.ts`
- `frontend/src/components/common/SearchableMultiSelect.tsx`
- `frontend/src/routes/ThemePackPage.tsx` (scaffold)
- `frontend/src/routes/ThemePackDetailPage.tsx` (scaffold)
- `frontend/src/routes/AbEventPage.tsx` (scaffold)
- `frontend/src/routes/AbEventDetailPage.tsx` (scaffold)
- `static/scripts/abEventImages.py`
- `static/data/abEventSpecList.json` (generated)
- `static/images/abEvent/*.webp` (generated)

### Modify
- `static/scripts/gift.py` — step 3 fusioned gift injection
- `static/scripts/aggregateAbevents.py` — add abEventSpecList + shared data extension
- `frontend/src/lib/constants.ts` — add AB_EVENT card dimensions
- `frontend/src/lib/assetPaths.ts` — add getAbEventImagePath
- `frontend/src/lib/router.tsx` — add 4 new routes
- `frontend/src/components/HeaderNav.tsx` — add 2 nav items
- i18n translation files — add nav labels

## Impact Analysis

- `gift.py` modification: Affects `static/data/egoGift/*.json` — fusioned gifts get updated `themePack[]`. No breaking change; additive only.
- `aggregateAbevents.py` modification: Produces new `abEventSpecList.json` + extends `_shared.json`. Existing `abEvent/{id}.json` and per-language i18n files unchanged.
- `router.tsx`: 4 new routes added. No changes to existing routes.
- `HeaderNav.tsx`: 2 new items in database category. No changes to existing items.
- `constants.ts`: New constants added. No changes to existing constants.
- `SearchableMultiSelect`: New component, no modifications to existing dropdowns.

## Risk Assessment

### Edge Cases
- Theme packs with dungeonIdx 2 and undefined selectableFloors — Infinity packs have no floor data; detail page must handle gracefully
- Fusioned gifts spanning multiple theme packs (e.g., 9416 materials from packs 1007+1008) — union of theme packs, not single pack
- Events without images (21 events) — `hasImage: false` in spec list; frontend shows placeholder or hides image area
- Events not in any theme pack (73 orphans) — `relatedThemePacks: []` is valid; filter shows them when no theme pack filter is active
- Empty `specificEgoGiftPool` (some packs have none) — detail page "specific gifts" section should hide or show "none"
- `cantSelectInThisCase` conditions reference ego gift IDs — display as informational text, not interactive
- Unity color tags in effect text (`<color=#hex>text</color>`) — must parse and convert to JSX/HTML spans
- `adderInfo` in personality events (44 events) — bonus modifiers to display in coin toss section

### Performance
- `abEventSpecList.json` must stay lightweight for list page load
- 340+ ego gifts in SearchableMultiSelect — cmdk handles virtual scrolling
- CSS-based filtering (existing pattern) prevents React reconciliation on filter changes

## Done When

- [ ] `gift.py` step 3 injects theme packs for fusioned gifts (24 gifts updated)
- [ ] `aggregateAbevents.py` generates `abEventSpecList.json` with correct relatedEgoGifts, relatedThemePacks, hasImage
- [ ] `_shared.json` includes targets and keywords sections from Localize files
- [ ] `abEventImages.py` extracts and converts images to webp (128 images from 2 sources)
- [ ] `AbEventTypes.ts` covers all mechanics/i18n/shared structures
- [ ] `AbEventSchemas.ts` validates all data files at runtime without errors
- [ ] `SearchableMultiSelect` renders, searches, and multi-selects with 340+ items
- [ ] Routes `/theme-pack`, `/theme-pack/$id`, `/ab-event`, `/ab-event/$id` resolve (scaffold pages)
- [ ] Header nav shows Theme Pack and Ab Event entries under Database
- [ ] `AB_EVENT` card dimensions added to `CARD_GRID` constants
- [ ] `getAbEventImagePath` added to `assetPaths.ts`
- [ ] All existing tests pass
- [ ] TypeScript compilation succeeds with no new errors

## Verification

### Automated
- [ ] Run `python3 static/scripts/gift.py --step theme_pack` — fusioned gifts in output have non-empty `themePack[]`
- [ ] Run `python3 static/scripts/aggregateAbevents.py --all-langs` — produces `abEventSpecList.json` with 149 entries
- [ ] Run `python3 static/scripts/abEventImages.py` — produces 128 webp files in `static/images/abEvent/`
- [ ] Frontend typecheck passes: `yarn tsc --noEmit`

### Manual
1. Navigate to `/theme-pack` — page renders (scaffold)
2. Navigate to `/ab-event` — page renders (scaffold)
3. Header Database dropdown shows Theme Pack and Ab Event links
4. Verify `abEventSpecList.json`: every event with `hasImage: true` has a corresponding webp file
5. Verify fusioned gift 9212: `themePack` contains `"1014"`
6. SearchableMultiSelect: type a gift name, see filtered results, select multiple, count updates on trigger button

### Edge Cases
- [ ] Event 971055 (has ChoiceEvent image, no MirrorDungeon image): `hasImage: true`, webp exists
- [ ] Event 971012 (no image from either source): `hasImage: false`, no webp
- [ ] Gift 9416 (materials from packs 1007+1008): `themePack` contains both
- [ ] Theme pack 1001 (empty specificEgoGiftPool): spec list entry valid, no crash
- [ ] SearchableMultiSelect with 0 options: renders empty state gracefully
