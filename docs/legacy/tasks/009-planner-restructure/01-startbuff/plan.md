# Plan: Start Buff View + Edit Pane

## Planning Gaps

**None.** Research is complete and spec is clear.

---

## Execution Overview

Restructure Start Buff section from direct-edit to view+pane pattern:
1. Add `viewMode` prop to existing components (backward compatible)
2. Create thin dialog wrapper that reuses existing components
3. Integrate pane state in PlannerMDNewPage

**Key Design:** Props are optional with defaults. `viewMode={false}` (default) preserves existing behavior.

---

## Execution Order

### Phase 1: Component Modifications (Parallel)

1. **StartBuffCard.tsx**: Add `viewMode` prop
   - Depends on: none
   - Enables: F1, F2
   - Add `viewMode?: boolean` (default: false)
   - Add `onViewClick?: () => void` callback
   - When `viewMode=true`: hide EnhancementButton div
   - When `viewMode=true`: call `onViewClick` instead of toggling selection

2. **StartBuffSection.tsx**: Add `viewMode` and `onClick` props
   - Depends on: none (parallel with step 1)
   - Enables: F3
   - Add `viewMode?: boolean` (default: false)
   - Add `onClick?: () => void` callback
   - Pass `viewMode` to all StartBuffCard children
   - When `viewMode=true`: section click calls `onClick`

### Phase 2: New Component

3. **StartBuffEditPane.tsx**: Create dialog wrapper
   - Depends on: Step 1, Step 2
   - Enables: F4, F5
   - Pattern: `ThemePackSelectorPane.tsx`
   - Props: `open`, `onOpenChange`, `mdVersion`, `selectedBuffIds`, `onSelectionChange`
   - Wrap `StartBuffSection` with `viewMode={false}`
   - Dialog: DialogContent > DialogHeader > DialogTitle + StartBuffSection + DialogFooter

### Phase 3: Integration

4. **PlannerMDNewPage.tsx**: Add pane state and dialog
   - Depends on: Step 3
   - Enables: F6
   - Add `isStartBuffPaneOpen` state
   - Replace `<StartBuffSection>` with `viewMode={true}` + `onClick`
   - Add `<StartBuffEditPane>` dialog after section

### Phase 4: Tests

5. **StartBuffCard.test.tsx**: Unit tests
   - Depends on: Step 1
   - Enables: UT1, UT2
   - Test viewMode hides buttons, calls onViewClick

6. **StartBuffSection.test.tsx**: Unit tests
   - Depends on: Step 2
   - Enables: UT3, UT4
   - Test viewMode propagation, onClick callback

7. **StartBuffEditPane.test.tsx**: Integration tests
   - Depends on: Step 3
   - Enables: IT1
   - Test dialog open/close, state propagation

---

## Verification Checkpoints

| After Step | Verify | Method |
|------------|--------|--------|
| 1 | F1, F2 | Unit test: EnhancementButton hidden |
| 2 | F3 | Unit test: onClick fires |
| 3 | F4, F5 | Unit test: Dialog structure |
| 4 | F6 | Manual: Full flow works |
| 5-7 | Tests pass | yarn test |

---

## Rollback Strategy

- **Safe stopping points:** After Phase 1, After Phase 2
- **All changes backward compatible:** new props have defaults
- If any step fails: revert that file only, existing behavior preserved
