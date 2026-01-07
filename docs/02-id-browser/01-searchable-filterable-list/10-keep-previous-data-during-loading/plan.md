# Plan: Keep Previous Search Data During Language Change

## Planning Gaps
**NONE FOUND.** Research complete and accurate.

## Execution Overview

Add TanStack Query's `placeholderData: keepPreviousData` to 5 query options factories across 4 hook files. Surgical, low-risk change improving UX during language transitions without affecting hook APIs.

## Dependency Analysis

### Files Being Modified

| File | Impact Level | Used By |
|------|--------------|---------|
| `useSearchMappings.ts` | Medium | `IdentityList`, `EGOList`, `EGOGiftList` |
| `useIdentityListData.ts` | Low | `IdentityList`, `IdentityPage` |
| `useEGOListData.ts` | Low | `EGOList`, `EGOPage` |
| `useEGOGiftListData.ts` | Low | `EGOGiftList`, `EGOGiftPage` |

### Ripple Effect Map
- Query options change → Deferred hooks return previous data during loading
- Return types unchanged → No consumer breakage
- Import changes → TypeScript build catches errors

### High-Risk Modifications
- `useSearchMappings.ts`: Shared by all lists - test all 3 pages after change

## Execution Order

### Phase 1: Search Mappings (Steps 1-4)
1. Add `keepPreviousData` import to `useSearchMappings.ts`
2. Add `placeholderData: keepPreviousData` to `createKeywordMatchQueryOptions()`
3. Add `placeholderData: keepPreviousData` to `createUnitKeywordsQueryOptions()`
4. Run `yarn build` - verify TypeScript passes

### Phase 2: Entity Name Lists (Steps 5-10)
5. Add `keepPreviousData` import to `useIdentityListData.ts`
6. Add `placeholderData: keepPreviousData` to `createIdentityNameListQueryOptions()`
7. Add `keepPreviousData` import to `useEGOListData.ts`
8. Add `placeholderData: keepPreviousData` to `createEGONameListQueryOptions()`
9. Add `keepPreviousData` import to `useEGOGiftListData.ts`
10. Add `placeholderData: keepPreviousData` to `createEGOGiftNameListQueryOptions()`

### Phase 3: Verification (Steps 11-14)
11. Run `yarn build` - verify all files compile
12. Manual test: Identity page language switch with search
13. Manual test: EGO page language switch with search
14. Manual test: EGO Gift page language switch with search

## Verification Checkpoints

| After Step | Verification |
|------------|--------------|
| 4 | `yarn build` passes |
| 11 | `yarn build` passes (all 4 files) |
| 12-14 | No "No results" flash during language switch |

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| First page load regression | Test in incognito (cold cache) |
| Rapid language switching | Test EN→KR→JP rapid switching |
| TypeScript errors | `yarn build` before manual testing |

## Dependency Verification Steps
- After step 11: Verify all 3 list pages render cards correctly
- After step 14: Verify filter logic still works (search "W" → W Corp results)

## Rollback Strategy
Remove `keepPreviousData` import and `placeholderData` option from all 4 files.
```
git checkout -- frontend/src/hooks/useSearchMappings.ts frontend/src/hooks/useIdentityListData.ts frontend/src/hooks/useEGOListData.ts frontend/src/hooks/useEGOGiftListData.ts
```
