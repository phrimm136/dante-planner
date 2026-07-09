# MD Planner Editor Consolidation - Research Findings

## Spec Ambiguities

**None identified.** Spec is clear and actionable.

---

## Spec-to-Code Mapping

**New Mode (`/planner/md/new`):**
- Extract 1150 lines from `PlannerMDNewPage.tsx` → new `PlannerMDEditorContent.tsx`
- Reduce `PlannerMDNewPage.tsx` to 20-line wrapper calling editor with `mode="new"`
- Keep draft recovery dialog (conditional on mode)

**Edit Mode (`/planner/md/$id/edit`):**
- Implement `PlannerMDEditPage.tsx` (30 lines) with `useSavedPlannerQuery` + `PlannerMDEditorContent` wrapper
- Pass loaded planner as initial state
- Skip draft recovery dialog (always disabled in edit mode)
- Backend ownership check already complete via `findPlannerOrThrow(userId, id)` (line 365)

**Shared Component (`PlannerMDEditorContent.tsx`):**
- Accept `mode: 'new' | 'edit'` prop
- Accept `planner?: SaveablePlanner` prop (for edit mode)
- Conditionally render draft recovery based on mode
- Use same auto-save hook (`usePlannerSave`) with `initialPlannerId` parameter

**No Changes Needed:**
- `usePlannerSave.ts` - Already supports `initialPlannerId` for updates
- `usePlannerStorage.ts` - IndexedDB persistence works for both modes
- `useSavedPlannerQuery.ts` - Loads planners for edit mode
- Route definitions in `lib/router.tsx` - Keep `/planner/md/new` and `/planner/md/$id/edit`
- `PlannerCardContextMenu.tsx` - Edit navigation already implemented

---

## Pattern Enforcement

**MUST Read Before Implementation:**

| New File | Reference File | Pattern to Copy |
|----------|----------------|-----------------|
| `PlannerMDEditorContent.tsx` | `PlannerMDNewPage.tsx` (all 1220 lines) | UI structure, 15+ state hooks, effect setup, child props, handlers |
| `PlannerMDNewPage.tsx` (wrapper) | `routes/PlannerMDDetailPage.tsx` (lines 20-50) | Suspense + loader pattern |
| `PlannerMDEditPage.tsx` | `routes/PlannerMDDetailPage.tsx` + `routes/SettingsPage.tsx` | Ownership check + conditional rendering |

**Cross-Reference Validation:**

- PlannerState interface (usePlannerSave lines 38-65) matches all state hooks in PlannerMDNewPage ✓
- usePlannerSave supports both create (no ID) and update (with ID) ✓
- useSavedPlannerQuery returns SaveablePlanner | null ✓
- deserializeSets() converts JSON arrays to Sets (required for edit mode) ✓
- Route params extraction via useParams({ from: '/planner/md/$id/edit' }) ✓

---

## Existing Utilities (Reuse)

**State Initialization:**
- `createDefaultEquipment()` - PlannerMDNewPage line ~170
- `createDefaultSkillEAState()` - PlannerMDNewPage line ~200
- All initial useState hooks with default values - extract to shared initialization

**Data Loading:**
- `useSavedPlannerQuery(id)` - loads planner from IndexedDB/server
- `usePlannerConfig()` - version config (schemaVersion, mdCurrentVersion)
- `deserializeSets()` - schemas/PlannerSchemas.ts

**Auto-save:**
- `usePlannerSave({ state, config, initialPlannerId?, onServerReload })` - unchanged

**Draft Recovery:**
- Dialog logic lines 360-410 in PlannerMDNewPage - wrap in `if (mode === 'new')`

---

## Gap Analysis

**Currently Missing:**
- `PlannerMDEditorContent.tsx` - shared editor component (~1150 lines)
- Mode prop system to distinguish new vs edit behavior
- Conditional draft recovery based on mode
- State initialization from loaded planner (edit mode)

**Needs Modification:**
- `PlannerMDNewPage.tsx` - reduce to wrapper (~20 lines)
- `PlannerMDEditPage.tsx` - implement wrapper with loading (~30 lines)

**Can Reuse Without Changes:**
- usePlannerSave hook
- usePlannerStorage hook
- useSavedPlannerQuery hook
- usePlannerConfig hook
- All child components (DeckBuilder, StartBuff, EGOGift, FloorTheme, NoteEditor)
- Validation schemas
- Route definitions
- Navigation triggers

