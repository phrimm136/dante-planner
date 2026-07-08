# Code: Floor Theme Gift

## What Was Done
- Created floor theme gift component hierarchy (FloorThemeGiftSection as container)
- Implemented ThemePackViewer, ThemePackPlaceholder, ThemePackSelectorPane for theme pack selection
- Implemented FloorGiftViewer, FloorGiftSelectorPane for floor-specific gift selection
- Created DifficultyIndicator component with getFloorDifficultyLabel helper
- Added difficulty rules: 1F normal only chain, 1-10F normal+hard, 11-15F extreme only
- Created data layer: ThemePackTypes, ThemePackSchemas (with Zod), useThemePackListData hook
- Added difficulty constants: DIFFICULTY_LABELS, DIFFICULTY_COLORS, FLOOR_COUNTS, DUNGEON_IDX
- Integrated floor theme state in PlannerMDNewPage (per-floor: themePackId, difficulty, giftIds)
- Added i18n translations for floor theme placeholders in EN, KR, JP, CN

## Files Changed
- frontend/src/components/floorTheme/DifficultyIndicator.tsx
- frontend/src/components/floorTheme/ThemePackViewer.tsx
- frontend/src/components/floorTheme/ThemePackSelectorPane.tsx
- frontend/src/components/floorTheme/FloorGiftViewer.tsx
- frontend/src/components/floorTheme/FloorGiftSelectorPane.tsx
- frontend/src/components/floorTheme/FloorThemeGiftSection.tsx
- frontend/src/hooks/useThemePackListData.ts
- frontend/src/types/ThemePackTypes.ts
- frontend/src/schemas/ThemePackSchemas.ts
- frontend/src/schemas/index.ts
- frontend/src/lib/constants.ts
- frontend/src/routes/PlannerMDNewPage.tsx
- static/i18n/EN/common.json, KR/common.json, JP/common.json, CN/common.json

## Verification Results
- F1-F5 (Core features): pass
- E1-E3 (Edge cases): pass
- I1 (Integration): pass
- Build: pass (TypeScript compiles without errors)

## Issues & Resolutions
- Missing i18n keys in KR/JP/CN → Added translations for floor theme keys
- Type/Schema mismatch (textColor nullable) → Changed to non-nullable after data verification
- Manual useMemo violations → Removed all useMemo (React Compiler handles optimization)
- Hardcoded fallback color → Changed to constant reference
- Missing ARIA labels → Added to all interactive buttons
- Fragile difficulty mapping → Added explicit getBaseDifficulty function with DUNGEON_IDX

## Code Review Rounds
- Round 1: NEEDS WORK (6 critical issues found)
- Round 2: NEEDS WORK (3 critical null safety issues)
- Round 3: ACCEPTABLE (critical issues fixed, remaining are minor pattern docs)
