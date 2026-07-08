# MD Planner Editor Consolidation - Execution Plan

## Planning Gaps

None identified. Research is complete and spec is clear.

## Execution Overview

Extract 1150 lines of editor UI from `PlannerMDNewPage.tsx` into `PlannerMDEditorContent.tsx` component that accepts `mode` and optional `planner` props. Refactor new page into thin wrapper, implement edit page with data loading. Core pattern: conditional draft recovery + state initialization from props.

**Strategy**: Component extraction → new page refactor → edit page implementation → tests. Progressive rendering optimization and auto-save remain unchanged.

## Dependency Analysis (Senior Thinking)

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `PlannerMDNewPage.tsx` | Medium | usePlannerConfig, usePlannerSave, usePlannerStorage, useAuthQuery, all section components | Route definition in router.tsx, direct navigation to `/planner/md/new` |
| `PlannerMDEditPage.tsx` | Low | useSavedPlannerQuery, PlannerMDEditorContent | Route definition in router.tsx, PlannerCardContextMenu navigation |
| `PlannerMDEditorContent.tsx` (new) | Medium | Same as PlannerMDNewPage minus routing | PlannerMDNewPage, PlannerMDEditPage |

### Ripple Effect Map

- **If PlannerMDEditorContent props change** → Both wrapper pages need updates
- **If usePlannerSave hook changes** → Both modes affected (unlikely - stable interface)
- **If state initialization fails** → Edit mode shows empty editor (need error handling)
- **If draft recovery logic breaks** → Only new mode affected (isolated to mode check)
- **If useSavedPlannerQuery returns null** → Edit page must show 404 error

### High-Risk Modifications

- **PlannerMDNewPage.tsx**: Risk: Breaking existing draft recovery behavior
  - Mitigation: Extract UI verbatim, keep recovery logic in EditorContent with `mode === 'new'` guard

- **State initialization in edit mode**: Risk: Planner loads async after useState initializes
  - Mitigation: Use `useEffect` with `planner` dependency to sync state when data arrives

- **Progressive rendering**: Risk: Copying requestAnimationFrame logic incorrectly
  - Mitigation: Extract entire effect verbatim, verify visibleSections state works in both modes

## Execution Order

### Phase 1: Preparation (Read Reference Files)

1. **Read PlannerMDNewPage.tsx completely (1220 lines)**: Understand all state hooks, effects, UI structure
   - Depends on: none
   - Enables: F1 (new mode unchanged)

2. **Read PlannerMDDetailPage.tsx**: Understand Suspense + error handling pattern
   - Depends on: none
   - Enables: F2 (edit mode loading)

3. **Verify usePlannerSave interface**: Confirm `initialPlannerId` parameter exists and works
   - Depends on: none
   - Enables: F2 (edit mode saves)

### Phase 2: Extract Shared Component

4. **Create PlannerMDEditorContent.tsx**: Extract 1150 lines of UI from PlannerMDNewPage
   - Depends on: Step 1 (understand source structure)
   - Enables: F1, F2 (shared editor logic)
   - **Pattern source**: PlannerMDNewPage.tsx lines 209-1220 (entire component)
   - **Actions**:
     - Copy all imports from PlannerMDNewPage
     - Copy KeywordSelector component verbatim
     - Copy createDefaultEquipment and createDefaultSkillEAState functions
     - Create props interface: `{ mode: 'new' | 'edit', planner?: SaveablePlanner }`
     - Copy all useState hooks (15+)
     - Add useEffect to initialize state from `planner` prop when `mode === 'edit'`
     - Copy draft recovery dialog with guard: `showRecoveryDialog && mode === 'new'`
     - Copy all section rendering (DeckBuilder, StartBuff, EGOGift, FloorTheme, NoteEditor)
     - Copy progressive rendering effect
     - Pass `initialPlannerId` to usePlannerSave when `planner?.metadata.id` exists

