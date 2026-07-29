# Execution Plan: Apply Identity Filter Pattern to EGO Filter

## Planning Gaps

**RESOLVED - Research findings corrected one spec error:**
- Spec claims IdentityListItem.season needs change from `number` to `Season`
- Reality: IdentityListItem.season is ALREADY `Season` type (verified at IdentityTypes.ts:21)
- Only IdentityPage state needs fixing (`Set<number>` → `Set<Season>`)

**No blocking gaps. Proceeding with plan.**

## Execution Overview

This task applies type safety improvements to filter systems across Identity and EGO list pages. The core changes involve:

1. **Strengthen type safety** - Replace loose types with strict literal unions (`Set<EGOType>`, `Set<Season>`)
2. **Extract duplication** - Create shared filter count utility used by both pages
3. **Add missing patterns** - EGO page needs cleanup hook that Identity already has
4. **Fix broken imports** - EGOListItem type alias missing, causing import errors

**Key Constraint:** Micro-suspense pattern in SeasonDropdown must remain intact. Component fetches i18n internally, parent wraps in Suspense. Type changes affect only prop signatures, not data fetching logic.

**Execution Strategy:** TDD approach - create utility + tests first, then apply type changes atomically (all consumers updated together to prevent intermediate broken states).

## Dependency Analysis

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `lib/filterUtils.ts` | **New** | None | IdentityPage, EGOPage (after creation) |
| `lib/__tests__/filterUtils.test.ts` | **New/Test** | filterUtils.ts | Test suite only |
| `types/EGOTypes.ts` | Low | None | EGOList, EGOList.test.tsx (fixes broken import) |
| `components/common/SeasonDropdown.tsx` | **Medium** | constants.ts (Season type) | IdentityPage, EGOPage (both consumers updated) |
| `routes/IdentityPage.tsx` | Low | SeasonDropdown, IdentityList, filterUtils | None (page-isolated) |
| `routes/EGOPage.tsx` | Low | SeasonDropdown, EGOList, filterUtils | None (page-isolated) |
| `components/identity/IdentityList.tsx` | Low-Medium | Season type from constants | IdentityPage only |
| `components/ego/EGOList.tsx` | Low-Medium | EGOType, Season types | EGOPage only |
| `components/ego/EGOList.test.tsx` | Test | EGOTypes (EGOListItem) | Test suite only |

### Ripple Effect Map

**SeasonDropdown Type Change (`Set<number>` → `Set<Season>`):**
- IdentityPage.tsx state declaration (line 92) → MUST change to `Set<Season>`
- EGOPage.tsx state declaration (line 50) → Already correct, no change
- IdentityList.tsx props interface → MUST change to `Set<Season>`
- EGOList.tsx props interface → Already correct, no change

**EGOListItem Type Addition:**
- EGOList.tsx line 2 broken import → Fixed by adding alias
- No other consumers (type is local to EGO domain)

**Filter Utility Extraction:**
- IdentityPage.tsx lines 97-104 → Replace with utility call
- EGOPage.tsx lines 54-60 → Replace with utility call
- No cascading changes (utility is pure function)

### High-Risk Modifications

**SeasonDropdown (Medium Risk):**
- **Why risky:** Shared by two pages, type change breaks API contract
- **Mitigation:** Update both consumers atomically in same step
- **Verification:** TypeScript compilation will fail if any consumer mismatched
- **Rollback:** Revert single commit containing all Season type changes

**Micro-Suspense Pattern (Medium Risk):**
- **Why risky:** Type changes could theoretically affect internal suspense mechanism
- **Mitigation:** Internal implementation (useFilterI18nData call) unchanged, only prop types modified
- **Verification:** Manual language switch test on both pages (explicit test case)
- **Rollback:** If suspense broken, revert to `Set<number>` temporarily while debugging

## Execution Order

### Phase 1: Foundation (Tests First)

1. **Create lib/filterUtils.ts**
   - Depends on: None
   - Enables: F1 (filter count utility)
   - Pure function: `calculateActiveFilterCount(...filterSets: ReadonlySet<unknown>[]) => number`
   - Uses reduce to sum all Set sizes, returns 0 for empty input

2. **Create lib/__tests__/filterUtils.test.ts**
   - Depends on: Step 1
   - Enables: F1 verification
   - 11 test cases covering edge cases, type safety, immutability

