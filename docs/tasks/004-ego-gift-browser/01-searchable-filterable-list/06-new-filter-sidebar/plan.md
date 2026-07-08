# Execution Plan: EGO Gift Filter Sidebar

## Execution Overview

Integrate responsive filter sidebar into EGOGiftPage following established FilterPageLayout pattern from IdentityPage. Proceeds in 4 phases:

1. **Data Layer**: Constants, types, schemas for difficulty fields
2. **Filter Components**: 4 new filter components following existing patterns
3. **Integration**: Wire FilterPageLayout into EGOGiftPage, update EGOGiftList
4. **Testing**: Verify all filters and interactions

**Key Pattern Sources:**
- Page structure: `IdentityPage.tsx`
- Toggle buttons: `RankFilter.tsx`
- Dropdown: `SeasonDropdown.tsx`
- Icon filter: `CompactEGOTypeFilter.tsx`

---

## Execution Order

### Phase 1: Data Layer

**1. Add constants to `lib/constants.ts`**
- Depends on: none
- Enables: F2, F3, F5
- Add: `EGO_GIFT_TIERS`, `EGO_GIFT_TIER_TAGS`, `EGO_GIFT_DIFFICULTIES`, `EGO_GIFT_ATTRIBUTE_TYPES`

**2. Update `types/EGOGiftTypes.ts`**
- Depends on: none
- Enables: F2
- Add: `hardOnly?: boolean`, `extremeOnly?: boolean` to EGOGiftSpec and EGOGiftListItem

**3. Update `schemas/EGOGiftSchemas.ts`**
- Depends on: Step 2
- Enables: F2
- Add: optional `hardOnly` and `extremeOnly` fields to Zod schemas

### Phase 2: Filter Components

**4. Create `CompactEGOGiftKeywordFilter.tsx`**
- Depends on: none
- Enables: F1
- Pattern: `CompactKeywordFilter.tsx` + `EGOGiftKeywordFilter.tsx`
- Compact version with "None" toggle, 7-column grid

**5. Create `DifficultyFilter.tsx`**
- Depends on: Step 1
- Enables: F2
- Pattern: `RankFilter.tsx`
- 3 toggle buttons (Normal/Hard/Extreme)

**6. Create `TierFilter.tsx`**
- Depends on: Step 1
- Enables: F3
- Pattern: `RankFilter.tsx`
- 6 toggle buttons (I, II, III, IV, V, EX)

**7. Create `ThemePackDropdown.tsx`**
- Depends on: none
- Enables: F4
- Pattern: `SeasonDropdown.tsx`
- Multi-select dropdown with checkbox items, i18n labels

**8. Create `CompactAttributeTypeFilter.tsx`**
- Depends on: Step 1
- Enables: F5
- Pattern: `CompactEGOTypeFilter.tsx`
- Icon filter for CRIMSON/AMBER/SCARLET (uses getAffinityIconPath)

### Phase 3: Integration

**9. Update `EGOGiftPage.tsx`**
- Depends on: Steps 4-8
- Enables: F1-F5, E3, E4
- Actions:
  - Wrap content in Suspense for hooks
  - Add FilterPageLayout with filterContent, primaryFilters, secondaryFilters
  - Add 6 filter state hooks
  - Add activeFilterCount calculation
  - Add handleResetAll function
  - Pass filter states to EGOGiftList

**10. Update `EGOGiftList.tsx`**
- Depends on: Steps 2-3, Step 9
- Enables: F1-F5, E1, E2
- Actions:
  - Add filter props for all 5 filters
  - Difficulty: derive from hardOnly/extremeOnly
  - Tier: extract from tag array
  - Theme pack: check themePack array membership
  - Attribute type: check attributeType field
  - Apply AND across filter types, OR within

### Phase 4: Testing

**11. Manual Desktop Testing**
- Depends on: Steps 9-10
- Test sidebar visibility, filter interactions, reset

**12. Manual Mobile Testing**
- Depends on: Steps 9-10
- Test expandable header, primary/secondary visibility

**13. Create/Update Tests**
- Depends on: Steps 9-10
- Add unit tests for filter logic

---

## Verification Checkpoints

**After Step 3 (Data Layer):**
- Run: `yarn tsc --noEmit`
- Verify: Types compile without errors

**After Step 8 (Components):**
- Run: `yarn tsc --noEmit`
- Verify: All component files exist and compile

**After Step 10 (Integration):**
- Verify F1: Keyword filter works
- Verify F2: Difficulty filter works
- Verify F3: Tier filter works
- Verify F4: Theme Pack filter works
- Verify F5: Attribute Type filter works
- Verify E1: Empty results message
- Verify E4: Reset All clears everything

**After Step 12 (Mobile):**
- Verify mobile expandable header
- Verify primary/secondary filter split

---

## Rollback Strategy

**Safe Stopping Points:**
- After Step 3: Data layer complete, no UI changes
- After Step 8: Components exist but not integrated

**If Step 9 fails:** Revert EGOGiftPage.tsx - page remains functional
**If Step 10 fails:** Revert EGOGiftList.tsx - sidebar shows but filters inactive

**Backup Before Changes:**
- `frontend/src/routes/EGOGiftPage.tsx`
- `frontend/src/components/egoGift/EGOGiftList.tsx`