5. **Write unit tests for PlannerMDEditorContent**: Test mode prop behavior
   - Depends on: Step 4 (component created)
   - Enables: UT1, UT2 (component renders in both modes)
   - **Actions**:
     - Test: renders with `mode="new"`, no planner prop → default state
     - Test: renders with `mode="edit"`, planner prop → state initialized from planner
     - Test: draft recovery dialog shows only when `mode="new"`
     - Mock: usePlannerSave, usePlannerConfig, usePlannerStorage

### Phase 3: Refactor New Page

6. **Reduce PlannerMDNewPage.tsx to wrapper (~20 lines)**: Call EditorContent with mode="new"
   - Depends on: Step 4 (EditorContent exists)
   - Enables: F1 (new mode works unchanged)
   - **Pattern source**: PlannerMDDetailPage.tsx (Suspense wrapper pattern)
   - **Actions**:
     - Remove all UI code (lines 209-1220)
     - Keep Suspense boundary from original
     - Render: `<PlannerMDEditorContent mode="new" />`
     - Remove KeywordSelector, default equipment functions (moved to EditorContent)

7. **Manual verification: New mode unchanged**: Create planner, draft recovery, auto-save
   - Depends on: Step 6 (new page refactored)
   - Enables: F1, F3 (new mode features work)
   - **Verification**:
     - Navigate to `/planner/md/new` → loads with defaults
     - Make changes → auto-save triggers
     - Close tab → reopen → draft recovery dialog appears
     - Click "Recover" → changes restored
     - Click "Discard" → fresh state

### Phase 4: Implement Edit Page

8. **Implement PlannerMDEditPage.tsx (~30 lines)**: Load planner + render EditorContent
   - Depends on: Step 4 (EditorContent exists)
   - Enables: F2 (edit mode loads planner)
   - **Pattern source**: PlannerMDDetailPage.tsx (useSavedPlannerQuery + error handling)
   - **Actions**:
     - Extract `id` from route params: `useParams({ from: '/planner/md/$id/edit' })`
     - Load planner: `const planner = useSavedPlannerQuery(id)`
     - Error handling: If `!planner`, show 404 with link to list
     - Render: `<PlannerMDEditorContent mode="edit" planner={planner} />`
     - Wrap in Suspense with skeleton fallback
     - Wrap in ErrorBoundary

9. **Add state initialization effect to EditorContent**: Sync state when planner prop changes
   - Depends on: Step 4, 8 (EditorContent + edit page exist)
   - Enables: F2 (edit mode state initialization)
   - **Actions**:
     - Add useEffect with `[planner, mode]` dependencies
     - Guard: `if (mode !== 'edit' || !planner) return`
     - Deserialize sets: `const deserialized = deserializeSets(planner.content)`
     - Update all state hooks with planner data (copy pattern from handleContinueDraft)
     - Set published state: `setIsPublished(planner.metadata.published ?? false)`

10. **Write integration tests for edit page**: Load planner → initialize state → save
    - Depends on: Step 8, 9 (edit page + state init complete)
    - Enables: IT1, IT2 (edit mode integration)
    - **Actions**:
      - Test: Edit page loads planner data via useSavedPlannerQuery
      - Test: State initializes from loaded planner (title, category, equipment)
      - Test: Auto-save calls PUT with existing ID
      - Mock: useSavedPlannerQuery, plannerApi

### Phase 5: Tests

11. **Write unit tests for mode-conditional logic**: Draft recovery, state init
    - Depends on: Step 5 (EditorContent tests started)
    - Enables: UT1, UT2 (mode prop behavior)
    - **Actions**:
      - Test: `mode="new"` shows draft recovery when draft exists
      - Test: `mode="edit"` never shows draft recovery
      - Test: `mode="edit"` with planner prop initializes state from planner
      - Test: Sets deserialized correctly (selectedKeywords: array → Set)