3. **Run unit tests**
   - Depends on: Step 2
   - Verify: `yarn test filterUtils.test.ts` passes all 11 tests
   - Checkpoint: Foundation verified before proceeding

### Phase 2: Type Definitions

4. **Add EGOListItem type alias to types/EGOTypes.ts**
   - Depends on: None
   - Enables: F2 (fix broken import)
   - Add: `export type EGOListItem = EGO` after line 23
   - Pattern: Matches IdentityListItem naming convention

### Phase 3: Shared Component Update

5. **Update SeasonDropdown props interface**
   - Depends on: None (Season type already exists in constants.ts)
   - Enables: F3 (type-safe season filtering)
   - Change: `selectedSeasons: Set<number>` → `Set<Season>` (line 14)
   - Change: `onSelectionChange: (seasons: Set<number>)` → `(seasons: Set<Season>)` (line 15)
   - Critical: Internal implementation unchanged (useFilterI18nData, suspense)

### Phase 4: Identity Page Updates (Atomic)

6. **Update IdentityPage.tsx - Season state + utility**
   - Depends on: Steps 1, 5
   - Enables: F3, F1 (Identity)
   - Changes:
     - Line 92: `useState<Set<number>>` → `useState<Set<Season>>`
     - Lines 97-104: Replace manual sum with `calculateActiveFilterCount(...)`
     - Import: Add `import { calculateActiveFilterCount } from '@/lib/filterUtils'`
   - Verification: TypeScript compilation succeeds

7. **Update IdentityList.tsx props interface**
   - Depends on: Step 6
   - Enables: F3 (type consistency)
   - Change: `selectedSeasons: Set<number>` → `Set<Season>` in props interface
   - Import: Season type already imported from constants (line 1)

### Phase 5: EGO Page Updates (Atomic)

8. **Update EGOPage.tsx - EGO type + cleanup + utility**
   - Depends on: Steps 1, 4
   - Enables: F4, F5, F1 (EGO)
   - Changes:
     - Line 49: `useState<Set<string>>` → `useState<Set<EGOType>>`
     - Import: Add `import type { EGOType } from '@/types/EGOTypes'`
     - Lines 54-60: Replace manual sum with `calculateActiveFilterCount(...)`
     - Import: Add `import { calculateActiveFilterCount } from '@/lib/filterUtils'`
     - After handleResetAll (line 71): Add cleanup useEffect
   - Verification: TypeScript compilation succeeds

9. **Update EGOList.tsx props interface + import**
   - Depends on: Steps 4, 8
   - Enables: F4, F2 (type consistency)
   - Changes:
     - Line 2: Import should work now (EGOListItem alias exists)
     - Line 18: `selectedEGOTypes: Set<string>` → `Set<EGOType>`
     - Import: Add `import type { EGOType } from '@/types/EGOTypes'`

### Phase 6: Test Updates

10. **Update EGOList.test.tsx imports**
    - Depends on: Step 4
    - Enables: Test suite passing
    - Verify: EGOListItem import resolves correctly
    - Run: `yarn test EGOList.test.tsx` to verify 13 existing tests still pass

11. **Run full TypeScript compilation**
    - Depends on: Steps 1-10
    - Command: `yarn tsc --noEmit`
    - Verify: No type errors across entire codebase

12. **Run full test suite**
    - Depends on: Steps 1-11
    - Command: `yarn test`
    - Verify: All tests pass (existing + new filterUtils tests)

### Phase 7: Manual Verification

13. **Test micro-suspense on both pages**
    - Depends on: Steps 1-12
    - Verify: Language switch → SeasonDropdown suspends → Sidebar stays mounted
    - See: Verification Checkpoints below for detailed steps

14. **Test filter functionality**
    - Depends on: Steps 1-12
    - Verify: All filters work, badge counts correct, Reset All works
    - See: Verification Checkpoints below

15. **Test navigation cleanup (EGO only)**
    - Depends on: Step 8
    - Verify: Filters reset on navigate away + back
    - See: Verification Checkpoints below

## Verification Checkpoints

### After Step 3 (Foundation Complete)
- **Verify F1:** `yarn test filterUtils.test.ts` passes all 11 tests
- **Test:** Import utility in Node REPL, call with sample Sets, verify output

