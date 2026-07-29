# Research: Start Gift Summary + EditPane

## Spec Ambiguities

**None** - Spec is well-defined with clear reference implementations.

Minor notes (non-blocking):
- EA counter format "2/3" matches existing `giftSelection` i18n pattern
- Placeholder uses natural content flow (no fixed dimensions)
- Need to add `selectStartGift` i18n key

---

## Spec-to-Code Mapping

| Requirement | Current Code | Action |
|------------|--------------|--------|
| Summary (Selected) | `StartBuffSection.tsx:48-56` | Create `StartGiftSummary.tsx` |
| Summary (Empty) | `ThemePackViewer.tsx:74-97` | Dashed border placeholder |
| Edit Pane Dialog | `StartBuffEditPane.tsx:26-75` | Create `StartGiftEditPane.tsx` |
| 10 Keyword Rows | `StartGiftSection.tsx:110-137` | Move to EditPane |
| Selection Logic | `StartGiftSection.tsx:88-108` | Move to EditPane |
| EA Calculation | `StartGiftSection.tsx:24-40` | Move to EditPane |
| Page Integration | `PlannerMDNewPage.tsx:740-747` | Replace with Summary + Pane |

---

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `StartGiftSummary.tsx` | `StartBuffSection.tsx` | onClick, PlannerSection, summary display |
| `StartGiftSummary.tsx` | `ThemePackViewer.tsx` | Dashed border placeholder styling |
| `StartGiftEditPane.tsx` | `StartBuffEditPane.tsx` | Dialog structure, sizing, Done button |
| `StartGiftEditPane.tsx` | `StartGiftSection.tsx` | Selection logic, EA calculation, useEffect |

---

## Existing Utilities

| Category | Utility | Usage |
|----------|---------|-------|
| Wrapper | `PlannerSection` | Section container |
| Dialog | shadcn/ui `Dialog*` | EditPane container |
| Hooks | `useStartGiftPools()` | 10 keyword pools |
| Hooks | `useEGOGiftListData()` | Gift spec + i18n |
| Hooks | `useStartBuffData()` | EA calculation |
| Components | `StartGiftRow.tsx` | Reuse unchanged |
| Components | `EGOGiftCard.tsx` | Gift display (96x96) |
| Assets | `getStatusEffectIconPath()` | Keyword icons |

---

## Gap Analysis

**Create:**
- `StartGiftSummary.tsx` - Summary view
- `StartGiftEditPane.tsx` - Dialog with selection UI
- i18n: `selectStartGift` key

**Modify:**
- `PlannerMDNewPage.tsx` - Add pane state, swap components
- `common.json` - Add i18n key

**Reuse Unchanged:**
- `StartGiftRow.tsx`
- `EGOGiftCard.tsx`
- All hooks (`useStartGiftPools`, `useEGOGiftListData`, `useStartBuffData`)

**Delete After Verification:**
- `StartGiftSection.tsx`

---

## Testing Requirements

### Manual UI Tests
- Empty state: dashed placeholder, "Click to select" text, EA "0/1"
- Click placeholder: EditPane opens with 10 rows
- Select keyword: row highlights, gifts become selectable
- Select gift: counter updates, limit enforced
- Done button: closes dialog, Summary shows selection
- Reopen: selection persists
- Buff interaction: EA limit updates dynamically
- Page refresh: selection persists via autosave

### Automated Tests
- `calculateMaxGiftSelection()` pure function
- Selection trimming when EA decreases
- Summary render with/without selection
- EditPane row rendering

---

## Technical Constraints

| Constraint | Requirement |
|-----------|-------------|
| Pattern | Follow Summary + EditPane split exactly |
| Dependencies | No new packages, use existing shadcn/ui |
| State | Keep state lifted in PlannerMDNewPage |
| Autosave | Use existing `usePlannerAutosave` |
| i18n | Add to `common.json` only |
