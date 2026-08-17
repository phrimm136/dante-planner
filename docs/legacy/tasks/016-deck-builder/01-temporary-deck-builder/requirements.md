# Task: Standalone Deck Builder Page

## Description

Create a temporary deck builder page at `/planner/deck` that allows users to experiment with deck configurations without saving to their planner.

**Core behaviors:**
- Page loads with default equipment for all 12 sinners (base identity + ZAYIN EGO)
- Equipment state resets on every page visit (no persistence to IndexedDB or server)
- Import/Export via clipboard using existing deck code format
- Truly standalone - isolated from the main planner editor

**User interactions:**
- Toggle deployment order for each sinner (add/remove from deployment)
- Switch between Identity and EGO tabs to browse equipment
- Filter by sinner, keyword, or search text
- Click card to select equipment, hover for tier/level selector
- Import: read deck code from clipboard, show confirmation dialog with warnings
- Export: encode current deck to clipboard
- Reset Order: clear deployment order

**Future extension support:**
- Multiple deck configurations on same page
- Rest bonus calculation
- Deck comparison features

## Research

- `DeckBuilderPane.tsx` - current dialog-based deck builder (600+ lines to extract)
- `usePlannerEditorStore.tsx` - Zustand store with `createDefaultEquipment()` function
- `PlannerMDNewPage.tsx` - pattern for page with ephemeral store provider
- `PlannerMDEditorContent.tsx` lines 386-429 - import/export handler patterns
- `lib/deckCode.ts` - `encodeDeckCode()` and `decodeDeckCode()` utilities
- Progressive rendering system in DeckBuilderPane (snapshot sorting, scroll preservation)

## Scope

Files to READ for context:
- `frontend/src/components/deckBuilder/DeckBuilderPane.tsx`
- `frontend/src/components/deckBuilder/DeckBuilderSummary.tsx`
- `frontend/src/stores/usePlannerEditorStore.tsx`
- `frontend/src/routes/PlannerMDNewPage.tsx`
- `frontend/src/routes/PlannerMDEditorContent.tsx`
- `frontend/src/lib/deckCode.ts`
- `frontend/src/lib/router.tsx`

## Target Code Area

Files to CREATE:
- `frontend/src/routes/DeckBuilderPage.tsx` - route page with store provider
- `frontend/src/components/deckBuilder/DeckBuilderContent.tsx` - extracted core UI

Files to MODIFY:
- `frontend/src/lib/router.tsx` - add `/planner/deck` route
- `frontend/src/components/deckBuilder/DeckBuilderPane.tsx` - refactor to use DeckBuilderContent

## System Context (Senior Thinking)

- **Feature domain**: Planner (MD) - see architecture-map "Quick Reference: Core Files by Feature"
- **Core files in this domain**:
  - `routes/PlannerMDEditorContent.tsx` (shared editor)
  - `components/deckBuilder/*` (Summary+Pane pattern)
  - `stores/usePlannerEditorStore.tsx` (Zustand state)
- **Cross-cutting concerns**:
  - Zustand store provider pattern
  - TanStack Query data hooks (useIdentityListData, useEGOListData)
  - i18n via useTranslation
  - Progressive rendering with requestAnimationFrame

## Impact Analysis

- **Files being modified**:
  - `router.tsx` (High impact) - all navigation depends on this
  - `DeckBuilderPane.tsx` (Low impact) - domain-isolated component
- **What depends on these files**:
  - router.tsx: all Link components, route navigation
  - DeckBuilderPane: PlannerMDEditorContent only
- **Potential ripple effects**:
  - router.tsx: route ordering matters for specificity
  - DeckBuilderPane refactor must preserve exact behavior for existing planner editor
- **High-impact files to watch**: Do not modify store structure (usePlannerEditorStore)

## Risk Assessment

- **Edge cases not yet defined**:
  - What happens if user has invalid data in clipboard on import?
  - Should filter state persist during session (localStorage) or reset with equipment?
- **Performance concerns**:
  - Progressive rendering (10 cards/frame) must be preserved
  - Sorting snapshot system prevents re-sort jank during filtering
  - Scroll position restoration is timing-sensitive
- **Backward compatibility**:
  - DeckBuilderPane refactor must not break existing planner editor dialog
  - `isDialogMode` prop differentiates behavior for snapshot initialization
- **Security considerations**: None (no auth, no server calls)

## Testing Guidelines

### Manual UI Testing

1. Navigate to `/planner/deck`
2. Verify page loads with 12 sinners, each with default identity and ZAYIN EGO
3. Click on a sinner in the grid to toggle deployment
4. Verify deployment order number appears on sinner card
5. Click "Reset Order" button
6. Verify all deployment numbers are cleared
7. Switch to "EGO" tab
8. Verify EGO cards are displayed
9. Select the "Yi Sang" sinner filter
10. Verify only Yi Sang EGOs are shown
11. Click an EGO card
12. Verify tier selector popup appears on hover
13. Select a different threadspin level
14. Verify EGO is equipped and scroll position is preserved
15. Click "Export" button
16. Verify success toast appears
17. Open a text editor and paste - verify deck code is copied
18. Modify some equipment
19. Click "Import" button
20. Verify confirmation dialog appears with previous deck
21. Click "Confirm"
22. Verify equipment reverts to imported state
23. Navigate to `/planner` then back to `/planner/deck`
24. Verify equipment is reset to defaults (not persisted)

### Automated Functional Verification

- [ ] Default equipment: All 12 sinners have base identity (ID pattern 1XX01) and ZAYIN EGO (ID pattern 2XX01)
- [ ] Store isolation: Equipment changes do not affect main planner editor state
- [ ] Progressive rendering: 100+ cards render without blocking UI (batched via requestAnimationFrame)
- [ ] Filter persistence: Filters persist when switching Identity/EGO tabs
- [ ] Scroll preservation: Scroll position maintained after equipment change

### Edge Cases

- [ ] Empty clipboard: Import shows error toast "Invalid deck code"
- [ ] Invalid deck code: Import shows error toast, no state change
- [ ] Partial deck code (warnings): Import shows confirmation with warning list
- [ ] Maximum deployment: Can deploy up to 12 sinners
- [ ] ZAYIN unequip blocked: Cannot unequip ZAYIN EGO (required slot)

### Integration Points

- [ ] Router: `/planner/deck` route accessible from navigation
- [ ] Data hooks: Identity and EGO list data loads correctly (shared TanStack Query cache)
- [ ] i18n: All labels translated correctly
- [ ] Deck code: Export/Import compatible with existing planner deck codes
