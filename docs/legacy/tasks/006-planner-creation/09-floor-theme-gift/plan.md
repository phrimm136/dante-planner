# Plan: Floor Theme Gift

## Execution Overview

This task implements floor sections for the planner with theme pack selection and floor-specific EGO gift viewing. The architecture follows existing patterns: parent-owned state (PlannerMDNewPage), callback-based selection (StartGiftSection pattern), Suspense/useSuspenseQuery data fetching, and modular component composition. Key complexity areas: floor-difficulty rules, theme pack filtering by dungeonIdx/selectableFloors, and EGO gift filtering by themePack field.

## Execution Order

### Phase 1: Data Layer (Foundation)

1. **[constants.ts]**: Add DIFFICULTY_LABELS, DIFFICULTY_COLORS, FLOOR_COUNTS constants
   - Depends on: none
   - Enables: F1, F3, F4

2. **[ThemePackTypes.ts]**: Create ThemePackConfig, ExceptionCondition, ThemePackEntry, FloorThemeSelection interfaces
   - Depends on: none
   - Enables: F1, F2, F4

3. **[ThemePackSchemas.ts]**: Create Zod schemas for themePackList.json validation
   - Depends on: step 2
   - Enables: F1, F4

4. **[EGOGiftSchemas.ts]**: Add themePack field to EGOGiftSpecSchema (array of string IDs)
   - Depends on: none
   - Enables: F5

5. **[EGOGiftTypes.ts]**: Add themePack field to EGOGiftSpec interface
   - Depends on: step 4
   - Enables: F5

6. **[useThemePackListData.ts]**: Create data hook with useSuspenseQuery and Zod validation
   - Depends on: steps 2, 3
   - Enables: F1, F4

### Phase 2: Asset Layer

7. **[assetPaths.ts]**: Add getThemePackImagePath(), getThemePackFramePath(), getThemePackHighlightPath() helpers
   - Depends on: none
   - Enables: F2

8. **[Image Generation Script]**: Script to compose 150+ theme pack images from layers (pack + boss + icons + logo + frame)
   - Depends on: step 7
   - Enables: F2, E1

### Phase 3: Component Layer

9. **[DifficultyIndicator.tsx]**: Component showing NORMAL/HARD/INFINITY MIRROR/EXTREME MIRROR with colors
   - Depends on: step 1
   - Enables: F3

10. **[ThemePackViewer.tsx]**: Component showing selected theme pack (pre-composed image + text overlay + attribute icons)
    - Depends on: steps 6, 7, 8
    - Enables: F2

11. **[ThemePackSelectorPane.tsx]**: Dialog with difficulty tabs and filtered theme pack list
    - Depends on: steps 6, 9, 10
    - Enables: F4

12. **[FloorGiftViewer.tsx]**: Component showing floor-specific selected EGO gifts (read-only display)
    - Depends on: existing EGOGiftListData hook
    - Enables: F5

13. **[FloorGiftSelectorPane.tsx]**: Dialog using EGOGiftSelectionList with theme pack filtering
    - Depends on: steps 4, 5, existing EGOGiftSelectionList
    - Enables: F5

14. **[FloorThemeGiftSection.tsx]**: Container component for single floor (ThemePackViewer left + FloorGiftViewer right)
    - Depends on: steps 9, 10, 11, 12, 13
    - Enables: F1, I1

### Phase 4: Integration Layer

15. **[DeckTypes.ts]**: Add FloorThemeSelection interface for planner state
    - Depends on: step 2
    - Enables: I1

16. **[PlannerMDNewPage.tsx]**: Add per-floor state (selectedThemePackId, selectedDifficulty, selectedFloorGiftIds)
    - Depends on: step 15
    - Enables: I1

17. **[PlannerMDNewPage.tsx]**: Render FloorThemeGiftSection for each floor based on category (5/10/15)
    - Depends on: steps 14, 16
    - Enables: I1

18. **[i18n/*/common.json]**: Add placeholder text translations for empty states
    - Depends on: none
    - Enables: E2

## Verification Checkpoints

- After step 6: Verify F1 (data loading) - Console log themePackList data in hook, check no validation errors
- After step 8: Verify F2 (theme pack display) - Visual inspection of generated images match game
- After step 11: Verify F4 (theme pack selection) - Click theme pack placeholder, see filtered list by floor/difficulty
- After step 13: Verify F5 (gift filtering) - Select theme pack, see only matching/empty themePack gifts
- After step 17: Verify I1 (full integration) - E2E test: select category, select theme pack per floor, select gifts, verify state

## Rollback Strategy

- Steps 1-6: Data layer is additive-only, no rollback needed
- Step 8 (image script): Keep original layer assets, regenerate if needed
- Steps 9-14: Components are isolated, can remove without affecting existing features
- Steps 15-17: State changes are additive to PlannerMDNewPage, can revert to previous state shape
- Safe stopping points: After step 6 (data ready), After step 14 (components ready), After step 17 (full integration)

## Critical Files

- `frontend/src/routes/PlannerMDNewPage.tsx` - Core integration point
- `frontend/src/components/startGift/StartGiftSection.tsx` - Pattern to follow
- `frontend/src/components/egoGift/EGOGiftSelectionList.tsx` - Reuse for gift filtering
- `static/data/themePackList.json` - Data source for filtering logic
- `frontend/src/lib/constants.ts` - Add difficulty constants