---

## Testing Requirements

### Manual UI Tests

**New Mode (Unchanged):**
- Navigate `/planner/md/new` → default state (empty equipment, "5F" category)
- No prior draft → no recovery dialog
- With prior draft → recovery dialog with Recover/Discard
- Recover → state restored, Discard → fresh state
- Auto-save after 2s debounce

**Edit Mode (New):**
- Navigate `/planner/md/{uuid}/edit` → planner loads (title, category, equipment)
- NO recovery dialog (even if IndexedDB has other drafts)
- Edit field → auto-save after 2s
- Refresh → loads latest server version
- Invalid UUID → 404 error with link to list
- Other user's planner → 404 from backend (ownership check)

### Automated Tests

**Unit Tests:**
- PlannerMDEditorContent renders with mode="new" and mode="edit"
- Draft recovery hidden when mode="edit"
- State initialization: new uses defaults, edit uses loaded planner
- deserializeSets converts selectedKeywords array to Set

**Integration Tests:**
- Edit page: load planner → pass to editor → state initialized from planner
- New page: draft recovery flow (both Recover and Discard)
- Auto-save: create (new mode) vs update (edit mode) API calls
- usePlannerSave with initialPlannerId parameter
- Ownership: backend returns 404 for unauthorized access

**Edge Cases:**
- Missing planner (404) → error page with link
- State timing: planner loads after useState → useEffect syncs
- Concurrent edits → conflict dialog (409) → reload server version
- Empty title → both modes handle gracefully

---

## Technical Constraints

**Dependencies:**
- React 19 + React Compiler (no manual memo, hooks rules)
- TanStack Router (lazy loading, typed params)
- TanStack Query (Suspense, error handling)
- Zod validation (deserializeSets for Sets)

**Performance:**
- Suspense waterfall: Edit mode loads planner + spec data (spec likely cached from new page)
- Progressive rendering: requestAnimationFrame optimization in EditorContent
- File size: EditorContent ~1150 lines (under 1500 limit)

**Backend Integration:**
- Ownership verification: `findPlannerOrThrow(userId, id)` returns 404 for both "not found" and "not owner"
- Frontend cannot distinguish 404 causes (treat all as missing planner)
- Auto-save: PUT `/api/planner/md/{id}` with syncVersion for optimistic locking
- Validation: PlannerContentValidator runs on all saves

**Pattern Compliance:**
- fe-component: Extract EditorContent following composition pattern
- fe-routing: Edit page follows lazy route + Suspense pattern
- fe-data: useSavedPlannerQuery follows TanStack Query + Zod validation pattern

---

## Implementation Phases

**Phase 1: Extract Shared Component**
- Create `PlannerMDEditorContent.tsx` with mode prop
- Copy all UI, state, effects from PlannerMDNewPage
- Make draft recovery conditional on mode
- Accept optional planner prop for edit mode initialization

**Phase 2: Refactor New Page**
- Reduce PlannerMDNewPage to wrapper
- Call `<PlannerMDEditorContent mode="new" />`
- Wrap in Suspense boundary

**Phase 3: Implement Edit Page**
- Add `useSavedPlannerQuery(id)` loader
- Check planner exists (404 handling)
- Optional: Check ownership (UX improvement, deferred)
- Call `<PlannerMDEditorContent mode="edit" planner={loadedPlanner} />`
- Wrap in Suspense + ErrorBoundary

**Phase 4: Testing**
- Manual UI verification (both modes)
- Unit tests for mode-conditional logic
- Integration tests for auto-save + loading

---

## Domain & Skills

**Domain:** Frontend - Mirror Dungeon Planner Editor

**Skills Required:**
- fe-component (component extraction and composition)
- fe-routing (route params and lazy loading)
- fe-data (Suspense, TanStack Query, Zod validation)

**Core Files Modified:**
- `frontend/src/routes/PlannerMDNewPage.tsx` (reduce to wrapper)
- `frontend/src/routes/PlannerMDEditPage.tsx` (implement wrapper)
- `frontend/src/routes/PlannerMDEditorContent.tsx` (new shared component)

**Impact Level:**
- PlannerMDNewPage: Medium (existing users unaffected, UI/behavior unchanged)
- PlannerMDEditPage: Low (currently skeleton, not in production)
- PlannerMDEditorContent: Medium (new shared component, must be tested thoroughly)
