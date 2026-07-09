# Research: Comprehensive Gift Section → Summary + Pane Pattern

## Spec Ambiguities

**NONE FOUND** - Spec is well-defined.

Key decisions already specified:
- No theme pack restriction (ALL gifts shown)
- Silent cascade: Recipe ingredients auto-selected without notification
- Filter states local to pane (reset on reopen)
- Enhancement selection (0-3 levels) preserved
- Selection data format unchanged (encoded gift IDs)

---

## Spec-to-Code Mapping

| Requirement | Location | Action |
|-------------|----------|--------|
| Summary component | `ComprehensiveGiftSummary.tsx` | CREATE |
| Selector pane | `ComprehensiveGiftSelectorPane.tsx` | CREATE |
| Remove inline section | `EGOGiftComprehensiveListSection.tsx` | DELETE |
| Wire in planner | `PlannerMDNewPage.tsx` | MODIFY |
| Selection list | `EGOGiftSelectionList.tsx` | REUSE (no change) |
| Cascade logic | `lib/egoGiftEncoding.ts` | REUSE (no change) |

---

## Pattern Enforcement (MANDATORY)

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `ComprehensiveGiftSummary.tsx` | `FloorGiftViewer.tsx` | Empty state + gift card grid + tooltip |
| `ComprehensiveGiftSelectorPane.tsx` | `FloorGiftSelectorPane.tsx` | Dialog structure, filter layout |
| Both | `EGOGiftComprehensiveListSection.tsx` | Cascade selection logic (lines 68-101) |
| PlannerMDNewPage | `FloorThemeGiftSection.tsx` | Pane state orchestration |

---

## Existing Utilities (REUSE - DO NOT CREATE)

| Category | File | Functions |
|----------|------|-----------|
| Gift encoding | `lib/egoGiftEncoding.ts` | `encodeGiftSelection`, `decodeGiftSelection`, `getBaseGiftId`, `findEncodedGiftId`, `getCascadeIngredients` |
| Selection list | `EGOGiftSelectionList.tsx` | Full component with enhancement selection |
| Filters | `EGOGiftKeywordFilter.tsx`, `EGOGiftSearchBar.tsx` | Complete filter components |
| Sorter | `Sorter.tsx` | SortMode type + component |
| Cards | `EGOGiftCard.tsx` | `isSelected` + `enhancement` props |
| Dialog | `components/ui/dialog.tsx` | shadcn Dialog |
| i18n | `common.json` | `pages.plannerMD.selectGifts`, `pages.plannerMD.comprehensiveGiftList` |

---

## Gap Analysis

- **Missing:** Nothing - all utilities exist
- **Modify:** `PlannerMDNewPage.tsx` (import + wire-up)
- **Reuse directly:** All existing components and utilities

---

## Testing Requirements

### Manual UI Tests
- Navigate to planner → Comprehensive section visible with placeholder
- Click placeholder → Dialog opens with full gift list
- Verify filters: keyword, sorter, search all present
- Filter by keyword → Only matching gifts shown
- Click enhancement level → Gift selected
- Select recipe gift → Cascade adds ingredients at level 0
- Close dialog → Summary shows selected gifts
- Toggle same level → Deselects gift
- Reopen pane → Selections preserved

### Automated Tests
- Pane open/close state management
- Selection persistence across pane cycles
- Cascade adds ingredients at enhancement 0
- Toggle logic: same level deselects, different level updates
- Summary displays all selected with enhancement indicators

### Edge Cases
- Empty state: Placeholder visible, pane shows all
- Cascade with filters: Ingredients added even if hidden by filter
- Many selections: Cards wrap in grid
- Already-selected ingredient: Cascade doesn't overwrite enhancement

---

## Technical Constraints

| Constraint | Solution |
|------------|----------|
| Encoding format | Use existing `encodeGiftSelection()` / `decodeGiftSelection()` |
| Cascade preservation | `findEncodedGiftId()` check prevents overwrite |
| No theme pack filter | Omit `giftIdFilter` prop to `EGOGiftSelectionList` |
| Suspense boundary | Works within existing planner Suspense |
| State management | Parent manages selection, passes to summary/pane |

---

## Component Dependency Graph

```
PlannerMDNewPage (modified)
├── ComprehensiveGiftSummary (new)
│   ├── useEGOGiftListData
│   ├── EGOGiftCard
│   ├── decodeGiftSelection
│   └── Tooltip + EGOGiftTooltipContent
│
└── ComprehensiveGiftSelectorPane (new)
    ├── Dialog
    ├── EGOGiftSelectionList
    ├── EGOGiftKeywordFilter
    ├── EGOGiftSearchBar
    ├── Sorter
    ├── encodeGiftSelection
    ├── getCascadeIngredients
    └── useEGOGiftListData
```

---

## Summary

**Status:** READY TO IMPLEMENT
**Effort:** Low - Redistributes existing logic into focused components
**Risk:** Very Low - Uses existing utilities, no logic changes
**Components:** +2 new, -1 deleted, 0 modified utilities
