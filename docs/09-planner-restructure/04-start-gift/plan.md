# Plan: Start Gift Summary + EditPane

## Planning Gaps

**None** - Research is complete and all patterns are documented.

## Execution Overview

Refactor StartGiftSection into Summary + EditPane pattern:
1. Create `StartGiftSummary.tsx` (view component with empty/selected states)
2. Create `StartGiftEditPane.tsx` (dialog with selection logic)
3. Update PlannerMDNewPage integration
4. Add i18n key
5. Write tests
6. Delete old StartGiftSection

---

## Execution Order

### Phase 1: Interface Layer (Components)

1. **[StartGiftSummary.tsx]**: Create summary view component
   - Depends on: none
   - Enables: F1 (summary display), F2 (empty placeholder), F3 (EA counter)
   - Pattern: `StartBuffSection.tsx` + `ThemePackPlaceholder`

2. **[StartGiftEditPane.tsx]**: Create dialog with full selection UI
   - Depends on: Step 1 (interface alignment)
   - Enables: F4 (dialog), F5-F7 (selection logic)
   - Pattern: `StartBuffEditPane.tsx` + `StartGiftSection.tsx`

### Phase 2: Integration

3. **[common.json]**: Add `selectStartGift` i18n key
   - Depends on: none
   - Enables: F2 (placeholder text)

4. **[PlannerMDNewPage.tsx]**: Update imports and integration
   - Depends on: Steps 1, 2, 3
   - Enables: I1 (full integration)
   - Changes: Add state, swap imports, update JSX

### Phase 3: Tests

5. **[StartGiftSummary.test.tsx]**: Create unit tests
   - Depends on: Step 1
   - Tests: Empty/selected states, EA counter, click handler

6. **[StartGiftEditPane.test.tsx]**: Create unit tests
   - Depends on: Step 2
   - Tests: Dialog visibility, row rendering, Done button

### Phase 4: Cleanup

7. **[StartGiftSection.tsx]**: Delete after verification
   - Depends on: Steps 4-6 complete, all tests pass
   - Action: Remove deprecated component

---

## Verification Checkpoints

| After Step | Verify | Method |
|------------|--------|--------|
| 2 | Components compile | `yarn tsc --noEmit` |
| 4 | Integration works | Manual: `/planner` page test |
| 6 | Tests pass | `yarn vitest --run` |

---

## Rollback Strategy

- **Steps 1-2**: No impact (new files only)
- **Step 4**: Revert PlannerMDNewPage, keep old import
- **Safe stop**: After Step 2 (components exist but not integrated)
- **Do NOT Step 7** until manual verification complete
