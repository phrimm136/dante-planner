# Execution Plan: DeckBuilder Popup Pane Refactor

## Planning Gaps
**NONE** - Research complete, implementation-ready.

## Execution Overview
Refactor DeckBuilder from monolithic to Summary+Pane pattern in 7 phases, 14 steps.

---

## Execution Order

### Phase 1: Types and State Setup (Non-breaking)

**1. Add DeckFilterState interface to DeckTypes.ts**
- Depends on: none
- Enables: F1 (Filter persistence)
- File: `frontend/src/types/DeckTypes.ts`

**2. Add lifted state to PlannerMDNewPage.tsx**
- Depends on: Step 1
- Enables: F1, F5 (Pane state)
- Action: Add `isDeckPaneOpen` and `deckFilterState` useState hooks
- Keep existing `<DeckBuilder>` rendering (no breaking changes)

### Phase 2: Shared Components

**3. Create DeckBuilderActionBar.tsx**
- Depends on: none
- Enables: F2 (Button consistency), F6, F7, F8
- Action: Extract Import/Export/Reset Order buttons from DeckBuilder.tsx lines 365-377
- Props: onImport, onExport, onResetOrder, showEditDeck?, onEditDeck?
- File: `frontend/src/components/deckBuilder/DeckBuilderActionBar.tsx`

### Phase 3: Summary Component

**4. Create DeckBuilderSummary.tsx**
- Depends on: Step 3
- Enables: F3 (Summary display), F2 (Button position)
- Pattern: StartBuffSection.tsx
- Action: Wrap SinnerGrid + StatusViewer + DeckBuilderActionBar in PlannerSection
- File: `frontend/src/components/deckBuilder/DeckBuilderSummary.tsx`

### Phase 4: Pane Component

**5. Create DeckBuilderPane.tsx**
- Depends on: Step 3
- Enables: F4 (Pane editing), F1, F9 (Entity mode)
- Pattern: StartBuffEditPane.tsx
- Action: Dialog with SinnerGrid, StatusViewer, ActionBar (same position), filters, entity grids
- Use lifted filterState props (not internal state)
- File: `frontend/src/components/deckBuilder/DeckBuilderPane.tsx`

### Phase 5: Integration

**6. Move handler functions to PlannerMDNewPage**
- Depends on: Steps 4, 5
- Enables: F6, F7, F8, F10 (Autosave)
- Action: Extract handleImport, handleExport, handleResetDeployment from DeckBuilder

**7. Replace DeckBuilder with Summary+Pane**
- Depends on: Step 6
- Enables: All features
- Action: Replace `<DeckBuilder>` with `<DeckBuilderSummary>` + `<DeckBuilderPane>`

**8. Import confirmation dialog in PlannerMDNewPage**
- Depends on: Step 7
- Enables: F6 (Import confirmation)
- Action: Move import confirmation Dialog from DeckBuilder to parent

### Phase 6: Cleanup and i18n

**9. Add i18n keys to common.json**
- Depends on: none (parallel)
- Enables: F11 (i18n compliance)
- Keys: deckBuilder.editDeck, deckBuilder.paneTitle

**10. Update components to use i18n**
- Depends on: Steps 4, 5, 9
- Enables: F11

**11. Delete old DeckBuilder.tsx**
- Depends on: Steps 7, 8 verified
- Action: Remove file, verify no remaining imports

**12. Update index exports (if any)**
- Depends on: Step 11
- Check: `frontend/src/components/deckBuilder/index.ts`

### Phase 7: Tests

**13. Add unit tests for DeckBuilderActionBar**
- Depends on: Step 3
- File: `frontend/src/components/deckBuilder/__tests__/DeckBuilderActionBar.test.tsx`

**14. Add integration tests for Summary+Pane**
- Depends on: Step 7
- Test: Filter persistence, equipment sync, dialog open/close
- File: `frontend/src/components/deckBuilder/__tests__/DeckBuilderIntegration.test.tsx`

---

## Verification Checkpoints

| After Step | Verify | Method |
|------------|--------|--------|
| 2 | Types compile | `yarn tsc --noEmit` |
| 3 | ActionBar renders | Manual test |
| 4 | Summary displays | Manual test |
| 5 | Pane opens/closes | Manual test |
| 7 | Full integration | Full testing checklist |
| 8 | Import from both | Test both contexts |
| 11 | No broken imports | `yarn build` |
| 14 | Tests pass | `yarn test` |

---

## Rollback Strategy

**Safe stopping points:**
- After Step 2: Types added, no UI changes
- After Step 5: Components created but not integrated
- After Step 7: Full integration complete

**If step fails:**
- Steps 1-5: Delete new files, revert type changes
- Steps 6-8: Revert PlannerMDNewPage, keep DeckBuilder.tsx
- Step 11: Restore from git if needed

**Critical:**
- Do NOT delete DeckBuilder.tsx until Steps 7-8 verified
- Do NOT modify imports until new components exist
