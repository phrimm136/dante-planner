# Execution Plan: FE/BE Username API Alignment

## Planning Gaps
**NONE** - All information needed for execution is available.

## Execution Overview

This is a **type alignment task** to fix FE/BE API contract mismatch. The backend sends username as two separate fields (`authorUsernameKeyword`, `authorUsernameSuffix`), but frontend expects single `authorName` field. This causes validation failures preventing the community planner list from loading.

**Strategy:**
1. Update canonical type definition (PlannerListTypes.ts)
2. Remove duplicate type and add re-export (MDPlannerListTypes.ts)
3. Create formatter utility to compose and translate username
4. Update Zod schema to validate new structure
5. Modify component to use formatter
6. Update test mocks

**No breaking changes** - dev-only code, not yet in production.

## Dependency Analysis

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `types/PlannerListTypes.ts` | Low | constants.ts (MDCategory, RRCategory) | PlannerCard.tsx, PlannerCardContextMenu.tsx, schemas, tests |
| `types/MDPlannerListTypes.ts` | Low | PlannerListTypes.ts (after re-export) | useMDGesellschaftData.ts, filters hook, page component |
| `schemas/PlannerListSchemas.ts` | Medium | PlannerListTypes.ts, PlannerSchemas.ts | useMDGesellschaftData.ts (validation) |
| `lib/formatDate.ts` | Low | i18next | PlannerCard.tsx (existing formatPlannerDate consumer) |
| `components/plannerList/PlannerCard.tsx` | Low | formatDate.ts, PlannerListTypes | PlannerMDGesellschaftPage.tsx |
| `hooks/useMDGesellschaftData.test.tsx` | Low | schemas | Test runner only |

### Ripple Effect Map

**Type changes trigger compile-time errors:**
- If `PlannerListTypes.PublicPlanner` changes → PlannerCard.tsx will show TypeScript error (destructuring `authorName`)
- If schema validation fails → `useMDGesellschaftData` throws validation error, list page shows error boundary
- If formatter has wrong signature → PlannerCard will fail TypeScript compilation

**No runtime ripple effects** - this is primarily a compile-time change:
- Formatter is new code (no existing consumers)
- Type consolidation doesn't affect runtime (TypeScript compile-time only)
- Schema change is strict (fails fast on bad data)

### High-Risk Modifications

**Medium Risk: `schemas/PlannerListSchemas.ts`**
- Why: Runtime validation failure prevents list from loading (shows error boundary)
- Mitigation: Schema change is simple (two string fields), validated by TypeScript + tests
- Fallback: If validation fails, error boundary catches it, no app crash

**Low Risk: All other files**
- Type changes: Compile-time only (dev catches errors before runtime)
- Formatter: Pure function with defensive checks (handles edge cases)
- Component: Isolated (only used in gesellschaft page)

## Execution Order

### Phase 1: Type Alignment (Foundation)
**Goal:** Update type definitions to match backend API contract

1. **Update `types/PlannerListTypes.ts` PublicPlanner interface**
   - Depends on: None
   - Enables: F1 (type safety), F2 (schema validation foundation)
   - Action: Replace `authorName: string` with `authorUsernameKeyword: string` and `authorUsernameSuffix: string`
   - Expected errors: PlannerCard.tsx, schemas will show TypeScript errors (resolved in later steps)

