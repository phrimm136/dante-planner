# Execution Plan: EGO Gift Observation Summary + EditPane Refactor

## Planning Gaps

**NONE** - Research is complete, spec is clear, all patterns and utilities exist.

---

## Execution Overview

Refactor EGO Gift Observation section from inline component to Summary + EditPane pattern:

1. Create `EGOGiftObservationSummary.tsx` (pattern: StartBuffSection)
2. Create `EGOGiftObservationEditPane.tsx` (pattern: StartBuffEditPane)
3. Update `PlannerMDNewPage.tsx` (replace section, add state)
4. Delete `EGOGiftObservationSection.tsx` (after verification)

**Total:** 2 created, 1 modified, 1 deleted

---

## Dependency Analysis

### Files Being Modified

| File | Impact | Depends On | Used By |
|------|--------|------------|---------|
| PlannerMDNewPage.tsx | High | Summary, EditPane (new) | Planner route |
| EGOGiftObservationSection.tsx | Low (delete) | N/A | Remove from imports |

### Files Being Created

| File | Pattern Source | Used By |
|------|---------------|---------|
| EGOGiftObservationSummary.tsx | StartBuffSection | PlannerMDNewPage |
| EGOGiftObservationEditPane.tsx | StartBuffEditPane | PlannerMDNewPage |

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| PlannerMDNewPage.tsx | High-impact (~720 lines) | Minimal changes: 1 state, swap import, add EditPane |
| EGOGiftObservationSelection.tsx | Reuse only | Do NOT modify - verify props match |
| EGOGiftSelectionList.tsx | Reuse only | Do NOT modify - verify props match |

---

## Execution Order

### Phase 1: Create Components (Parallel)

| Step | File | Action | Features |
|------|------|--------|----------|
| 1 | components/egoGift/EGOGiftObservationSummary.tsx | CREATE | F1-F4 |
| 2 | components/egoGift/EGOGiftObservationEditPane.tsx | CREATE | F5-F11 |

### Phase 2: Integration

| Step | File | Action | Features |
|------|------|--------|----------|
| 3 | routes/PlannerMDNewPage.tsx | MODIFY | I1-I4 |

### Phase 3: Cleanup

| Step | File | Action |
|------|------|--------|
| 4 | components/egoGift/EGOGiftObservationSection.tsx | DELETE |

---

## Verification Checkpoints

### After Step 2 (Components Created)
- Both files compile (no TypeScript errors)
- Exports match expected pattern

### After Step 3 (Integration Complete)
- `yarn build` passes
- Navigate to `/planner/md/new` - section renders
- Click section → EditPane opens
- Select/deselect gifts works
- Cost updates correctly (0/70/160/270)
- Filters work in EditPane
- Close dialog → filters reset, selection persists

### After Step 4 (Cleanup Complete)
- No import errors
- `yarn build` passes

---

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| Data format change | 3 | Format unchanged (Set<string>), no migration |
| Autosave breaks | 3 | Verify draft save/load cycle |
| Performance regression | 2 | EditPane suspends on dialog open (improvement) |
| Backward compatibility | All | observationGiftIds data shape preserved |

---

## Rollback Strategy

### Safe Stopping Points
- After Step 2: Components exist but not integrated. Planner works as before.
- After Step 3: Full integration. Can revert by restoring old imports.

### If Step 3 Fails
1. Revert PlannerMDNewPage.tsx changes
2. Keep new components for debugging
3. Old section continues to work

### If Step 4 Fails
1. Do not delete - file likely still referenced
2. Search for remaining imports: `grep -r "EGOGiftObservationSection"`
