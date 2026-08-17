# Research: Comprehensive EGO Gift List

## Spec Ambiguities
None identified. Spec is clear and unambiguous.

---

## Spec-to-Code Mapping

**Core Components:**
- Enhancement level selector → New wrapper around `EgoGiftMiniCard` with hover overlay
- Comprehensive list display → Modify `EGOGiftSelectionList.tsx` to pass enhancement in callback
- Card sizing → Use existing `EgoGiftMiniCard` (w-24 h-24)

**Selection Logic:**
- No selection + click level → `onGiftSelect(enhancement + giftId)`
- Selected + click same level → toggle off/deselect
- Selected + click different level → update enhancement level
- Numeric string encoding in parent handler

**Integration Points:**
- Add comprehensive section to `PlannerMDNewPage.tsx` after observation section
- New state: `Set<string>` for comprehensive gift selections
- Use `useEGOGiftListData()` with no pool filtering (all gifts)

---

## Spec-to-Pattern Mapping

**From existing code:**
- `EGOGiftObservationSection` → Structural template (header, filters, grid layout)
- `EGOGiftSelectionList` → Reuse for filtering, sorting, search
- `EgoGiftMiniCard` → Already has enhancement prop, w-24 h-24 size
- `TierLevelSelector` → Hover overlay UI pattern (state on hover, absolute positioning)
- `EGOGiftObservationSelection` → Adapt for showing selected gifts with decoded enhancement

**Styling patterns:**
- Container: bg-muted border-border rounded-md p-6 (from observation section)
- Enhancement indicator: w-6 h-6 absolute top-0 right-0 (from EgoGiftMiniCard)

---

## Gap Analysis

**Must Create:**
- `EGOGiftComprehensiveListSection.tsx` - Main section component
- Enhancement level selector overlay for hover interaction
- Handler for encode/decode numeric string format

**Must Modify:**
- `EGOGiftSelectionList.tsx` - Update callback to support enhancement level
- `PlannerMDNewPage.tsx` - Add comprehensive section + state
- `EgoGiftMiniCard.tsx` - Add hover enhancement selector overlay

**Can Reuse Directly:**
- `useEGOGiftListData()` hook, `sortEGOGifts()` utility
- `EGOGiftKeywordFilter`, `Sorter`, search components
- Tailwind grid classes, styling patterns

---

## Technical Constraints

**Pattern Compliance:**
- MUST use `EGOGiftSelectionList` (don't duplicate filtering logic)
- MUST follow `EGOGiftObservationSection` structure
- MUST use Zod validation for new types
- MUST encode enhancement in numeric string format

**Component Constraints:**
- EgoGiftMiniCard: w-24 h-24 (fixed size)
- Enhancement levels: 0, 1, 2 only
- Overlay: hover-triggered (not click-based)

**Data Constraints:**
- NO pool filtering (all gifts selectable)
- Selection: Set<string> with numeric string encoding
- UNLIMITED selections (unlike observation's MAX=3)
- Suspense boundary required for async data
