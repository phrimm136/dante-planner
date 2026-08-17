# Research: Apply Identity Filter Pattern to EGO Filter

## Spec Ambiguities

**None found.** Spec is comprehensive with explicit requirements.

## Spec-to-Code Mapping

**Type Safety Changes:**
- EGO type filter: `Set<string>` → `Set<EGOType>` in EGOPage.tsx:49
- Season filter (Identity): `Set<number>` → `Set<Season>` in IdentityPage.tsx:92
- Season filter (EGO): Already `Set<Season>` - no change needed
- SeasonDropdown props: `Set<number>` → `Set<Season>` in SeasonDropdown.tsx:14

**Type Definitions:**
- Add `EGOListItem` type alias in EGOTypes.ts (fixes broken import in EGOList.tsx)
- IdentityListItem.season: Already `Season` type (spec incorrect, verified at IdentityTypes.ts:21)

**Utility Extraction:**
- Create `lib/filterUtils.ts` with `calculateActiveFilterCount()` function
- Replace manual sum in IdentityPage.tsx:97-104 (7 filters)
- Replace manual sum in EGOPage.tsx:54-60 (6 filters)

**Defensive Patterns:**
- Add cleanup useEffect to EGOPage.tsx (IdentityPage already has it at lines 119-123)

## Spec-to-Pattern Mapping

**Type-Safe Set Filters:**
- Pattern: Use literal union types directly in `Set<T>` declarations
- Source: EGOPage.tsx:50 already uses `Set<Season>` correctly
- Apply: Same pattern for `Set<EGOType>` in selectedEGOTypes state

**Micro-Suspense Boundaries:**
- Pattern: Component fetches i18n internally, parent wraps in Suspense
- Source: SeasonDropdown.tsx:64-65 uses `useFilterI18nData()` internally
- Preservation: Only prop types change, internal implementation unchanged
- Critical: Parent Suspense boundaries must remain (IdentityPage:193-198, EGOPage:141-146)

**Filter State Cleanup:**
- Pattern: useEffect with empty deps returning cleanup function
- Source: IdentityPage.tsx:119-123
- Apply: Exact replication in EGOPage after handleResetAll definition

**Pure Utility Function:**
- Pattern: No state, variadic args, pure computation
- Source: lib/startGiftCalculator.ts
- Apply: `calculateActiveFilterCount(...filterSets: ReadonlySet<unknown>[])` with reduce

**Type Alias Pattern:**
- Pattern: Export type alias with JSDoc
- Source: IdentityTypes.ts:8-22 shows IdentityListItem interface structure
- Apply: `export type EGOListItem = EGO` in EGOTypes.ts

**Test Pattern:**
- Pattern: Vitest describe/it, edge cases, pure function testing
- Source: lib/__tests__/startGiftCalculator.test.ts
- Apply: Create filterUtils.test.ts with 11 test cases

## Pattern Enforcement

**Files to Read Before Writing:**

| New File | Must Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `lib/filterUtils.ts` | `lib/startGiftCalculator.ts` | Pure function, variadic params, no side effects |
| `lib/__tests__/filterUtils.test.ts` | `lib/__tests__/startGiftCalculator.test.ts` | Vitest structure, edge cases, no mocks needed |

**Existing Patterns to Follow:**
- SeasonDropdown micro-suspense: Internal `useFilterI18nData()` + parent Suspense wrapper
- Cleanup pattern: useEffect(() => () => handleResetAll(), [])
- Filter state: Set<LiteralUnion> for type safety (EGOType, Season)

## Existing Utilities

**Search Results:**

| Category | Location | Finding |
|----------|----------|---------|
| Filter calculation | `lib/` | **None exist** - new utility needed |
| Pure function tests | `lib/__tests__/*.test.ts` | startGiftCalculator provides template |
| Constants (Sets) | `lib/constants.ts` | No Set utilities, filterUtils will be first |
| Type definitions | `types/*.ts` | Both Identity and EGO use same naming pattern |

