# Research: DeckBuilder Popup Pane Refactor

## Spec Ambiguities
**NONE FOUND** - Specification is clear and implementation-ready.

---

## Spec-to-Code Mapping

| Requirement | File/Location | Action |
|-------------|---------------|--------|
| DeckBuilderSummary | `components/deckBuilder/DeckBuilderSummary.tsx` | CREATE - SinnerGrid + StatusViewer + ActionBar |
| DeckBuilderPane | `components/deckBuilder/DeckBuilderPane.tsx` | CREATE - Dialog with filters + entity grids |
| DeckBuilderActionBar | `components/deckBuilder/DeckBuilderActionBar.tsx` | CREATE - Shared Import/Export/Reset buttons |
| DeckFilterState type | `types/DeckTypes.ts` | ADD interface |
| Parent state management | `routes/PlannerMDNewPage.tsx` | ADD isDeckPaneOpen + deckFilterState |
| Replace DeckBuilder | `routes/PlannerMDNewPage.tsx` | REPLACE with Summary + Pane |
| i18n keys | `static/i18n/EN/common.json` | ADD editDeck, paneTitle |
| Delete old component | `components/deckBuilder/DeckBuilder.tsx` | DELETE after migration |

---

## Pattern Enforcement (MANDATORY)

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| DeckBuilderSummary.tsx | StartBuffSection.tsx | PlannerSection wrapper, onClick handler, compact display |
| DeckBuilderPane.tsx | StartBuffEditPane.tsx | Dialog + DialogContent structure, open/onOpenChange props |
| DeckBuilderActionBar.tsx | DeckBuilder.tsx | Button layout (flex, gap-2), Icon imports |

---

## Existing Utilities (REUSE)

| Category | Location | Functions |
|----------|----------|-----------|
| Import/Export | lib/deckCode.ts | encodeDeckCode, decodeDeckCode, validateDeckCode |
| Equipment handlers | DeckBuilder.tsx | handleToggleDeploy, handleEquipIdentity, handleEquipEgo, handleResetDeployment |
| Filter logic | DeckBuilder.tsx lines 132-205 | Identity/EGO filtering with sinners/keywords/search |
| Constants | lib/constants.ts | SECTION_STYLES for container styling |
| Data hooks | Already imported | useIdentityListData, useEGOListData, useSearchMappings |

---

## Gap Analysis

**Missing (CREATE):**
- DeckBuilderSummary.tsx
- DeckBuilderPane.tsx
- DeckBuilderActionBar.tsx
- DeckFilterState interface
- i18n keys: deckBuilder.editDeck, deckBuilder.paneTitle

**Modify:**
- PlannerMDNewPage.tsx - Add filter state, wire Summary+Pane
- DeckTypes.ts - Add DeckFilterState interface
- common.json - Add i18n keys

**Reuse (NO CHANGES):**
- SinnerGrid.tsx - Pure component, props-driven
- StatusViewer.tsx - Pure component, reads equipment prop
- EntityToggle.tsx, TierLevelSelector.tsx - Pane-only components
- SinnerFilter.tsx, KeywordFilter.tsx, SearchBar.tsx - Filter components

---

## Testing Requirements

### Manual UI Tests
- Main page summary renders (SinnerGrid, StatusViewer, buttons visible)
- Button positions identical in Summary and Pane
- "Edit Deck" opens Dialog, Escape/outside click closes
- Filter state persists across open/close
- Equipment changes sync to Summary immediately
- Mobile Dialog usable at 375px width

### Automated Functional Verification
- Filter persistence across popup cycles
- Equipment sync without refetch
- StatusViewer updates in both locations
- Import/Export from both Summary and Pane
- Reset Order clears deployment
- Entity mode toggle works
- Autosave triggers on equipment changes

### Edge Cases
- Empty equipment renders gracefully
- No filters shows full list
- Large screen Dialog no overflow
- Keyboard: Tab navigation, Escape closes

---

## Technical Constraints

1. **Pattern Compliance**: Follow StartBuff Summary+EditPane exactly
2. **State Lifting**: All filter state to PlannerMDNewPage parent
3. **Component Reuse**: SinnerGrid/StatusViewer identical in both locations
4. **Data Caching**: React Query shared cache (no refetch on reopen)
5. **i18n**: Use t('deckBuilder.editDeck'), t('deckBuilder.paneTitle')
6. **No Breaking Changes**: Keep existing behavior until fully migrated

---

## Implementation Order

1. Types + state setup (non-breaking)
2. DeckBuilderActionBar (shared component)
3. DeckBuilderSummary (main page view)
4. DeckBuilderPane (Dialog view)
5. Wire up in PlannerMDNewPage + i18n
6. Delete old DeckBuilder.tsx
