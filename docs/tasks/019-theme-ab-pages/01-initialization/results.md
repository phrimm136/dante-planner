# Theme Pack & Ab-Event Pages — Results

## What Was Done

### Data Pipeline (Python)
- `gift.py`: Fusioned gift theme pack injection (24 gifts updated)
- `aggregateAbevents.py`: Major rewrite
  - `abEventSpecList.json` generation with relatedEgoGifts, relatedThemePacks, hasImage
  - `abEventNameList.json` per language (abnormality names from MirrorDungeonAbName + enemy names + image filenames)
  - `_shared.json` extended: effects, targets, keywords, affinities, unitKeywords, sinnerNames
  - `specialEventPool` included in theme pack reverse map
  - Probability-based results (`Prob_0.5` etc.) with per-result effects
  - Sub-event capture (8-digit action sub-events + 6-digit chained sub-events)
  - HP heal color normalization (#f8c200 -> #00ff9c)
  - Base effect template inclusion (strip numeric suffixes for template lookup)
  - `nextEventId` capture in personality event results (chained coin tosses)
  - Exclusion system: 902xxx, 911xxx, duplicates, sub-events
  - `lang_glob` helper for prefixed/unprefixed localize files
- `abEventImages.py`: New script, 138 webp images from 2 raw sources
- `themePack.py`: Removed unused `difficulty` field

### Frontend — Foundation
- Types: `AbEventTypes.ts` (schema-derived via z.infer)
- Schemas: `AbEventSchemas.ts` with full mechanics/i18n/shared validation
  - `AbEventSubEventSchema` with selectionEvents
  - `AbEventProbabilityResultSchema` with nextEventId
  - `AbEventI18nSchema` with subEventTexts including selectionTexts
- Constants: `AB_EVENT` card dimensions, `THEME_PACK_DIFFICULTIES`, floor constants
- Asset paths: `getAbEventImagePath()`
- Hooks: `useAbEventListData`, `useAbEventDetailData`, `useThemePackDetailData`
- `SearchableMultiSelect` component (shadcn Command + Popover)

### Frontend — Pages
- **ThemePackPage**: FilterPageLayout + difficulty/floor/ego gift filters + ThemePackList with CSS-based filtering
- **ThemePackDetailPage**: Left (card + difficulty badges + floors) + Right (exclusive gifts, exclusive events, all gifts, all events with specialEventPool)
- **AbEventPage**: FilterPageLayout + ego gift/theme pack searchable dropdowns + AbEventList
- **AbEventDetailPage**: Full recursive event rendering
  - Choice branches with narrative text
  - Coin toss sections with SUCCESS/FAILURE, adder bonuses
  - Probability-based results with per-result effects
  - Sub-event rendering (recursive: 971088->97108801->97108802, 971093->971094->971095)
  - Effect text resolution: templates, descId lookup, conditions, amounts
  - Unity color tag parsing: `ColoredText` component (consolidated from 3 duplicate implementations)
  - `CantSelectCondition` for ego gift/cost requirements
  - `<size>` tag handling
  - i18n: SUCCESS/FAILURE labels, affinity names, sinner name, adder labels

### Frontend — Shared
- `ColoredText.tsx`: Consolidated Unity rich text parser (sanitize + recursive parse + size tags)
- `abEventTextResolver.ts`: Effect/condition text resolution logic
- `AbEventChoiceBranch.tsx`: Choice + coin toss + sub-event rendering
- Skeleton presets: `themePack` and `abEvent` for list and detail pages
- Header nav: 2 new items under Database
- i18n: All 4 languages (EN/KR/JP/CN) with full translation keys

### Tests
- `themePackFilter.test.ts`: 14 tests for difficulty/floor/ego gift filtering
- `abEventFilter.test.ts`: 9 tests for ego gift/theme pack filtering
- `AbEventSchemas.test.ts`: 23 tests for all schema validations
- `ThemePackSchemas.test.ts`: 5 tests including detail schema
- All 1178 existing tests still pass

## Gap Between Plan and Implementation

The original plan (`docs/tasks/019-theme-ab-pages/01-initialization/requirements.md`) scoped Phase 1 as data pipeline + foundation scaffolding. During implementation, the scope expanded significantly:

### Planned but scope grew
- **AbEventDetailPage**: Planned as scaffold, became full recursive event renderer with 7+ rendering modes (direct effects, coin toss, probability branches, sub-events, chained selections, condition resolution, effect template substitution)
- **Data pipeline**: Planned as 3 simple scripts, became complex with probability results, sub-event chains, exclusion system, effect template normalization, and cross-referencing between 6 data sources
- **Text resolution**: Not in original plan. Required building a full template engine for Unity color tags, condition phrases, amount substitution, affinity coloring, and target/keyword i18n

### Not planned, emerged during review
- **Sub-event recursion**: 16 parent events have nested action sub-events (up to 3 levels deep). Required recursive data capture and rendering.
- **Probability-based results**: 16 events have `Prob_` conditions with per-result effects. Required separate rendering path from coin tosses.
- **`specialEventPool`**: Separate from `eventPool` in theme packs. Discovered during review, 109 events across 23 packs were missing.
- **Color tag consolidation**: 3 duplicate `parseColorTags` implementations across codebase. Consolidated into single `ColoredText.tsx`.
- **Effect `descId` lookup**: Pre-baked effect text from `ChoiceEventEffect` localize files, keyed by `descId` on individual effects. Required changing the entire effect rendering strategy from `choiceEffects` arrays to per-effect `descId` resolution.
- **`cantSelectInThisCase` conditions**: EGO gift and cost requirements on choices, with `<size>` tags in message text or generated condition display.
- **`isExtremePack` bug**: Empty `exceptionConditions` array caused `every()` to return `true`, classifying pack 1122 as extreme.
- **`difficulty` field cleanup**: Removed from `themePack.py` and schema — it was a raw game field unrelated to dungeon difficulty filtering.

### Session 2: Bug Fixes → Architecture Rework

Session 2 planned to fix the 19 remaining bugs. Scope expanded significantly:

**What grew beyond bug fixes:**
- **Conditional result system**: 2 events (901010, 901024) had non-Prob conditional results (`MpAverage_*`). Required new `conditionalResults` array on choices, new schema, new rendering path.
- **Coin toss sub-conditions**: 5 events had `Failed_*` or `Prob_*` sub-branches inside coin toss outcomes. Required new `subResults` on results, narrative text pairing per sub-condition.
- **Cumulative probability**: `ProbTimesRepeatCount_*` pattern discovered in 901023. Self-referencing `nextEventId` caused infinite recursion.
- **Pipeline refactoring**: 5 duplicated effect extraction blocks → 3 helpers (`parse_effect`, `collect_effects`, `dedup_effects`).
- **Frontend refactoring**: 616 → 532 lines. 8 extracted components (`RenderContext`, `EffectList`, `BranchCard`, etc.).
- **Unplanned removals**: Event name system, search bar.
- **Unplanned additions**: Localization (12+ i18n keys, 4 languages), filter logic change (OR→AND), layout redesign, EgoGiftSearchDropdown extraction.

**Root causes of excessive user guidance:**
1. **No data model audit.** Bugs were treated individually. A batch scan of all `resultCondition` values would have revealed 3 result types upfront.
2. **Frontend-first fixes.** Patched renderer before understanding data; user corrected to fix in pipeline 3 times.
3. **Assumptions about game mechanics.** Probability interpretation guessed instead of verified against raw data.
4. **Sequential bug investigation.** 5 bugs shared "target suffix not stripped", 3 shared "conditional results not captured" — batch investigation would have halved the work.

### What the Specification Should Have Included

**1. Data model catalog in the spec**

The spec described the output format (`abEventSpecList.json` shape) but not the input complexity. A spec section like this would have prevented most Session 1+2 divergence:

```markdown
## Raw Data Patterns

### resultCondition values (from resultList)
- `Prob_1` — always (single result, ignore)
- `Prob_0.5` — probability branch (multiple = branching results)
- `ProbTimesRepeatCount_0.125` — cumulative probability per attempt
- `MpAverage_Under0` / `MpAverage_NotLessThan25` — conditional on SP average
- `Failed_Under3` / `Failed_NotLessThan3` — coin toss failure sub-conditions

### cantSelectInThisCase values
- `HasNotEgoGift_{id}` — requires ego gift
- `HasNotEnoughCost_{amount}` — requires cost threshold

### Target format
- Base: `EveryAlly`, `RandomAlly`, `LowestHpAlly`, `ChosenPersonality`
- Suffixed: `RandomAlly_1` (strip `_1` for lookup)
- Sinner as target: `Donquixote` (normalize to `DonQuixote`)

### Effect text resolution
- `descId` → lookup in ChoiceEventEffect localize
- No `descId` → template from effect type base name (strip numeric suffixes)
- `</color=#hex>content</color>` — malformed Unity tags, strip both
```

Without this catalog, each pattern was discovered mid-implementation, triggering architecture pivots.

**2. Explicit normalization layer in the spec**

The spec should state where data cleaning happens:

```markdown
## Normalization (pipeline only, not frontend)
- Sinner key casing: `Donquixote` → `DonQuixote`, `Ryosyu` → `Ryoshu`
- Target suffixes: strip trailing `_\d+`
- Malformed color tags: strip `</color=#hex>content</color>` → `content`
- Duplicate effects: deduplicate identical entries
```

Session 2 wasted cycles fixing these in the frontend first, then moving to pipeline after user correction.

**3. Rendering mode enumeration in the spec**

The spec described "choices with effects" generically. It should have enumerated every rendering mode:

```markdown
## Choice Rendering Modes
1. Single result text + direct effects
2. Single result text + probability branches (971003: 1 narrative, 2 prob cards)
3. Multiple result texts + probability branches (1:1 text-to-branch mapping)
4. Multiple result texts + conditional branches (MpAverage_*)
5. Coin toss with SUCCESS/FAILURE + effects
6. Coin toss with sub-conditions (Failed_Under3 = different effects per failure margin)
7. Coin toss with Prob_ sub-results (equal-split, not cumulative)
8. Cumulative probability with retry (ProbTimesRepeatCount_*, self-referencing nextEventId)
9. Sub-event (8-digit action event, recursive)
10. Chained coin toss (nextEventId from coin toss result → another coin toss)
```

**4. Reference screenshots per rendering mode**

The results doc's remaining issues included event IDs but no reference images. Adding a reference screenshot per rendering mode (from the game client or wiki) eliminates ambiguity about what the output should look like.

**5. Plan structure for game data features**

For features that consume raw game data, the plan should follow:
1. **Data audit** — catalog all raw patterns with counts
2. **Normalization spec** — where each cleanup happens (pipeline vs frontend)
3. **Rendering mode enumeration** — exhaustive list with example event IDs
4. **Reference per mode** — screenshot or text description of expected output
5. **Implementation order** — pipeline → schema → rendering, not interleaved

### Theme Pack Page
- No reported bugs. Functional as designed.

## Issues Resolved (Session 2)

### Data Pipeline Fixes
- **Sinner name normalization**: `Donquixote`→`DonQuixote`, `Ryosyu`→`Ryoshu` at extraction time (target, adderInfo correctionCase)
- **Target suffix stripping**: `RandomAlly_1`→`RandomAlly` for lookup, with sinnerNames fallback
- **Duplicate effect deduplication** in coin toss results
- **Malformed color tags**: `</color=#hex>content</color>` → `content` (stripped in pipeline post-processing)
- **Sub-event effect refs**: `collect_effect_refs` now scans subEvents for descId/effect types (fixed 901034)
- **Conditional results** (`MpAverage_Under0`, etc.): new `conditionalResults` array on choices
- **Coin toss sub-conditions** (`Failed_Under3`, `Prob_*`): new `subResults` on coin toss results
- **Equal-split probability** for `Prob_*` sub-results (971036/971037: 50%/50%)
- **`Prob_1` + empty condition** combos treated as flat effects, not branching (971024)
- **Identity names** added to `_shared.json` for `SpecificPersonality` adder info
- **Event name list generation removed** — names not displayed anywhere
- **`--all-langs`/`--lang` flags removed** — always generates all languages
- **Extracted helpers**: `parse_effect`, `collect_effects`, `dedup_effects` — deduplicated 5 extraction blocks

### Frontend Bug Fixes
- **`[{0}]` bracket format** → `{sinner name}` (regex handles optional brackets)
- **Coin toss threshold** styled `text-foreground font-semibold`
- **`coinToss` i18n** fallback with `t()` (4 languages)
- **Conditional results rendering** with `formatConditionLabel` (i18n-backed: `MpAverage_*`, `Failed_*`, `Prob_*`, `ProbTimesRepeatCount_*`)
- **Sub-results in coin toss** with narrative text paired per sub-condition
- **"Nothing happened"** suppressed for self-referencing nextEventId
- **Self-reference infinite loop** prevented in SubEventBlock (901023)
- **Coin toss in parent selectionEvents** accessible from SubEventBlock (971012)
- **cantSelectInThisCase** rendered in sub-event choices (971088 cost requirements)
- **Single-narrative probability branches** (971003: 1 text + 2 probability cards)
- **901034 ego gift 991007**: sub-event descId now included in effect refs

### Frontend Refactoring
- **AbEventChoiceBranch** refactored (616→532 lines): `RenderContext`, `EffectList`, `NothingHappened`, `BranchCard`, `BranchLabel`, `ChoiceResults`, `SubEventChoice`, `CoinTossOutcome`
- **Event names removed** from cards, list, detail page, hooks, schemas
- **Search bar removed** from AbEvent list page
- **EgoGiftSearchDropdown** extracted as shared component (was duplicated in AbEventPage + ThemePackPage)
- **ThemePackCardLink** memo comparator fixed to include `packName` (language switch)
- **Difficulty/floor filter** changed from OR to AND logic
- **ThemePack detail layout**: difficulty/floors moved next to card (flex row) in left column

### Localization
- `filters.egoGift`, `filters.searchEgoGift`, `filters.searchThemePack`, `filters.floor` — all 4 languages
- `abEvent.coinToss`, `abEvent.condMpAverage*`, `abEvent.condFailed*`, `abEvent.condProb*`, `abEvent.nothingHappened` — all 4 languages
- ThemePackDetailPage: `Difficulty`, `Floors`, `Exclusive Abnormality Events` use `t()`
- AbEvent detail tab title: narrative text[:20] with truncation mark via route loader
- `SearchableMultiSelect`: locale-aware sorting via `Intl.Collator`

### Code Review Fixes
- `adderInfo` Zod schema: proper typed schema instead of `z.unknown()`
- `#a16a3b` → CSS variable `--cant-select` + Tailwind class `text-cant-select`
- `CREAM` constant used in regex via `new RegExp()` instead of hardcoded hex
- `useMemo` added to ThemePackPage EgoGiftDropdown

## Remaining Issues

### Per-Event (Deferred)
- **901008**: Battle choice shows two "win" rewards — intended game data inconsistency (no descId)
- **971016/971017**: First choice prob-based branching — needs investigation
- **971054**: First prob result in second choice — needs result text

### Enhancement Ideas
- Progressive rendering could be replaced with virtualization for very large lists
- `ProbTimesRepeatCount` cumulative probability display could show accumulated percentage

## Files Changed

### New Files (Session 1)
- `static/scripts/abEventImages.py`
- `static/data/abEventSpecList.json` (generated)
- `static/images/abEvent/*.webp` (138 files, generated)
- `frontend/src/components/abEvent/AbEventCard.tsx`
- `frontend/src/components/abEvent/AbEventCardLink.tsx`
- `frontend/src/components/abEvent/AbEventChoiceBranch.tsx`
- `frontend/src/components/abEvent/AbEventList.tsx`
- `frontend/src/components/abEvent/CantSelectCondition.tsx`
- `frontend/src/components/common/ColoredText.tsx`
- `frontend/src/components/common/SearchableMultiSelect.tsx`
- `frontend/src/components/filter/CompactDungeonDifficultyFilter.tsx`
- `frontend/src/components/filter/CompactFloorFilter.tsx`
- `frontend/src/components/themePack/ThemePackCardLink.tsx`
- `frontend/src/components/themePack/ThemePackList.tsx`
- `frontend/src/hooks/useAbEventListData.ts`
- `frontend/src/hooks/useAbEventDetailData.ts`
- `frontend/src/hooks/useThemePackDetailData.ts`
- `frontend/src/lib/abEventFilter.ts`
- `frontend/src/lib/abEventTextResolver.ts`
- `frontend/src/lib/themePackFilter.ts`
- `frontend/src/routes/AbEventPage.tsx`
- `frontend/src/routes/AbEventDetailPage.tsx`
- `frontend/src/routes/ThemePackPage.tsx`
- `frontend/src/routes/ThemePackDetailPage.tsx`
- `frontend/src/schemas/AbEventSchemas.ts`
- `frontend/src/types/AbEventTypes.ts`

### New Files (Session 2)
- `frontend/src/components/filter/EgoGiftSearchDropdown.tsx`
- `frontend/src/components/abEvent/__tests__/formatConditionLabel.test.ts`
- `frontend/src/components/common/__tests__/ColoredText.test.ts`
- `frontend/src/lib/__tests__/abEventTextResolver.test.ts`

### Test Files (Both Sessions)
- `frontend/src/lib/__tests__/abEventFilter.test.ts` (9 tests)
- `frontend/src/lib/__tests__/themePackFilter.test.ts` (14 tests, updated for AND logic)
- `frontend/src/schemas/__tests__/AbEventSchemas.test.ts` (28 tests, expanded)
- `frontend/src/schemas/__tests__/ThemePackSchemas.test.ts` (6 tests, expanded)
- `frontend/src/lib/__tests__/abEventTextResolver.test.ts` (31 tests)
- `frontend/src/components/abEvent/__tests__/formatConditionLabel.test.ts` (12 tests)
- `frontend/src/components/common/__tests__/ColoredText.test.ts` (12 tests)

### Modified Files
- `static/scripts/aggregateAbevents.py` — major rewrite + refactored helpers
- `frontend/src/lib/constants.ts` — AB_EVENT dimensions, difficulty/floor constants
- `frontend/src/lib/assetPaths.ts` — getAbEventImagePath
- `frontend/src/lib/router.tsx` — 4 new routes + ab-event detail loader
- `frontend/src/components/HeaderNav.tsx` — 2 nav items
- `frontend/src/components/common/DetailPageSkeleton.tsx` — themePack/abEvent presets, flex layout
- `frontend/src/components/common/ListPageSkeleton.tsx` — themePack/abEvent presets
- `frontend/src/components/floorTheme/ThemePackCard.tsx` — import from consolidated ColoredText
- `frontend/src/components/startBuff/formatBuffDescription.tsx` — re-export from ColoredText
- `frontend/src/schemas/ThemePackSchemas.ts` — ThemePackDetailSchema, NodeOptionSchema
- `frontend/src/schemas/index.ts` — AbEvent + ThemePackDetail exports
- `frontend/src/types/ThemePackTypes.ts` — isExtremePack empty array fix
- `frontend/src/styles/globals.css` — `--cant-select` CSS variable
- `static/i18n/*/common.json` — nav labels
- `static/i18n/*/database.json` — abEvent/themePack/filter i18n keys

## Verification
- Build: Clean (yarn tsc --noEmit passes)
- Tests: 90 files, 1244 tests pass (66 new tests across 2 sessions)
- Code review: ACCEPTABLE (4.4/5.0)

## Learnings
- Zod schemas silently strip unknown fields — every new nested data field needs schema updates in ALL parent types
- Unity color tags have malformed variants (`</color=#hex>`) — strip in pipeline, not frontend
- Game event data has 3 layers: parent events (6-digit), selection events (8-digit coin tosses), and action sub-events (6-digit or 8-digit chained events)
- `specialEventPool` is separate from `eventPool` in theme pack data
- `choiceEffects` in i18n don't map 1:1 to `directEffects` — they correspond to effects with `descId` field
- Three types of multi-result choices: probability (`Prob_*`), conditional (`MpAverage_*`), cumulative (`ProbTimesRepeatCount_*`)
- Coin toss failure branches can have sub-conditions (`Failed_Under3`) with different effects per condition
- `Prob_*` values in coin toss sub-results are NOT individual probabilities — equal-split 1/N is correct
- `ProbTimesRepeatCount_*` is cumulative probability with negative value = failure/retry
- Self-referencing `nextEventId` in sub-events causes infinite recursion — must guard in renderer
- Data normalization belongs in the pipeline (build-time), not the frontend (runtime)
- `collect_effect_refs` must scan subEvents too, not just parent choices and selectionEvents
- Game difficulty labels (Normal/Hard/Infinity/Extreme) are shown in English across all languages — exception to i18n rule
- Memo comparators that only check ID miss prop changes on language switch