**Verification:**
- SEASONS constant exists (constants.ts:528)
- Season type defined as `(typeof SEASONS)[number]` (constants.ts:533)
- EGOType union defined in EGOTypes.ts:7
- No existing filter count utility - safe to create new

## Gap Analysis

**Currently Missing:**
- `lib/filterUtils.ts` - Pure utility for summing filter Set sizes
- `lib/__tests__/filterUtils.test.ts` - Unit tests for utility
- `EGOListItem` type alias - Fixes broken import

**Needs Modification:**
- SeasonDropdown.tsx: Props type from `Set<number>` to `Set<Season>`
- IdentityPage.tsx: Season state + utility usage
- EGOPage.tsx: EGO type state + cleanup + utility usage
- IdentityList.tsx: Props season type `Set<number>` → `Set<Season>`
- EGOList.tsx: Props EGO type + import fix
- EGOList.test.tsx: Type import updates

**Can Reuse:**
- Micro-suspense pattern (SeasonDropdown implementation unchanged)
- Cleanup pattern from IdentityPage (lines 119-123)
- Filter state management pattern (useState with Set)
- Suspense wrapper pattern (both pages identical)

## Testing Requirements

### Manual UI Tests

**Type Safety Verification:**
- IDE shows error for `selectedEGOTypes.add('INVALID')`
- IDE autocomplete suggests only valid EGOType values
- No TypeScript errors in production build

**Micro-Suspense (Critical):**
- Language switch on EGOPage: Dropdown shows Skeleton, sidebar stays mounted
- Language switch on IdentityPage: Dropdown shows Skeleton, sidebar stays mounted
- Filter selections preserved during language switch
- No console errors during suspense

**Filter Functionality:**
- Identity page: All 7 filters work, badge count correct
- EGO page: All 6 filters work, badge count correct
- Reset All clears all filters
- Filter combinations use AND logic correctly

**Navigation Cleanup:**
- Select filters on EGOPage
- Navigate away and back
- Verify filter state is empty (not leaked)

**Mobile Responsive:**
- Resize to <1024px
- Filter sidebar collapses with badge count
- Selections preserved during resize

### Automated Tests

**Unit Tests (filterUtils.test.ts - 11 cases):**
- calculateActiveFilterCount() with no args → 0
- calculateActiveFilterCount() with empty sets → 0
- Single set: Set([1,2,3]) → 3
- Multiple sets: Set([1,2]), Set([3,4,5]) → 5
- Mixed types: Set<string>, Set<number>, Set<Season> → correct sum
- Input sets not mutated (immutability check)
- Identity scenario: 7 filter sets → correct total
- EGO scenario: 6 filter sets → correct total
- Order independence: (A, B) === (B, A)
- EGOType set: Set<EGOType>(['ZAYIN', 'TETH']) → 2
- Season set: Set<Season>([1, 2, 3]) → 3

**Integration Tests:**
- EGOList.test.tsx: Updated imports don't break 13 existing tests
- TypeScript compilation: `yarn tsc --noEmit` succeeds
- Full test suite: `yarn test` passes

**Type Safety Tests:**
- selectedEGOTypes only accepts valid EGOType literals
- selectedSeasons only accepts valid Season values
- SeasonDropdown enforces Season type in both pages

## Technical Constraints

**Micro-Suspense Safety:**
- Cannot modify SeasonDropdown internal implementation
- Only prop types changed, not data fetching logic
- Parent Suspense boundaries must remain unchanged
- Language switch must continue to work correctly

**Type Change Coordination:**
- IdentityPage season type change requires simultaneous update
- State declaration + SeasonDropdown caller + IdentityList props all updated together
- Single commit prevents intermediate broken state

**Runtime Behavior:**
- Season type is `number` at runtime (TypeScript only)
- Zero runtime performance impact from type changes
- Compile-time errors guide all necessary fixes