### After Step 7 (Identity Complete)
- **Verify F3:** Navigate to `/identity`, select Season 1, verify filter works
- **Verify F1:** Badge count shows correct total (7 filter types)
- **Test:** Language switch EN→KR, verify SeasonDropdown suspends, sidebar stays mounted

### After Step 9 (EGO Complete)
- **Verify F4:** Navigate to `/ego`, select ZAYIN, verify filter works (type-safe)
- **Verify F5:** Navigate away, back to `/ego`, verify filters reset
- **Verify F1:** Badge count shows correct total (6 filter types)
- **Test:** Language switch EN→KR, verify SeasonDropdown suspends, sidebar stays mounted

### After Step 12 (All Tests Pass)
- **Verify:** No TypeScript errors in IDE
- **Verify:** `yarn tsc --noEmit` succeeds
- **Verify:** `yarn test` passes all tests

### After Step 15 (Manual Tests Complete)
- **Verify:** Mobile responsive (filter sidebar collapse/expand)
- **Verify:** Edge cases (empty filters, rapid language switches)
- **Verify:** All filter combinations work (AND logic)

## Risk Mitigation

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| **SeasonDropdown type mismatch** | Steps 5-7 | Update all consumers atomically in Phase 4. TypeScript compilation will fail if mismatch, preventing broken state. |
| **Micro-suspense broken** | Steps 5, 13 | Internal implementation unchanged (only prop types). Explicit manual test verifies language switch still works. |
| **EGOListItem import failure** | Step 9 | Type alias added first (Step 4) before consumer updated. Import will resolve correctly. |
| **Filter count calculation wrong** | Steps 6, 8 | 11 unit tests verify utility correctness before use. Edge cases (empty sets, no args) covered. |
| **Cleanup hook infinite loop** | Step 8 | Pattern copied from IdentityPage (working code). Empty dependency array prevents re-runs. |
| **Type narrowing breaks** | Steps 8, 9 | TypeScript strict mode enforces literal types. Compiler errors guide fixes if violations occur. |

## Dependency Verification Steps

### After Step 5 (SeasonDropdown Modified)
- **Test IdentityPage:** Navigate to `/identity`, verify SeasonDropdown renders, no console errors
- **Test EGOPage:** Navigate to `/ego`, verify SeasonDropdown renders, no console errors
- **Verify:** Both pages compile without TypeScript errors

### After Step 7 (IdentityList Modified)
- **Test IdentityPage:** Filter by Season 1, verify list updates correctly
- **Verify:** No prop type errors in browser console

### After Step 9 (EGOList Modified)
- **Test EGOPage:** Filter by ZAYIN, verify list updates correctly
- **Verify:** No prop type errors in browser console

## Rollback Strategy

### Safe Stopping Points
- **After Phase 1:** Foundation complete, no production code modified yet
- **After Phase 2:** Type definitions added, no runtime behavior changed
- **After Phase 4:** Identity page complete, EGO page unchanged (can deploy if needed)
- **After Phase 5:** All changes complete, tests verify correctness

### Rollback Steps
**If Step 5-7 fails (Identity broken):**
1. Revert SeasonDropdown.tsx to `Set<number>` props
2. Revert IdentityPage.tsx state to `Set<number>`
3. Revert IdentityList.tsx props to `Set<number>`
4. Keep filterUtils.ts (doesn't cause breakage)

**If Step 8-9 fails (EGO broken):**
1. Revert EGOPage.tsx to `Set<string>` for selectedEGOTypes
2. Revert EGOList.tsx props to `Set<string>`
3. Remove cleanup useEffect if causing issues
4. Keep filterUtils.ts and Identity changes (independent)

**If Step 13 fails (Micro-suspense broken):**
1. Verify internal implementation unchanged (only prop types modified)
2. Check parent Suspense boundaries still present in both pages
3. If critical bug: Revert Step 5 (SeasonDropdown props) and dependent steps
4. File bug report with reproduction steps

### Critical Path
**Must Complete Together:**
- Steps 5, 6, 7 (Season type change - IdentityPage + SeasonDropdown + IdentityList)
- Steps 8, 9 (EGO type change - EGOPage + EGOList)

**Can Deploy Independently:**
- Phase 1-2 (utility + types, no production impact)
- Phase 4 (Identity only, EGO unchanged)
- Phase 5 (EGO only, Identity already stable)
