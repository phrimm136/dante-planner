# Execution Plan: Planner Editor UI Standardization

## Execution Overview

Standardize planner editor sections using **foundation-first** approach:
- Phase 1: Constants Foundation (EMPTY_STATE)
- Phase 2: Summary Button Conversions (4 components)
- Phase 3: EditPane Reset Buttons (2 components)
- Phase 4: Layout Fixes (double-wrap, floor sections)
- Phase 5: i18n Keys (4 languages)
- Phase 6: Tests (7 test files)
- Phase 7: Verification

---

## Dependency Analysis

### Files Being Modified

| File | Impact | Depends On | Used By |
|------|--------|------------|---------|
| `lib/constants.ts` | High | None | All summary components |
| `DeckBuilderSummary.tsx` | Low | PlannerSection | PlannerMDNewPage |
| `StartBuffSection.tsx` | Low | PlannerSection | PlannerMDNewPage |
| `StartGiftSummary.tsx` | Low | PlannerSection | PlannerMDNewPage |
| `StartGiftEditPane.tsx` | Low | Dialog, Button | PlannerMDNewPage |
| `EGOGiftObservationSummary.tsx` | Low | PlannerSection | PlannerMDNewPage |
| `ComprehensiveGiftSummary.tsx` | Low | PlannerSection | PlannerMDNewPage |
| `ComprehensiveGiftSelectorPane.tsx` | Low | Dialog, EGOGiftSelectionList | PlannerMDNewPage |
| `FloorThemeGiftSection.tsx` | Medium | PlannerSection | PlannerMDNewPage (x15) |

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| `FloorThemeGiftSection.tsx` | Flex layout may break in PlannerSection | Flex is inside children; wrapper won't affect |
| `DeckBuilderSummary.tsx` | Spacing shift on container removal | Only duplicate border/bg removed |
| `ComprehensiveGiftSelectorPane.tsx` | Filter reset may surprise users | Matches Observation behavior - consistent |

---

## Execution Order

### Phase 1: Constants Foundation
1. **Add EMPTY_STATE to constants.ts** - MIN_HEIGHT + DASHED_BORDER tokens

### Phase 2: Summary Button Conversions
2. **StartBuffSection.tsx** - div→button, add dashed border to empty state
3. **StartGiftSummary.tsx** - div→button
4. **EGOGiftObservationSummary.tsx** - div→button, add dashed border
5. **ComprehensiveGiftSummary.tsx** - fix min-h-24→min-h-28, add hover

### Phase 3: EditPane Reset Buttons
6. **StartGiftEditPane.tsx** - Add Reset (left) + Done (right), clear keyword AND gifts
7. **ComprehensiveGiftSelectorPane.tsx** - Add Reset/Done + filter reset useEffect

### Phase 4: Layout Fixes
8. **DeckBuilderSummary.tsx** - Remove inner SECTION_STYLES.container
9. **FloorThemeGiftSection.tsx** - Wrap in PlannerSection, bg-muted→bg-card

### Phase 5: i18n Keys
10. **EN common.json** - Add `"floor": "Floor {{number}}"`
11. **KR common.json** - Add `"floor": "{{number}}층"`
12. **JP common.json** - Add `"floor": "{{number}}階"`
13. **CN common.json** - Add `"floor": "第{{number}}层"`

### Phase 6: Tests
14. **StartBuffSection.test.tsx** - button, empty state
15. **StartGiftSummary.test.tsx** - button
16. **StartGiftEditPane.test.tsx** - Reset/Done layout
17. **EGOGiftObservationSummary.test.tsx** - button, empty state
18. **ComprehensiveGiftSummary.test.tsx** - button, height
19. **ComprehensiveGiftSelectorPane.test.tsx** - Reset/Done, filter reset
20. **FloorThemeGiftSection.test.tsx** - PlannerSection wrapper

### Phase 7: Verification
21. **Run all tests** - `yarn test`
22. **Manual UI verification** - Per Testing Guidelines

---

## Verification Checkpoints

| After Step | Verify |
|------------|--------|
| Step 1 | EMPTY_STATE exports without errors |
| Steps 2-5 | Tab + Enter/Space opens dialogs |
| Steps 6-7 | Reset buttons clear selections |
| Step 8 | DeckBuilder single border |
| Steps 9-13 | Floor sections have h2, bg-card |
| Steps 14-20 | Tests pass |
| Step 21-22 | Full verification |

---

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| FloorTheme flex breaks | 9 | Flex inside children, unaffected by wrapper |
| DeckBuilder spacing | 8 | Only redundant border removed |
| Button layout | 2-5 | Use `w-full text-left` class |
| Filter reset UX | 7 | Matches Observation - consistent |

---

## Rollback Strategy

- **Safe stops**: After Phase 1, Phase 4, Phase 5
- **DeckBuilder fail**: Revert container removal (visual-only)
- **FloorTheme fail**: Revert PlannerSection wrapper
- **Tests fail**: Fix before proceeding
