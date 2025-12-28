# Execution Plan: Comprehensive EGO Gift List

## Planning Gaps
None identified. Research is comprehensive.

---

## Execution Overview

The task implements a comprehensive EGO gift selection with hover-based enhancement level selection:
1. Create utility functions for encoding/decoding numeric string format
2. Create enhancement level selector component (TierLevelSelector hover pattern)
3. Create wrapper component adding enhancement selection to EgoGiftMiniCard
4. Create section component (EGOGiftObservationSection structure)
5. Integrate into PlannerMDNewPage with new state management
6. Verify all features via Playwright

---

## Execution Order

### Phase 1: Foundation

1. **`frontend/src/lib/constants.ts`**: Add `ENHANCEMENT_LEVELS = [0, 1, 2]` constant
   - Depends on: none
   - Enables: F2

2. **`frontend/src/lib/egoGiftEncoding.ts`** (NEW): Encoding utility functions
   - `encodeGiftSelection(enhancement, giftId)` and `decodeGiftSelection(encodedId)`
   - Depends on: none
   - Enables: F1, F3

### Phase 2: Components

3. **`frontend/src/components/egoGift/EGOGiftEnhancementSelector.tsx`** (NEW): Hover overlay
   - TierLevelSelector pattern: absolute positioning, hover-triggered
   - Props: `giftId`, `currentEnhancement`, `onSelect(enhancement, giftId)`
   - Depends on: step 1
   - Enables: F2, F3

4. **`frontend/src/components/egoGift/EgoGiftSelectableCard.tsx`** (NEW): Card wrapper
   - Combines EgoGiftMiniCard + EnhancementSelector
   - Depends on: step 3
   - Enables: F1, F2, F3

5. **`frontend/src/components/egoGift/EGOGiftSelectionList.tsx`**: Add enhancement support
   - New optional props: `onGiftSelectWithEnhancement`, `enableEnhancementSelection`, `selectedGiftEnhancements`
   - Backward compatible (existing usage unchanged)
   - Depends on: step 4
   - Enables: F1, F4

6. **`frontend/src/components/egoGift/EGOGiftComprehensiveSelection.tsx`** (NEW): Selected gifts display
   - Parses numeric string format, shows enhancement level
   - Click to remove
   - Depends on: step 2, step 4
   - Enables: F1

### Phase 3: Section Integration

7. **`frontend/src/components/egoGift/EGOGiftComprehensiveListSection.tsx`** (NEW): Main section
   - EGOGiftObservationSection structure (header, filters, grid)
   - NO pool filtering, NO max selection limit
   - Implements toggle logic with encoding utilities
   - Depends on: step 5, step 6
   - Enables: F1, F2, F3, F4

### Phase 4: Page Integration

8. **`frontend/src/routes/PlannerMDNewPage.tsx`**: Add section to page
   - New state: `comprehensiveGiftIds: Set<string>`
   - Suspense boundary around section
   - Depends on: step 7
   - Enables: I1

### Phase 5: i18n

9-12. **i18n files** (EN/KR/JP/CN): Add translation keys
   - `pages.plannerMD.comprehensiveGiftList`
   - Depends on: none (parallel with phase 2-4)
   - Enables: I1

### Phase 6: Verification

13. **Manual Testing via Playwright**: Verify all 10 features
    - Depends on: steps 1-12
    - Enables: All features verified

---

## Verification Checkpoints

- **After step 2**: Test encoding functions
  - `encodeGiftSelection(1, '9001')` → `'19001'`
  - `decodeGiftSelection('19001')` → `{ enhancement: 1, giftId: '9001' }`

- **After step 5**: Verify backward compatibility
  - EGOGiftObservationSection still works unchanged

- **After step 8**: Playwright full feature test
  - F1-F4 core features
  - E1-E3 edge cases
  - I1-I3 integration

---

## Rollback Strategy

- **Step 3 fails**: Fallback to click-based selection without hover
- **Step 5 fails**: Create separate list component (violates DRY but works)
- **Step 7 fails**: Integrate directly into PlannerMDNewPage

**Safe stopping points:**
- After step 2: Encoding utilities complete
- After step 5: Core components complete
- After step 8: All code complete, only i18n remaining

**Critical dependency**: Step 5 modifies shared EGOGiftSelectionList. If observation breaks, roll back and create separate component.
