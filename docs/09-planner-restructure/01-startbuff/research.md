# Research: Start Buff View + Edit Pane

## Spec Ambiguities

**None found.** Spec is clear and well-defined.

---

## Spec-to-Code Mapping

| Requirement | Current Code | Modification |
|-------------|--------------|--------------|
| View mode cards (no buttons) | `StartBuffCard` has EnhancementButton hard-coded (lines 131-142) | Add `viewMode` prop, conditionally render |
| View mode disables selection | `StartBuffCard.handleCardClick()` always toggles | When `viewMode=true`, call `onClick` prop instead |
| Main page read-only section | `StartBuffSection` used directly in PlannerMDNewPage | Add `viewMode` + `onClick` props |
| Edit pane with Dialog | No edit pane exists | Create `StartBuffEditPane` wrapper |
| Dialog state management | No dialog state for buff section | Add state in PlannerMDNewPage |
| Cost display | `displayBuff.cost` shown (line 99) | No change needed |
| Enhancement suffix | `getEnhancementSuffix()` used (line 116) | No change needed |

---

## Spec-to-Pattern Mapping

| Requirement | Pattern Source | Application |
|-------------|----------------|-------------|
| View + Edit Pane | `FloorThemeGiftSection.tsx` | State → viewer (read-only) + pane (interactive) |
| Dialog wrapper | `ThemePackSelectorPane.tsx` | DialogContent, DialogHeader, DialogTitle structure |
| viewMode prop | New pattern | `viewMode: boolean` prop on Card and Section |
| Section click handler | `FloorThemeGiftSection.handleOpenThemePackPane()` | onClick on viewer opens pane |
| Dialog state | `FloorThemeGiftSection` useState pattern | `isStartBuffPaneOpen` state |

---

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `StartBuffEditPane.tsx` | `ThemePackSelectorPane.tsx` | Dialog structure, close callback |
| (modifications) | `FloorThemeGiftSection.tsx` | View + pane pattern, onClick handlers |

---

## Existing Utilities

| Category | Location | Available |
|----------|----------|-----------|
| Dialog | `components/ui/dialog.tsx` | Dialog, DialogContent, DialogHeader, DialogTitle, DialogClose |
| Constants | `lib/constants.ts` | SECTION_STYLES |
| Hooks | `hooks/useStartBuffData.ts` | useStartBuffData(), getBaseBuffs() |
| Types | `types/StartBuffTypes.ts` | EnhancementLevel, StartBuff, BattleKeywords |

---

## Gap Analysis

- **Missing:** `StartBuffEditPane.tsx` (new dialog wrapper)
- **Modify:** `StartBuffCard.tsx` - add `viewMode` prop
- **Modify:** `StartBuffSection.tsx` - add `viewMode`, `onClick` props
- **Modify:** `PlannerMDNewPage.tsx` - add pane state + Dialog
- **Reuse:** Dialog components, existing Section/Card components

---

## Testing Requirements

### Manual UI Tests
- Main page: 10 cards WITHOUT enhancement buttons
- Main page: Click card opens dialog (not toggle selection)
- Dialog: Enhancement buttons visible and functional
- Dialog: Card click toggles selection
- Dialog close: Main page reflects updated state

### Automated Tests

**Unit:**
- `StartBuffCard viewMode=true` → no EnhancementButton renders
- `StartBuffCard viewMode=true` → onClick does not toggle selection
- `StartBuffSection` passes `viewMode` to all children

**Integration:**
- Dialog state controlled by parent
- Selection persists after dialog close
- Auto-save captures changes

---

## Technical Constraints

- **API stability:** Add new props as optional with defaults (`viewMode = false`)
- **State management:** No backend changes; state remains in PlannerMDNewPage
- **Dialog pattern:** Use `open` + `onOpenChange` props from shadcn/ui
- **Type safety:** Extend existing Props interfaces

---

## Implementation Sequence

1. `StartBuffCard.tsx` - add `viewMode` prop
2. `StartBuffSection.tsx` - add `viewMode`, `onClick` props
3. `StartBuffEditPane.tsx` - create dialog wrapper
4. `PlannerMDNewPage.tsx` - integrate pane state + Dialog

**Dependencies:** 1 & 2 parallel → 3 → 4
