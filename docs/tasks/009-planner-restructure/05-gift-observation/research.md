# Research: EGO Gift Observation Summary + EditPane

## Spec Ambiguities

**NONE FOUND** - Spec is clear and comprehensive.

---

## Spec-to-Code Mapping

| Requirement | Implementation | Files |
|-------------|----------------|-------|
| Summary + EditPane pattern | Extract inline section into two components | Section → Summary + EditPane |
| Cost display (right-aligned) | Copy StartBuffSection pattern (flex justify-end) | StarlightCostDisplay |
| Horizontal gift layout | flex flex-wrap gap-2 with min-h-28 | StartBuffSection line 62 |
| Clickable summary area | cursor-pointer, role="button", tabIndex={0}, keyboard handlers | StartBuffSection lines 52-59 |
| Empty state placeholder | Centered "Select EGO Gifts" text | i18n key exists |
| EditPane dialog wrapper | Dialog with max-w-[95vw] lg:max-w-[1440px] | StartBuffEditPane pattern |
| Desktop layout (9:1 grid) | col-span-9 selection, col-span-1 selected | Current section lines 110-138 |
| Mobile layout (stacked) | grid-cols-1 at <lg breakpoint | New responsive pattern |
| Local filter state | useState in EditPane, reset on close | Move from current lines 50-53 |
| Max 3 enforcement | useEffect with MAX_OBSERVABLE_GIFTS | Current lines 56-68, handler 74-76 |
| State in PlannerMDNewPage | Keep observationGiftIds, add isObservationPaneOpen | Line 242 + new state |

---

## Spec-to-Pattern Mapping

| Requirement | Pattern Source | Details |
|-------------|---------------|---------|
| Summary structure | StartBuffSection.tsx | Cost right, clickable area, empty state, horizontal flex, min-h-28 |
| EditPane structure | StartBuffEditPane.tsx | Dialog wrapper, header, footer with Reset/Done |
| Keyboard accessibility | StartBuffSection.tsx | role="button" + tabIndex={0} + onKeyDown |
| Hover effects | StartBuffSection.tsx | cursor-pointer hover:opacity-90 transition-opacity |
| Gift selection grid | EGOGiftSelectionList.tsx | Reuse as-is for EditPane left panel |
| Vertical selected display | EGOGiftObservationSelection.tsx | Reuse as-is for EditPane right panel |
| Cost calculation | Current section lines 81-83 | observationData.observationEgoGiftCostDataList.find() |

---

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| EGOGiftObservationSummary.tsx | StartBuffSection.tsx | Cost right, clickable area, empty state, horizontal flex, PlannerSection wrapper |
| EGOGiftObservationEditPane.tsx | StartBuffEditPane.tsx | Dialog structure, cost at top, footer buttons, responsive sizing |

---

## Existing Utilities

| Category | Location | Items | Status |
|----------|----------|-------|--------|
| Constants | lib/constants.ts:162 | MAX_OBSERVABLE_GIFTS = 3 | REUSE |
| Data hooks | hooks/useEGOGiftObservationData.ts | Cost data, gift data list | REUSE |
| Components | egoGift/EGOGiftObservationSelection.tsx | Vertical selected display | REUSE |
| Components | egoGift/EGOGiftSelectionList.tsx | Filtered/sorted grid | REUSE |
| Shared UI | common/StarlightCostDisplay.tsx | Cost display | REUSE |
| Shared UI | common/PlannerSection.tsx | Section wrapper | REUSE |
| Shared UI | ui/dialog.tsx | Dialog components | REUSE |

---

## Gap Analysis

- **Missing:** None - all utilities exist
- **Needs modification:** PlannerMDNewPage.tsx (import, state, component usage)
- **To delete:** EGOGiftObservationSection.tsx (after refactor)
- **Reuse unchanged:** EGOGiftSelectionList, EGOGiftObservationSelection, StarlightCostDisplay, useEGOGiftObservationData

---

## Testing Requirements

### Manual UI Tests
- Empty state: "Select EGO Gifts" placeholder centered, muted
- Summary cost: Right-aligned, updates (0/70/160/270)
- Summary click: Anywhere opens dialog, Enter/Space triggers
- Desktop EditPane: 9:1 grid visible
- Mobile EditPane: Stacked layout (selection above selected)
- Max selection: 4th gift silently ignored
- Filter reset: Close/reopen resets filters, selection persists
- Cost edge case: Missing data defaults to 0

### Automated Tests
- Summary has justify-end on cost container
- Summary div has cursor-pointer role="button"
- Empty state renders conditionally
- EditPane grid breakpoint at lg
- Max selection enforced in handler
- Cost matches lookup table
- Dialog controlled by open state
- Filters reset on dialog close

### Integration Points
- Planner autosave works (Set<string> unchanged)
- Draft loading restores observationGiftIds
- Suspense boundary wraps component
- PlannerSection inherits styling

---

## Technical Constraints

**Pattern compliance:**
- Summary MUST use StartBuffSection pattern
- EditPane MUST use StartBuffEditPane pattern
- MUST use shadcn Dialog from ui/dialog.tsx
- MUST NOT duplicate EGOGiftSelectionList or EGOGiftObservationSelection
- Filter state MUST be local to EditPane
- MUST use MAX_OBSERVABLE_GIFTS constant

**Performance:**
- EditPane suspends on dialog open (improvement over page-load)
- Cost calculation O(1) find
- Filters local to EditPane (efficient re-renders)

**Backward compatibility:**
- Data format Set<string> unchanged
- No migration needed for existing drafts
