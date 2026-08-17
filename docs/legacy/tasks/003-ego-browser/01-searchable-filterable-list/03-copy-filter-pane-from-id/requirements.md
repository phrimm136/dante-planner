# Task: Apply Identity Filter Pattern to EGO Filter with Type Safety

## Description

Apply the Identity page's filter sidebar pattern to the EGO page, fixing type inconsistencies and extracting duplicate logic. The filter sidebar UI already exists in both pages using the same layout components (FilterPageLayout, FilterSidebar, FilterSection), but the implementation has typing issues and code duplication that need to be addressed.

**Core Requirements:**

1. **Fix Type Safety:**
   - EGO type filter currently uses `Set<string>` but should use `Set<EGOType>` (literal union: 'ZAYIN' | 'TETH' | 'HE' | 'WAW' | 'ALEPH')
   - Season filter: Standardize both Identity and EGO to use stricter `Set<Season>` type (Identity currently uses looser `Set<number>`)
   - Add missing `EGOListItem` type alias to match Identity's `IdentityListItem` naming convention

2. **Update SeasonDropdown to Use Season Type:**
   - Change from `Set<number>` to `Set<Season>` for both props
   - Update IdentityPage to pass `Set<Season>` instead of `Set<number>`
   - EGO already uses `Set<Season>` (no change needed for EGO)
   - Preserve micro-suspense pattern (component uses `useFilterI18nData()` internally, parent wraps in Suspense)

3. **Extract Filter Count Calculation:**
   - Both pages manually sum 6-8 filter set sizes (duplicated logic)
   - Create `calculateActiveFilterCount(...filterSets)` utility in new `lib/filterUtils.ts`
   - Pure function that sums sizes of variable number of Set arguments

4. **Add Defensive Cleanup:**
   - Identity page has `useEffect` cleanup to reset filters on unmount
   - EGO page missing this pattern - add matching cleanup hook

**Micro-Suspense Pattern (Critical):**
- SeasonDropdown fetches i18n internally via suspending hook
- Parent components wrap dropdown in `<Suspense fallback={<Skeleton />}>`
- During language switch: dropdown suspends, filter sidebar stays mounted
- Type changes MUST NOT break this suspense mechanism

**UI Behavior (No Changes):**
- Filter sidebar layout already correct in both pages
- Primary filters (Sinner, Keyword) always visible on mobile
- Secondary filters (others) shown when sidebar expanded
- Reset button clears all filters including search
- Active filter count badge shows total selected filters

## Research

**Patterns to Study:**
- `frontend/src/routes/IdentityPage.tsx` (lines 86-123) - Filter state setup, cleanup pattern
- `frontend/src/components/common/SeasonDropdown.tsx` - Internal i18n fetching, suspense boundaries
- `frontend/src/components/ego/EGOList.tsx` - How filter props are consumed
- `frontend/src/types/IdentityTypes.ts` (lines 8-22) - `IdentityListItem` interface pattern

**Type Definitions:**
- `frontend/src/lib/constants.ts` (line 533) - `Season` type definition from SEASONS array
- `frontend/src/types/EGOTypes.ts` (line 7) - `EGOType` union definition
- `frontend/src/schemas/EGOSchemas.ts` (line 12) - `EgoTypeSchema` Zod enum

**Existing Test Patterns:**
- `frontend/src/lib/__tests__/startGiftCalculator.test.ts` - Pure function testing pattern
- `frontend/src/components/ego/EGOList.test.tsx` - Component with filter props testing

## Scope

**Files to Read for Context:**

Frontend Core:
- `frontend/src/routes/IdentityPage.tsx` - Reference implementation (needs season type fix)
- `frontend/src/routes/EGOPage.tsx` - Target page to fix
- `frontend/src/components/common/SeasonDropdown.tsx` - Component to update type
- `frontend/src/components/ego/EGOList.tsx` - Filter prop consumer
- `frontend/src/components/identity/IdentityList.tsx` - Filter prop consumer (needs season type fix)

Type Definitions:
- `frontend/src/types/IdentityTypes.ts` - Pattern for ListItem type (needs season type fix)
- `frontend/src/types/EGOTypes.ts` - EGO types, need to add alias
- `frontend/src/lib/constants.ts` - Season type definition

