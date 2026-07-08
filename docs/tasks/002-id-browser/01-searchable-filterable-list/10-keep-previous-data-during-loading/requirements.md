# Task: Keep Previous Search Data During Language Change

## Description
When users change the application language while a search query is active, the current implementation shows a jarring "No results" flash because the deferred i18n hooks return empty data during the fetch transition. This occurs because TanStack Query treats a language change as a new query (different query key), causing `data` to be `undefined` until the new fetch completes.

**Current behavior (problematic):**
1. User searches "W" for W Corp identities (EN)
2. User switches language to Korean (KR)
3. Query key changes → new fetch starts → `data` becomes `undefined`
4. Deferred hooks return empty mappings/names
5. Filter runs with empty data → 0 results temporarily
6. KR data loads → filter re-runs → results appear
7. User sees jarring flash: results → empty → results

**Desired behavior (industry standard):**
1. User searches "W" for W Corp identities (EN)
2. User switches language to Korean (KR)
3. Query key changes → new fetch starts
4. **Previous EN data stays visible** while KR data loads (placeholderData)
5. KR data arrives → seamlessly replaces EN data
6. Filter re-runs with KR data → shows updated results
7. User sees smooth transition: EN results → KR results

**Two search scenarios handled correctly:**
- **Language-specific text** (e.g., "이상" Korean): KR results stay visible → EN loads → 0 results (smooth transition to expected empty state)
- **ASCII text** (e.g., "W"): EN results stay visible → KR loads → KR results (smooth transition between result sets)

**Solution:** Use TanStack Query's `placeholderData: keepPreviousData` option, which is the industry standard pattern for maintaining UI stability during query key changes (pagination, filters, i18n transitions).

## Research
- TanStack Query v5 `placeholderData` API: https://tanstack.com/query/latest/docs/framework/react/guides/placeholder-query-data
- `keepPreviousData` utility function behavior
- Current deferred hook implementations in the codebase
- Verify no breaking changes to existing hook consumers

## Scope
Read for context:
- `frontend/src/hooks/useSearchMappings.ts` - Current deferred hook implementation
- `frontend/src/hooks/useIdentityListData.ts` - Identity i18n deferred hook
- `frontend/src/hooks/useEGOListData.ts` - EGO i18n deferred hook
- `frontend/src/hooks/useEGOGiftListData.ts` - EGO Gift i18n deferred hook
- `frontend/src/components/identity/IdentityList.tsx` - Consumer of deferred hooks
- TanStack Query documentation on `placeholderData`

## Target Code Area
Files to modify (add `placeholderData: keepPreviousData` to queries):
- `frontend/src/hooks/useSearchMappings.ts` - 2 queries (keywordMatch, unitKeywords)
- `frontend/src/hooks/useIdentityListData.ts` - 1 query (identityNameList)
- `frontend/src/hooks/useEGOListData.ts` - 1 query (egoNameList)
- `frontend/src/hooks/useEGOGiftListData.ts` - 1 query (egoGiftNameList)

**No changes needed to list components** - they receive data as before, just with smoother transitions.

## System Context (Senior Thinking)
- **Feature domain**: Identity Browser, EGO Browser, EGO Gift Browser (Filter Sidebar / Search subsystem)
- **Core files in this domain** (from architecture-map):
  - `hooks/useSearchMappings.ts` (both suspending and deferred variants)
  - `hooks/useIdentityListData.ts`, `useEGOListData.ts`, `useEGOGiftListData.ts`
  - List components: `IdentityList.tsx`, `EGOList.tsx`, `EGOGiftList.tsx`
- **Cross-cutting concerns touched**:
  - i18n data loading (`static/i18n/{lang}/*.json`)
  - TanStack Query caching and placeholder behavior
  - Filter computation in list components (indirect effect)

## Impact Analysis
- **Files being modified**:
  - `useSearchMappings.ts` (Medium impact - shared by all list components)
  - `useIdentityListData.ts` (Low impact - domain-isolated)
  - `useEGOListData.ts` (Low impact - domain-isolated)
  - `useEGOGiftListData.ts` (Low impact - domain-isolated)
- **What depends on these files**:
  - `IdentityList.tsx`, `EGOList.tsx`, `EGOGiftList.tsx` consume these hooks
  - Page components (`IdentityPage.tsx`, etc.) render the list components
- **Potential ripple effects**:
  - None expected - `placeholderData` is additive, doesn't change return type
  - Hook consumers receive same data shape, just with smoother loading transitions
- **High-impact files to watch**: `useSearchMappings.ts` - verify both suspending and deferred variants work correctly

## Risk Assessment
- **Edge cases**:
  - First page load (no previous data): Should work normally, `placeholderData` only applies when previous data exists
  - Rapid language switching: TanStack Query handles query cancellation automatically
  - Cache expiration: If old language data is stale/expired, may briefly show stale then fresh data
- **Performance concerns**:
  - Minimal - `keepPreviousData` just holds reference to previous result, no extra computation
- **Backward compatibility**:
  - Fully backward compatible - no API changes to hook consumers
  - Return types unchanged, behavior improved
- **Security considerations**: None (read-only i18n data, no user input involved)

## Testing Guidelines

### Manual UI Testing
1. Navigate to Identity page (`/identity`)
2. Type "W" in the search box
3. Verify W Corp identities are shown (filtered results)
4. Note current visible results count
5. Open language switcher and change to Korean (KR)
6. **Verify NO empty flash** - results should stay visible during transition
7. Verify after ~1-2 seconds, Korean names appear on cards
8. Verify filter results may change (based on KR name matching)
9. Clear search box, verify all cards visible
10. Type "이상" (Korean for "Faust")
11. Verify Korean results shown
12. Switch to English (EN)
13. **Verify NO empty flash** - Korean results stay until EN loads
14. Verify after EN loads, results become 0 (Korean text doesn't match EN names)
15. Repeat steps 1-14 for EGO page (`/ego`)
16. Repeat steps 1-14 for EGO Gift page (`/ego-gift`)

### Automated Functional Verification
- [ ] placeholderData configured: All deferred i18n queries use `keepPreviousData`
- [ ] Smooth transition: No "No results" flash during language change with active search
- [ ] Previous data visible: Old language results stay visible while new language loads
- [ ] Data replacement: New language data replaces old data once loaded
- [ ] Filter re-computation: Filter logic runs with new data after load completes
- [ ] First load unaffected: Initial page load works normally (no previous data to keep)

### Edge Cases
- [ ] Empty search + language change: Works normally, all cards visible throughout
- [ ] First visit (cold cache): No previous data, normal loading behavior
- [ ] Rapid language switching: No stale data issues, latest language data shown
- [ ] Language-specific search text: Smooth transition to 0 results (not jarring flash)
- [ ] Cached language return: Instant results when switching to previously loaded language

### Integration Points
- [ ] TanStack Query cache: Verify cache behavior with `keepPreviousData`
- [ ] i18n system: Language change triggers correct query key change
- [ ] Filter computation: useMemo in list components recomputes when data changes
- [ ] Suspense boundaries: Deferred hooks should NOT trigger Suspense (still using useQuery)
