# Research: Floor Theme Gift

## Clarifications Resolved

- **Extreme Color**: Use `#ffffff` (white) for EXTREME MIRROR difficulty indicator
- **Position Config**: Use fixed positions matching game's default layout (no user configurability)
- **Empty State**: Do not show difficulty categories with no available theme packs
- **Persistence**: Session only - reset on page reload but persist during session
- **EGO Gift Filtering**: Show only gifts where `themePack` contains selected theme pack ID OR is empty (no gifts with other theme pack IDs)
- **Image Composition**: Pre-composed static images (generate 150+ composed images, serve single image per theme pack) except texts

## Spec-to-Code Mapping

- Floor sections container → New `FloorThemeGiftSection` component (5/10/15 sections based on planner type)
- Theme pack selector pane → New `ThemePackSelectorPane` with difficulty tabs + filtered theme pack list
- Theme pack viewer → New `ThemePackViewer` with 8-layer composite image structure
- Difficulty indicator → New `DifficultyIndicator` component (NORMAL/HARD/INFINITY/EXTREME MIRROR)
- Floor gift viewer → Extend `EGOGiftObservationSection` to show floor-specific gifts
- Planner state extension → Add per-floor: `selectedThemePackId`, `selectedDifficulty`, `selectedFloorGiftIds`
- EGO gift filtering → Show gifts where `themePack` contains selected ID OR is empty (excludes other theme IDs)
- Asset path helpers → Add to `assetPaths.ts` for theme pack images/frames/highlights
- Type definitions → Create `ThemePackTypes.ts` with ThemePackConfig, FloorThemeSelection interfaces
- Zod schema → Create `ThemePackSchemas.ts` for themePackList.json validation
- Constants → Add `DIFFICULTY_LABELS`, `DIFFICULTY_COLORS`, `FLOOR_COUNTS` to constants.ts
- TanStack Query → Create `useThemePackListData()` hook with Suspense

## Spec-to-Pattern Mapping

- State management → Follow `StartGiftSection` callback pattern (parent owns state)
- Data fetching → Use `useSuspenseQuery` with Zod validation (per skill requirements)
- Component structure → Selector + Viewer split like `EGOGiftSelectionList` + `EGOGiftObservationSection`
- Asset paths → Extend `assetPaths.ts` helper pattern
- i18n → Use `useTranslation()` hook, extend `themePack.json`
- Modal/pane → Follow shadcn/ui Dialog pattern for selector panes
- Filter pipeline → Match `EGOGiftSelectionList` keyword → search → sort pattern

## Gap Analysis

**Currently Missing:**
- ThemePackViewer, ThemePackSelectorPane, DifficultyIndicator, FloorThemeGiftSection components
- ThemePackTypes.ts and ThemePackSchemas.ts
- useThemePackListData() hook
- Asset path helpers for theme pack images
- Image generation script to compose 150+ theme pack images from layers except texts

**Needs Modification:**
- `PlannerMDNewPage` - extend state for per-floor selections
- `EGOGiftSelectionList` - add theme pack filtering (show matching ID OR empty themePack only)
- `constants.ts` - add difficulty labels/colors
- `assetPaths.ts` - add theme pack image path helpers

**Can Reuse:**
- EGOGiftSelectionList filtering logic
- EGOGiftObservationSection layout pattern
- StartGiftSection callback pattern
- useSuspenseQuery and Zod validation infrastructure

## Technical Constraints

- Zod validation required for themePackList.json before use
- useSuspenseQuery mandatory (no plain useQuery)
- No hardcoded hex colors - use constants.ts
- All state updates must create new objects/Sets (immutability)
- Floor index mapping: selectableFloors (0-4) → 1F-5F or 6F-10F based on context
- Difficulty rules: floor 1 only normal, floor 2-10 normal+hard, floor 11-15 extreme only
- Theme pack layers render order: base → boss → icons → logo → text → types → frame → highlight

## Key Data Findings

- `themePackList.json`: 150+ entries with `exceptionConditions` for floor/difficulty filtering
- `dungeonIdx` mapping: 0=normal, 1=hard, 3=extreme (no 2)
- EGO gifts filtering: show gifts where `themePack` array contains selected pack ID OR is empty
- All image assets exist in `/static/images/UI/themePack/` and `/static/images/themePacks/`
- Translation keys in `/static/i18n/{lang}/themePack.json` with optional `specialName` override