Tests:
- `frontend/src/components/ego/EGOList.test.tsx` - Needs type import update
- `frontend/src/lib/__tests__/*.test.ts` - Pattern for utility tests

## Target Code Area

**Files to Create:**
- `frontend/src/lib/filterUtils.ts` - New utility with `calculateActiveFilterCount()`
- `frontend/src/lib/__tests__/filterUtils.test.ts` - Unit tests for new utility

**Files to Modify:**

Type Definitions:
- `frontend/src/types/EGOTypes.ts` - Add `EGOListItem` type alias
- `frontend/src/types/IdentityTypes.ts` - Change `season: Season` from `season: number` in IdentityListItem

Components:
- `frontend/src/components/common/SeasonDropdown.tsx` - Change props from `Set<number>` to `Set<Season>`
- `frontend/src/components/ego/EGOList.tsx` - Update props interface typing for `selectedEGOTypes`
- `frontend/src/components/identity/IdentityList.tsx` - Update props interface for `selectedSeasons` from `Set<number>` to `Set<Season>`

Pages:
- `frontend/src/routes/EGOPage.tsx` - Fix EGO type filter typing, add cleanup, use utility
- `frontend/src/routes/IdentityPage.tsx` - Fix season filter typing from `Set<number>` to `Set<Season>`, use utility

Tests:
- `frontend/src/components/ego/EGOList.test.tsx` - Fix type imports

## System Context (Senior Thinking)

**Feature Domain:** Identity/EGO/Gift Browser (Frontend List Pages)

**Core Files from architecture-map:**
- `routes/IdentityPage.tsx`, `routes/EGOPage.tsx` - List pages with filters
- `components/filter/FilterPageLayout.tsx` - Responsive filter sidebar layout (shared)
- `components/filter/FilterSidebar.tsx` - Filter container (shared)
- `components/filter/FilterSection.tsx` - Collapsible filter groups (shared)
- `components/common/SeasonDropdown.tsx` - Self-contained i18n dropdown

**Cross-Cutting Concerns:**
- **i18n**: SeasonDropdown uses `useFilterI18nData()` internally (suspending hook)
- **Micro-Suspense**: Components fetch i18n with internal Suspense boundaries
- **Type Validation**: Zod schemas validate data at runtime, TypeScript at compile-time
- **Constants**: `lib/constants.ts` defines Season type from static data

**Data Flow Pattern:**
```
Static JSON (seasons.json)
    ↓
SEASONS constant (constants.ts)
    ↓
Season type (typeof SEASONS[number])
    ↓
useFilterI18nData() (suspends)
    ↓
SeasonDropdown (wrapped in Suspense)
    ↓
Page state: Set<Season>
    ↓
List component filtering
```

## Impact Analysis

**Files Being Modified:**

Medium-Impact (Shared Components):
- `SeasonDropdown.tsx` - **Medium impact** - Used by Identity and EGO pages
  - Risk: Type change affects both consumers
  - Mitigation: Both pages updated simultaneously
- `IdentityList.tsx` - **Low-Medium impact** - Used only by IdentityPage
  - Risk: Filter type mismatch if not updated with page
  - Mitigation: Update props interface alongside page changes

Low-Impact (Page-Isolated):
- `IdentityPage.tsx` - Low impact (page isolated, but widely used)
- `EGOPage.tsx` - Low impact (page isolated)
- `EGOList.tsx` - Low impact (consumed by single page)

Type Definitions (Low Risk):
- `IdentityTypes.ts` - Fixing incorrect type (season should be Season, not number)
- `EGOTypes.ts` - Adding missing type alias

New Files (Zero Impact):
- `filterUtils.ts` - New utility, no dependencies yet
- `filterUtils.test.ts` - Test file only

**Dependencies from architecture-map:**

SeasonDropdown consumers:
- `routes/IdentityPage.tsx` (lines 193-198) - Wraps in Suspense with Skeleton - **NEEDS TYPE UPDATE**
- `routes/EGOPage.tsx` (lines 141-146) - Wraps in Suspense with Skeleton - **ALREADY CORRECT**
- Both use micro-suspense pattern (self-contained i18n fetching)