2. **Remove duplicate and re-export in `types/MDPlannerListTypes.ts`**
   - Depends on: Step 1
   - Enables: F1 (type consolidation)
   - Action: Delete PublicPlanner interface (lines 81-108), add `export type { PublicPlanner } from './PlannerListTypes'`
   - Verifies: Import compatibility maintained (consumers don't need import changes)

### Phase 2: Formatter Creation (Logic Layer)
**Goal:** Create utility to compose and translate username

3. **Add i18next import to `lib/formatDate.ts`**
   - Depends on: None
   - Enables: F3 (username formatting)
   - Action: Add `import i18next from 'i18next'` at top of file

4. **Add `formatAuthorName()` function to `lib/formatDate.ts`**
   - Depends on: Step 3
   - Enables: F3 (username composition), E1 (edge case handling)
   - Action: Add formatter function with defensive checks, placeholders for missing data, i18n translation
   - Includes: JSDoc comments, defensive logic for empty fields, fallback for missing translations

### Phase 3: Validation & Component (Integration Layer)
**Goal:** Wire up types, schema, and UI to use new contract

5. **Update `schemas/PlannerListSchemas.ts` PublicPlannerSchema**
   - Depends on: Step 1
   - Enables: F2 (runtime validation)
   - Action: Replace `authorName: z.string()` with `authorUsernameKeyword: z.string()` and `authorUsernameSuffix: z.string()`

6. **Update `components/plannerList/PlannerCard.tsx`**
   - Depends on: Step 4, Step 5
   - Enables: F3 (UI display), F4 (language switching)
   - Action:
     - Import formatAuthorName
     - Update destructuring to use two fields
     - Call formatter to compose authorName
     - Use composed value in JSX

7. **Update `hooks/useMDGesellschaftData.test.tsx` mock data**
   - Depends on: Step 5
   - Enables: UT1, UT2 (unit tests passing)
   - Action: Replace `authorName: 'TestUser'` with `authorUsernameKeyword: 'W_CORP', authorUsernameSuffix: 'AB123'`

### Phase 4: Tests
**Goal:** Verify all changes work correctly

8. **Run TypeScript compiler**
   - Depends on: Steps 1-7
   - Enables: F1 (compile-time verification)
   - Verification: `yarn tsc --noEmit` passes with no errors

9. **Run unit tests**
   - Depends on: Step 7, Step 8
   - Enables: UT1, UT2 (automated verification)
   - Verification: `yarn test` passes

### Phase 5: Manual Verification
**Goal:** Verify UI behavior across languages

10. **Manual UI testing**
    - Depends on: Steps 8, 9
    - Enables: MV1, MV2, MV3 (user-facing verification)
    - Verification: See "Verification Checkpoints" below

## Test Steps (MANDATORY)

**Unit Tests (Embedded in Execution Order):**
- Step 8: TypeScript compilation validates type safety
- Step 9: Existing unit tests validate schema structure

**Manual Tests (Step 10):**
- Navigate to `/planner/md/gesellschaft`
- Verify author names display as `"Faust-{keyword}-{suffix}"`
- Change language EN → KR → JP
- Verify translations update
- Check console for errors

## Verification Checkpoints

**After Step 2 (Type Consolidation):**
- Verify: `grep -r "interface PublicPlanner" frontend/src/types` shows only PlannerListTypes.ts
- Verify: MDPlannerListTypes.ts contains `export type { PublicPlanner } from './PlannerListTypes'`

**After Step 6 (Component Update):**
- Verify: PlannerCard.tsx imports formatAuthorName
- Verify: No destructuring of `authorName` field (replaced with two fields)

**After Step 8 (TypeScript):**
- Verify: F1 - `yarn tsc --noEmit` exits with code 0
- Verify: No TypeScript errors related to PublicPlanner type

**After Step 9 (Unit Tests):**
- Verify: UT1 - Test mocks match new schema structure
- Verify: UT2 - Schema validation passes for mock data

**After Step 10 (Manual Verification):**
- Verify: F3 - Author names display correctly in all languages
- Verify: F4 - Language switching updates usernames without page reload
- Verify: E1 - Empty field cases handled gracefully (check browser console)
- Verify: MV1 - Published planner list loads without errors
- Verify: MV2 - "Best" mode also shows author names correctly
- Verify: MV3 - No validation errors in console

## Risk Mitigation

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| Schema validation fails at runtime | Step 5 | Schema change is simple (two strings), tested in Step 9 before manual testing |
| i18n translation missing | Step 4 | Formatter uses `defaultValue: keyword` parameter, falls back to raw keyword gracefully |
| Type consolidation breaks imports | Step 2 | Re-export maintains import compatibility, verified by TypeScript in Step 8 |
| Formatter called before i18n loads | Step 6 | App wraps routes in Suspense, i18n loads during init (architecture verified) |
| Long keywords break layout | Step 6 | Existing CSS truncation handles this (no formatter concern) |

## Pre-Implementation Validation Gate

**N/A** - This is not a pattern copy task. This is a type alignment and utility creation task.

Changes are:
- Type field replacement (simple substitution)
- Schema field update (matching type change)
- New formatter function (no pattern reference needed)
- Component usage update (destructuring change + formatter call)

All changes follow existing patterns implicitly:
- Formatter follows existing formatDate.ts structure
- Type definitions follow TypeScript interface patterns
- Schema follows Zod object pattern
- Component follows React hook/formatter usage pattern

## Dependency Verification Steps

**After Step 2 (Type Re-export):**
- Verify: Files importing from MDPlannerListTypes still work (TypeScript compilation in Step 8)
- Check: `useMDGesellschaftData.ts`, `useMDGesellschaftFilters.ts`, `PlannerMDGesellschaftPage.tsx`

**After Step 5 (Schema Update):**
- Verify: `useMDGesellschaftData.ts` validation doesn't throw errors (manual test in Step 10)
- Check: API response passes `PaginatedPlannersSchema.safeParse()`

**After Step 6 (Component Update):**
- Verify: PlannerCard renders without errors (manual test in Step 10)
- Verify: PlannerCardContextMenu still works (uses same PublicPlanner type)

## Rollback Strategy

**If Step 5 fails (schema validation errors):**
- Safe stopping point: After Step 4 (formatter exists but not used)
- Rollback: Revert Steps 1-2 (type changes), keep formatter for future use
- Test: `yarn tsc --noEmit` should pass with types reverted

**If Step 10 fails (UI broken):**
- Safe stopping point: After Step 9 (tests pass)
- Debug: Check browser console for specific error
- Rollback: Revert Step 6 only (component changes), investigate formatter logic

**If Step 8 fails (TypeScript errors):**
- Critical blocker: Cannot proceed to tests/deployment
- Rollback: Revert all changes, re-read instructions for missed requirements
- Re-plan: Confirm understanding of type structure with user
