# Research: Unified Planner Save Hook

## Spec Ambiguities
**NONE FOUND** - Spec is well-defined with clear requirements.

---

## Spec-to-Code Mapping

| Requirement | Target File | Action |
|-------------|-------------|--------|
| Single unified save hook | `hooks/usePlannerSave.ts` | CREATE - merge auto-save + manual save |
| Shared syncVersion tracking | `hooks/usePlannerSave.ts` | Central syncVersionRef for both paths |
| Typed 409 detection | `lib/api.ts` | MODIFY - add ConflictError class |
| Conflict dialog | `components/planner/ConflictResolutionDialog.tsx` | CREATE |
| Manual save fix | `routes/PlannerMDNewPage.tsx` | MODIFY - remove lines 530-571, use hook |
| Type definitions | `types/PlannerTypes.ts` | ADD - ConflictState, ConflictResolutionChoice |
| Cleanup | `hooks/usePlannerAutosave.ts` | DELETE |

---

## Spec-to-Pattern Mapping

| Requirement | Pattern Source | Notes |
|-------------|----------------|-------|
| Hook state management | `usePlannerAutosave.ts:210-360` | useState, useRef, useCallback, useEffect |
| Dirty checking | `usePlannerAutosave.ts:158-179` | stateToComparableString JSON comparison |
| Debounce | `usePlannerAutosave.ts:317-345` | setTimeout with timerRef |
| Serialization | `usePlannerAutosave.ts:94-152` | createSaveablePlanner, serializeSets |
| Dialog UI | `components/noteEditor/LinkDialog.tsx` | Dialog + DialogHeader + DialogFooter |
| Toast notifications | `routes/PlannerMDNewPage.tsx:572,575` | toast.success/error from sonner |
| i18n keys | PlannerMDNewPage pattern | `t('pages.plannerMD.conflict.*')` |

---

## Pattern Enforcement (MANDATORY)

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `hooks/usePlannerSave.ts` | `hooks/usePlannerAutosave.ts` | State refs, debounce pattern, createSaveablePlanner |
| `components/planner/ConflictResolutionDialog.tsx` | `components/noteEditor/LinkDialog.tsx` | Dialog structure, Button variants, onOpenChange |
| `lib/api.ts` (error class) | `lib/api.ts:6-13` | Extend ApiConflictError interface |

---

## Existing Utilities (CHECK BEFORE CREATING)

| Category | Location | Found | Reuse? |
|----------|----------|-------|--------|
| Serialization | `schemas/PlannerSchemas.ts` | serializeSets(), deserializeSets() | YES |
| Dirty checking | `usePlannerAutosave.ts:158` | stateToComparableString() | YES - copy |
| Device ID | `usePlannerStorage.ts` | getOrCreateDeviceId() | YES |
| Toast | sonner package | toast.success/error | YES |
| Dialog | `components/ui/dialog.tsx` | Dialog, DialogContent, etc. | YES |
| Button | `components/ui/button.tsx` | variant="outline" | YES |

---

## Gap Analysis

### Currently Missing
- Typed ConflictError class (only string matching exists)
- ConflictResolutionDialog component
- Hook callback for conflict dialog trigger
- Manual save syncVersion fix (hardcoded to 1)

### Needs Modification
- `lib/api.ts` - Add ConflictError class
- `types/PlannerTypes.ts` - Add conflict types
- `routes/PlannerMDNewPage.tsx` - Switch to usePlannerSave + dialog

### Can Reuse
- usePlannerStorageAdapter for server/IndexedDB routing
- serializeSets/deserializeSets from PlannerSchemas
- Dialog components from shadcn/ui
- Toast API from sonner

---

## Testing Requirements

### Manual UI Tests
- Basic flow: Make change → wait 2s → verify auto-save indicator
- Manual save: Click Save → verify success toast
- Conflict: Two windows, concurrent edits → verify dialog appears
- Overwrite: Click Overwrite → verify local changes saved
- Discard: Click Discard → verify server version loaded
- Network error: Disable network → verify error toast

### Automated Tests

| Test Case | File | Assertion |
|-----------|------|-----------|
| Debounce timing | usePlannerSave.test.ts | No save until 2s after change |
| Dirty checking | usePlannerSave.test.ts | No save if state unchanged |
| Manual save version | usePlannerSave.test.ts | Uses tracked syncVersion, not 1 |
| 409 triggers dialog | ConflictResolutionDialog.test.ts | Dialog opens on conflict |
| Overwrite action | usePlannerSave.test.ts | Sends syncVersion+1 |
| Discard action | usePlannerSave.test.ts | Calls loadPlanner() |
| Network error | usePlannerSave.test.ts | Shows error toast |
| Guest mode | usePlannerSave.test.ts | No 409 possible |
| Initial render | usePlannerSave.test.ts | No auto-save on mount |

---

## Technical Constraints

| Constraint | Solution |
|------------|----------|
| No manual memo | React Compiler handles optimization |
| Set serialization | Use existing serializeSets/deserializeSets |
| 409 detection fragility | Create typed ConflictError, use instanceof |
| SSE race condition | Acceptable - dialog shows, user chooses, SSE may update |
| Guest mode | No conflicts possible (IndexedDB only) |
| Backward compatibility | SaveablePlanner structure unchanged |

---

## Implementation Order

1. Types: `PlannerTypes.ts` + `api.ts` conflict error
2. Hook: `usePlannerSave.ts` - unified save logic
3. Dialog: `ConflictResolutionDialog.tsx`
4. Integration: `PlannerMDNewPage.tsx` rewiring
5. Cleanup: Delete `usePlannerAutosave.ts`
6. Tests: Unit + component tests

---

## Estimated Sizes
- `usePlannerSave.ts`: ~420 lines
- `ConflictResolutionDialog.tsx`: ~80 lines
- Total new code: ~500 lines