IdentityList consumers:
- `routes/IdentityPage.tsx` only - Page-isolated component

EGOList consumers:
- `routes/EGOPage.tsx` only - Page-isolated component

**Potential Ripple Effects:**

1. **SeasonDropdown Type Change:**
   - Identity page passes `Set<number>` → needs change to `Set<Season>`
   - EGO page already passes `Set<Season>` → no change needed
   - Breaking change for IdentityPage but planned update

2. **IdentityListItem Season Type:**
   - Currently `season: number` (incorrect)
   - Should be `season: Season` (matches data structure)
   - Fixes type inconsistency between spec and interface

3. **EGOListItem Type Addition:**
   - Fixes broken import in `EGOList.tsx:1`
   - No other files import this type yet
   - Zero ripple effect

4. **Filter Utility Extraction:**
   - New file, imported by pages that already exist
   - No cascading changes
   - Could be adopted by EGOGiftPage later (optional)

**High-Impact Files to Watch:**
- `SeasonDropdown.tsx` - Shared by two pages, type change affects both
- `IdentityPage.tsx` - Widely used page, verify no regressions

## Risk Assessment

**Type Safety Risks:**

1. **Season Type Mismatch:**
   - IdentityPage currently passes `Set<number>`, SeasonDropdown expects `Set<Season>`
   - **Mitigation:** Update both simultaneously in single commit
   - **Impact:** High if not updated together - compile error prevents broken state

2. **Missing EGOListItem Type:**
   - Current EGOList imports non-existent type (TypeScript should error)
   - **Mitigation:** Add type alias before modifying consumer
   - **Impact:** Low - fixes existing bug

**Micro-Suspense Risks:**

3. **Breaking Suspense Mechanism:**
   - Type changes could theoretically affect how `useFilterI18nData()` is called
   - **Mitigation:** Internal implementation unchanged, only prop types modified
   - **Verification:** Manual test language switching
   - **Impact:** Medium if broken - filter sidebar would unmount during language change

4. **Skeleton Fallback Not Shown:**
   - If Suspense boundary removed accidentally during refactor
   - **Mitigation:** Explicit testing checklist includes language switch test
   - **Impact:** Low - visual glitch only, no data loss

**Edge Cases to Handle:**

5. **Filter Count Edge Cases:**
   - Empty filter sets (size = 0)
   - No filter arguments passed
   - **Mitigation:** Tests cover these cases explicitly
   - **Impact:** Low - UI shows "0" badge (correct)

**Performance Concerns:**

6. **Filter Re-render Performance:**
   - Each filter change causes page re-render
   - List components use `useMemo` for filtering (already optimized)
   - New utility function is pure, zero overhead
   - **Impact:** None - no performance change expected

**Backward Compatibility:**

7. **Season Type Breaking Change:**
   - IdentityPage state changes from `Set<number>` to `Set<Season>`
   - Season type is `number`, so runtime values identical
   - Only compile-time type checking changes
   - **Impact:** None on runtime behavior, compile-time errors guide fixes

8. **Cleanup useEffect in EGO:**
   - Adding cleanup doesn't change existing behavior
   - State is component-local, unmount destroys it anyway
   - **Impact:** None - defensive pattern only

**Security Considerations:**
- None - client-side filtering only, no user data transmission
- Type safety prevents invalid filter values (defense in depth)

## Testing Guidelines

### Manual UI Testing

**1. Type Safety Verification (Developer Testing):**
1. Open EGOPage.tsx in IDE
2. Try adding invalid EGO type: `selectedEGOTypes.add('INVALID_TYPE')`
3. Verify: TypeScript error shown in IDE
4. Try adding valid type: `selectedEGOTypes.add('ZAYIN')`
5. Verify: No TypeScript error, autocomplete suggests valid types
6. Open IdentityPage.tsx in IDE
7. Verify: `selectedSeasons` has type `Set<Season>`, autocomplete suggests valid season numbers

**2. Filter Functionality (Both Pages):**

