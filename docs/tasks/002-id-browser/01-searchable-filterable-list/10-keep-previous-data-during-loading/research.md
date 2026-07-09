# Research: Keep Previous Search Data During Language Change

## Spec Ambiguities
**NONE FOUND.** Spec is clear and well-defined.

## Spec-to-Code Mapping

| Requirement | File | Modification |
|-------------|------|--------------|
| Keep search mappings during language change | `hooks/useSearchMappings.ts` | Add `placeholderData: keepPreviousData` to both query options |
| Keep identity names during language change | `hooks/useIdentityListData.ts` | Add `placeholderData: keepPreviousData` to query options |
| Keep EGO names during language change | `hooks/useEGOListData.ts` | Add `placeholderData: keepPreviousData` to query options |
| Keep EGO Gift names during language change | `hooks/useEGOGiftListData.ts` | Add `placeholderData: keepPreviousData` to query options |

## Spec-to-Pattern Mapping

| Requirement | Pattern Source | Status |
|-------------|----------------|--------|
| TanStack Query placeholder | TanStack Query v5 `placeholderData` API | Built-in, not yet applied |
| Deferred hooks with empty state | Existing pattern in codebase | Already correct, needs option |
| List components handle empty data | `IdentityList.tsx` etc. | Already handles gracefully |

## Pattern Enforcement

| Modified File | MUST Read First | Pattern to Copy |
|---------------|-----------------|-----------------|
| `useSearchMappings.ts` | TanStack Query v5 docs | Add option to `queryOptions()` |
| `useIdentityListData.ts` | TanStack Query v5 docs | Add option to `queryOptions()` |
| `useEGOListData.ts` | TanStack Query v5 docs | Add option to `queryOptions()` |
| `useEGOGiftListData.ts` | TanStack Query v5 docs | Add option to `queryOptions()` |

## Existing Utilities

| Category | Location | Status |
|----------|----------|--------|
| TanStack Query helpers | `lib/queryClient.ts` | `keepPreviousData` is built-in |
| Deferred hooks | `hooks/use*Data.ts` | All exist, need option added |
| Constants | `lib/constants.ts` | No new constants needed |

## Gap Analysis

- **Currently missing:** `placeholderData: keepPreviousData` on 5 queries
- **Needs modification:** 4 factory functions in 4 files
- **Can reuse:** All existing hooks, consumers, boundaries

## Testing Requirements

### Manual UI Tests
- Navigate to Identity/EGO/EGO Gift pages
- Type search query, switch language
- Verify NO jarring empty flash
- Verify results stay visible until new data loads
- Verify smooth transition for both ASCII and language-specific search text

### Automated Verification
- [ ] `placeholderData: keepPreviousData` added to all 5 queries
- [ ] No TypeScript errors
- [ ] Hook return types unchanged
- [ ] Existing tests pass

### Edge Cases
- [ ] First page load (cold cache): Normal loading
- [ ] Rapid language switching: Latest language wins
- [ ] Empty search + language change: All cards visible throughout
- [ ] Cached language return: Instant results

## Technical Constraints

- **Dependencies:** None new - `keepPreviousData` built into TanStack Query v5
- **Compatibility:** Works with `useQuery()` deferred hooks only
- **Return types:** Unchanged - non-breaking, additive change
- **Consumer impact:** None - transparent improvement