**React Compiler:**
- No manual memoization needed (React Compiler handles)
- Pure utility function auto-optimized
- Filter state changes trigger efficient re-renders

**Immutability:**
- Utility function must not mutate input Sets
- Use readonly generic constraint: `ReadonlySet<unknown>`
- Filter logic preserves Set immutability pattern

## Key Findings

**1. IdentityListItem.season Already Correct:**
- Spec says change from `number` to `Season`
- Verification at IdentityTypes.ts:21 shows already `season: Season`
- Only page state needs fixing, not type definition

**2. EGOPage Missing Cleanup:**
- IdentityPage has cleanup useEffect (lines 119-123)
- EGOPage lacks this defensive pattern
- Simple replication fixes navigation state leak

**3. EGOListItem Type Missing:**
- EGOList.tsx:1 imports non-existent type
- Type alias pattern matches IdentityListItem
- Single line addition: `export type EGOListItem = EGO`

**4. Identical Filter Count Pattern:**
- Identity: 7 filter Set sizes summed manually
- EGO: 6 filter Set sizes summed manually
- Same pattern, different count - perfect for utility extraction

**5. Micro-Suspense Preserved:**
- Type changes don't affect suspense mechanism
- Internal `useFilterI18nData()` call unchanged
- Parent Suspense boundaries already correct
- Only prop type signatures modified

**6. Execution Order Critical:**
- Create utility + tests first (TDD)
- Add type definitions second (fixes imports)
- Update components third (uses new types)
- Test suite validates all changes

## Implementation Checklist

**Phase 1: Foundation**
- [ ] Create `lib/filterUtils.ts` with calculateActiveFilterCount
- [ ] Create `lib/__tests__/filterUtils.test.ts` with 11 tests
- [ ] Verify tests pass: `yarn test filterUtils.test.ts`

**Phase 2: Type Definitions**
- [ ] Add `EGOListItem` type alias to EGOTypes.ts
- [ ] Verify IdentityListItem.season is already Season type

**Phase 3: Shared Component**
- [ ] Update SeasonDropdown props: `Set<number>` → `Set<Season>`

**Phase 4: Identity Page**
- [ ] Update IdentityPage season state: `Set<number>` → `Set<Season>`
- [ ] Replace filter count manual sum with utility call
- [ ] Update IdentityList props: season type to `Set<Season>`

**Phase 5: EGO Page**
- [ ] Update EGOPage EGO type state: `Set<string>` → `Set<EGOType>`
- [ ] Add cleanup useEffect after handleResetAll
- [ ] Replace filter count manual sum with utility call
- [ ] Update EGOList props: import EGOListItem, fix selectedEGOTypes type

**Phase 6: Tests**
- [ ] Update EGOList.test.tsx type imports
- [ ] Run full test suite: `yarn test`
- [ ] TypeScript check: `yarn tsc --noEmit`

**Phase 7: Manual Verification**
- [ ] Test language switching on both pages (micro-suspense)
- [ ] Verify filter functionality on both pages
- [ ] Test navigation cleanup (EGO page)
- [ ] Mobile responsive check

## Risk Mitigation

**Micro-Suspense Breakage:**
- Risk: Type changes could theoretically affect suspense
- Mitigation: Internal implementation unchanged, only prop types
- Verification: Explicit language switch test in checklist

**Type Mismatch Errors:**
- Risk: IdentityPage state vs SeasonDropdown props type mismatch
- Mitigation: Update both simultaneously in same commit
- Verification: TypeScript compilation will fail if mismatch

**Import Breaking:**
- Risk: EGOList imports EGOListItem before type exists
- Mitigation: Add type alias before modifying component
- Verification: Execution order ensures type exists first

**State Leak:**
- Risk: EGOPage missing cleanup leaks state on navigation
- Mitigation: Add cleanup useEffect matching Identity pattern
- Verification: Manual navigation test verifies cleanup works