**Identity Page:**
1. Navigate to `/identity` page
2. Click "Sinner" filter, select "Yi Sang"
3. Verify: Identity cards filter to show only Yi Sang
4. Click "Season" dropdown, select "Season 1"
5. Verify: Badge count shows "2" (Sinner + Season)
6. Click "Reset All" button
7. Verify: All filters clear, badge count shows "0"
8. Repeat for all filter types (Keyword, Attribute, Attack Type, Rarity, Unit Keyword)

**EGO Page:**
1. Navigate to `/ego` page
2. Click "Sinner" filter, select "Faust"
3. Verify: EGO cards filter to show only Faust
4. Click "Season" dropdown, select "Season 2"
5. Verify: Badge count shows "2" (Sinner + Season)
6. Click "EGO Type" filter, select "ZAYIN"
7. Verify: Badge count shows "3" (Sinner + Season + EGO Type)
8. Verify: Only ZAYIN EGOs for Faust in Season 2 are visible
9. Click "Reset All" button
10. Verify: All filters clear, badge count shows "0"

**3. Micro-Suspense Pattern (Critical Test):**

**Language Switch - Identity Page:**
1. Navigate to `/identity` page with filters visible
2. Open browser DevTools Console
3. Change language setting (EN → KR or KR → EN)
4. **Verify During Switch:**
   - Season dropdown shows `<Skeleton>` fallback
   - Filter sidebar structure stays visible (does NOT unmount)
   - Other filters (Sinner, Keyword) remain interactive
   - Filter selections preserved (don't reset)
5. **Verify After Load:**
   - Season dropdown re-renders with new language labels
   - No console errors or warnings
   - Filter state unchanged

**Language Switch - EGO Page:**
1. Navigate to `/ego` page with filters visible
2. Open browser DevTools Console
3. Change language setting (EN → KR or KR → EN)
4. **Verify During Switch:**
   - Season dropdown shows `<Skeleton>` fallback
   - Filter sidebar structure stays visible (does NOT unmount)
   - Other filters remain interactive
   - Filter selections preserved
5. **Verify After Load:**
   - Season dropdown re-renders with new language labels
   - No console errors or warnings

**4. Navigation State Cleanup:**
1. Navigate to `/ego` page
2. Select multiple filters (Sinner, Season, EGO Type)
3. Navigate away to `/identity` page
4. Navigate back to `/ego` page
5. Verify: Filter state is reset (empty), not leaked from previous visit

**5. Mobile Responsive:**
1. Resize browser to mobile width (<1024px)
2. Verify: Filter sidebar shows "Filters" button with badge count
3. Click "Filters" button
4. Verify: Sidebar expands, primary filters visible
5. Verify: Secondary filters visible below primary
6. Select filters, verify badge count updates
7. Close sidebar, verify badge persists

### Automated Functional Verification

**Type Safety:**
- [ ] `selectedEGOTypes` accepts only valid EGOType literals ('ZAYIN', 'TETH', 'HE', 'WAW', 'ALEPH')
- [ ] `selectedSeasons` in both Identity and EGO uses `Set<Season>` (stricter type)
- [ ] SeasonDropdown accepts `Set<Season>` props from both pages
- [ ] EGOListItem type alias exists and matches EGO interface structure
- [ ] IdentityListItem has `season: Season` field (not `number`)

**Filter Behavior:**
- [ ] Identity page: All 7 filters work (Sinner, Keyword, Attribute, Attack Type, Rarity, Season, Unit Keyword)
- [ ] EGO page: All 6 filters work (Sinner, Keyword, Attribute, Attack Type, EGO Type, Season)
- [ ] Active filter count badge shows correct total across all selected filters
- [ ] Reset All button clears all filters and search query
- [ ] Filter selections use AND logic (must match all selected criteria)
- [ ] Empty state shown when no items match filters

**Utility Function:**
- [ ] `calculateActiveFilterCount()` returns 0 for no arguments
- [ ] `calculateActiveFilterCount()` returns 0 for all empty sets
- [ ] `calculateActiveFilterCount()` sums multiple sets correctly
- [ ] `calculateActiveFilterCount()` handles mixed types (string, number, Season, EGOType)
- [ ] `calculateActiveFilterCount()` does not mutate input sets

**Cleanup Pattern:**
- [ ] EGO page cleanup useEffect exists and calls handleResetAll on unmount
- [ ] Identity page cleanup useEffect remains unchanged

### Edge Cases

**Type Safety Edge Cases:**
- [ ] Invalid EGO type causes TypeScript compile error (not runtime error)
- [ ] Invalid season number outside Season type causes compile error
- [ ] Filter count utility handles empty sets without errors
- [ ] Filter count utility handles no arguments (returns 0)

**Micro-Suspense Edge Cases:**
- [ ] Language switch while filters active: Dropdown suspends, sidebar doesn't unmount
- [ ] Language switch with no filters active: Normal suspense behavior
- [ ] Multiple rapid language switches: No race conditions or errors
- [ ] Suspense fallback renders correctly (Skeleton with proper styling)

**Navigation Edge Cases:**
- [ ] Navigate away with active filters: Cleanup resets state on unmount
- [ ] Navigate back: Filter state starts fresh (no leaked state)
- [ ] Direct URL navigation: Filter state initializes correctly
- [ ] Browser back/forward: Filter state handled correctly

**Mobile/Responsive Edge Cases:**
- [ ] Mobile filter sidebar expands/collapses correctly
- [ ] Badge count visible when sidebar collapsed
- [ ] Filter selections preserved during responsive breakpoint changes
- [ ] Touch interactions work on mobile (no desktop-only behaviors)

### Integration Points

**SeasonDropdown Integration:**
- [ ] Identity page wraps SeasonDropdown in Suspense with Skeleton fallback
- [ ] EGO page wraps SeasonDropdown in Suspense with Skeleton fallback
- [ ] Both pages pass `Set<Season>` type (type-safe)
- [ ] Dropdown state changes propagate to parent page state correctly

**IdentityList Integration:**
- [ ] IdentityList receives updated props interface with `Set<Season>`
- [ ] Filtering logic works with Season type (type-safe comparisons)
- [ ] No runtime errors from type mismatches

**EGOList Integration:**
- [ ] EGOList receives updated props interface with `Set<EGOType>`
- [ ] Filtering logic works with new types (type-safe comparisons)
- [ ] No runtime errors from type mismatches

**Filter Utility Integration:**
- [ ] Identity page uses utility instead of manual sum (7 filter sets)
- [ ] EGO page uses utility instead of manual sum (6 filter sets)
- [ ] Badge count updates correctly when filters change
- [ ] Utility imported from correct path (`@/lib/filterUtils`)

**Test Suite Integration:**
- [ ] EGOList.test.tsx passes with updated type imports
- [ ] filterUtils.test.ts covers all utility function cases (11 tests)
- [ ] Full test suite runs without errors: `yarn test`
- [ ] TypeScript compilation succeeds: `yarn tsc --noEmit`

## Implementation Notes

**Execution Order (Critical):**
1. Create `filterUtils.ts` and `filterUtils.test.ts` (TDD approach)
2. Add `EGOListItem` type alias to `EGOTypes.ts` (fixes broken import)
3. Update `IdentityListItem` season type from `number` to `Season`
4. Update `SeasonDropdown` props from `Set<number>` to `Set<Season>`
5. Update IdentityPage: season state type + use filter utility
6. Update IdentityList props interface: season type
7. Fix EGOPage: EGO type state + add cleanup + use utility
8. Fix EGOList props interface: EGO type
9. Update test imports

**Season Type Standardization:**
- Both Identity and EGO use `Set<Season>` (stricter type)
- No backwards compatibility needed - breaking change is intentional
- Season type defined as `(typeof SEASONS)[number]` from constants
- Runtime values identical (numbers), only compile-time types change

**Micro-Suspense Preservation:**
- SeasonDropdown internal implementation MUST NOT change
- Only prop types are being updated
- Parent Suspense boundaries must remain in place
- Test language switching explicitly to verify

**Type Safety Philosophy:**
- Stricter types prevent bugs at compile-time
- `Set<EGOType>` catches typos before runtime
- `Set<Season>` ensures only valid season numbers
- Trade-off: Breaking changes for significant safety gain