12. **Manual verification: Edit mode**: Load planner, edit, save, no draft dialog
    - Depends on: Step 8, 9 (edit page implemented)
    - Enables: F2, F3, I1, I2 (all edit features)
    - **Verification**:
      - Create planner in new mode, save
      - Navigate to planner list, right-click card, click "Edit"
      - Verify: Loads to `/planner/md/{id}/edit`
      - Verify: Planner data populates (title, category, equipment)
      - Verify: NO draft recovery dialog
      - Edit field → wait 2s → verify auto-save
      - Refresh → verify changes persisted

13. **Edge case verification**: Missing planner, unauthorized, concurrent edits
    - Depends on: Step 12 (basic edit flow works)
    - Enables: E1, E2 (edge case handling)
    - **Verification**:
      - Navigate to `/planner/md/nonexistent-uuid/edit` → 404 error
      - As User B, visit User A's planner edit URL → 404 (backend ownership check)
      - Open same planner in two tabs → edit in both → verify conflict dialog

## Verification Checkpoints

- **After Step 6**: Verify F1 - New mode works unchanged (create, draft recovery, auto-save)
- **After Step 9**: Verify F2 - Edit mode loads planner and initializes state
- **After Step 12**: Verify F3, I1, I2 - No draft dialog in edit, auto-save works both modes, navigation works
- **After Step 13**: Verify E1, E2 - 404 handling, unauthorized access blocked

## Risk Mitigation

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| State initialization race condition (planner loads after useState) | Step 9 | Use useEffect with `[planner, mode]` deps to sync state when data arrives |
| Draft recovery breaks in new mode | Step 6 | Keep recovery logic in EditorContent with `mode === 'new'` guard |
| Progressive rendering breaks | Step 4 | Extract requestAnimationFrame effect verbatim, verify visibleSections state |
| Auto-save uses wrong ID in edit mode | Step 4 | Pass `initialPlannerId={planner?.metadata.id}` to usePlannerSave |
| useSavedPlannerQuery returns null | Step 8 | Add `if (!planner)` error handling before rendering EditorContent |
| Backend ownership check bypassed | Step 8 | Backend already enforces via `findPlannerOrThrow(userId, id)` - no frontend change needed |

## Pre-Implementation Validation Gate

**BEFORE Step 4 execution, verify research completed:**

| Validation Category | Check | Blocker if Missing |
|---------------------|-------|-------------------|
| **Reference Completeness** | Read all 1220 lines of PlannerMDNewPage.tsx? | YES |
| **Contract Alignment** | usePlannerSave accepts `initialPlannerId` parameter? | YES |
| **Dependency Resolution** | All child components available (DeckBuilder, StartBuff, EGOGift, FloorTheme, NoteEditor)? | YES |
| **Structure Documentation** | State hooks, effects, progressive rendering documented? | YES |
| **Pattern Sources** | PlannerMDDetailPage.tsx read for Suspense + error handling? | YES |

## Dependency Verification Steps

- **After Step 4** (EditorContent created): Test component in isolation with mock hooks
- **After Step 6** (new page refactored): Test new mode end-to-end (create → save → draft recovery)
- **After Step 8** (edit page created): Test edit mode end-to-end (load → edit → save)
- **After Step 9** (state init added): Verify state matches loaded planner (check Sets deserialized)

## Rollback Strategy

- **If Step 4 fails**: EditorContent extraction has errors
  - **Rollback**: Delete `PlannerMDEditorContent.tsx`, keep PlannerMDNewPage unchanged
  - **Safe point**: Before Step 6

- **If Step 6 fails**: New page refactor breaks draft recovery
  - **Rollback**: Restore PlannerMDNewPage from git
  - **Safe point**: Before Step 6

- **If Step 9 fails**: State initialization doesn't work in edit mode
  - **Rollback**: Remove useEffect in EditorContent, keep edit page as skeleton
  - **Safe point**: After Step 8 (edit page structure exists)
