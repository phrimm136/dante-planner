# Execution Plan: StartBuff Summary/Edit Mode Separation

## Planning Gaps

**None.** Research complete and verified:
- Assets exist: `startBuffMini.webp`, `startBuffMiniHighlight.webp`
- Patterns verified: `EGOGiftCard`, `EGOGiftEnhancementIndicator`
- Line numbers confirmed for viewMode removal

## Execution Overview

**Strategy:** Foundation first (constants, helpers) → New component → Refactor existing → Integration → Tests

**Key insight:** `StartBuffEditPane.tsx` already works correctly. Main change is `StartBuffSection.tsx` switching to mini cards.

---

## Execution Order

### Phase 1: Foundation

1. **`lib/constants.ts`**: Add MD6_ACCENT_COLOR
   - Depends on: none
   - Enables: F1 (cyan text color)
   - Action: Add `export const MD6_ACCENT_COLOR = '#00ffcc'`

2. **`lib/assetPaths.ts`**: Add mini card asset helpers
   - Depends on: none
   - Enables: F2 (background/hover images)
   - Action: Add `getStartBuffMiniPath()`, `getStartBuffMiniHighlightPath()`

### Phase 2: New Component

3. **`StartBuffMiniCard.tsx`**: Create mini card (NEW)
   - Depends on: Steps 1-2
   - Enables: F3 (compact display)
   - Pattern: `EGOGiftCard.tsx`, `EGOGiftEnhancementIndicator.tsx`
   - Layout: Background → Icon (top) → Name+suffix (bottom) → Indicator (top-right) → Hover overlay

### Phase 3: Refactor

4. **`StartBuffCard.tsx`**: Remove viewMode
   - Depends on: none (parallel with Step 3)
   - Enables: F4 (SRP - edit-only)
   - Lines: 23, 48, 60, 65, 85, 155-168

5. **`StartBuffSection.tsx`**: Summary view with mini cards
   - Depends on: Step 3
   - Enables: F5 (summary), F6 (empty state), F7 (click opens dialog)
   - Changes: Import mini card, filter to selected, flex-wrap layout, placeholder

6. **`StartBuffEditPane.tsx`**: Remove viewMode={false}
   - Depends on: Step 4
   - Enables: F8 (clean edit pane)
   - Line: 60

### Phase 4: Integration

7. **`PlannerMDNewPage.tsx`**: Remove viewMode prop
   - Depends on: Step 5
   - Enables: F9 (integration complete)
   - Line: 590

### Phase 5: Tests

8. **`StartBuffMiniCard.test.tsx`**: Unit tests (NEW)
   - Depends on: Step 3
   - Tests: background, icon, name+suffix, indicator, hover

9. **`StartBuffSection.test.tsx`**: Update tests
   - Depends on: Steps 5, 7
   - Tests: empty state, click handler, mini card rendering

---

## Verification Checkpoints

| After Step | Verify |
|------------|--------|
| 2 | Asset helpers return correct paths |
| 3 | Mini card renders in isolation |
| 4 | Edit pane works without viewMode |
| 5 | Summary shows mini cards, empty state works |
| 7 | Full workflow: select → mini card appears |
| 8-9 | All tests pass |

---

## Rollback Strategy

**Safe stopping points:** After Step 2, 4, 7

| If Fails | Action |
|----------|--------|
| Step 3 | Fix isolated component |
| Step 4 | Revert StartBuffCard viewMode removal |
| Step 5 | Revert to original grid rendering |
| Integration | Check prop threading |
