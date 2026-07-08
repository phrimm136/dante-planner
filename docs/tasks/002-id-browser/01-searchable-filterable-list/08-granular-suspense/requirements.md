# Task: Granular Suspense for Identity and EGO Gift List Pages

## Description

Apply EGO list page's granular suspension pattern to Identity and EGO Gift list pages. Currently, when users change language, the Identity and EGO Gift grids completely disappear (show skeleton) while i18n data loads. The EGO page already handles this gracefully - the grid stays visible and only individual card names show loading skeletons.

The target behavior:
- Grid remains visible during language change (no full grid skeleton flash)
- Each card's name independently shows a small skeleton while its name loads
- Search by name temporarily returns no matches during language load (acceptable UX)
- Once i18n loads, names appear and search works correctly

The pattern involves:
1. **Deferred hooks** that return empty objects instead of suspending (for filtering/search)
2. **Name components** with internal Suspense (for individual card name display)
3. **Spec-only data flow** at the grid level (no i18n merge in CardGrid)

## Research

- **Reference implementation:** `EGOPage.tsx`, `EGOList.tsx`, `EGOCard.tsx`, `EGOName.tsx`
- **Deferred hook pattern:** `useEGOListI18nDeferred()` in `useEGOListData.ts` (lines 78-94)
- **Name component pattern:** `EGOName.tsx` - uses suspending hook wrapped in Suspense
- **Card internal Suspense:** `EGOCard.tsx` lines 126-128 - Suspense around EGOName
- **Type pattern:** `EGOListItem` in `EGOTypes.ts` - spec-only, no name field
- **Search with deferred hook:** `EGOList.tsx` lines 40-42, 89-106

## Scope

Read for context:
- `frontend/src/hooks/useEGOListData.ts` (reference implementation)
- `frontend/src/components/ego/EGOList.tsx` (reference implementation)
- `frontend/src/components/ego/EGOCard.tsx` (reference implementation)
- `frontend/src/components/ego/EGOName.tsx` (reference implementation)
- `frontend/src/routes/EGOPage.tsx` (reference implementation)
- `frontend/src/types/EGOTypes.ts` (reference types)

## Target Code Area

### New Files
- `frontend/src/components/identity/IdentityName.tsx`
- `frontend/src/components/egoGift/EGOGiftName.tsx`

### Modified Files
- `frontend/src/hooks/useIdentityListData.ts` - Add deferred hook
- `frontend/src/hooks/useEGOGiftListData.ts` - Add `useQuery` import + deferred hook
- `frontend/src/types/IdentityTypes.ts` - Add `IdentityListItem` type (spec-only)
- `frontend/src/types/EGOGiftTypes.ts` - Add `EGOGiftListItemSpec` type (spec-only)
- `frontend/src/components/identity/IdentityCard.tsx` - Use IdentityName with internal Suspense
- `frontend/src/components/identity/IdentityCardLink.tsx` - Update type to spec-only
- `frontend/src/components/identity/IdentityList.tsx` - Use deferred hook for name search
- `frontend/src/components/egoGift/EGOGiftCard.tsx` - Use EGOGiftName with internal Suspense
- `frontend/src/components/egoGift/EGOGiftCardLink.tsx` - Update type to spec-only
- `frontend/src/components/egoGift/EGOGiftList.tsx` - Use deferred hook for name search
- `frontend/src/routes/IdentityPage.tsx` - Remove inner Suspense around CardGrid, delete CardGridSkeleton
- `frontend/src/routes/EGOGiftPage.tsx` - Remove inner Suspense around CardGrid, delete CardGridSkeleton

## Testing Guidelines

### Manual UI Testing

**Identity Page:**
1. Navigate to `/identity` page
2. Wait for initial load, verify grid displays correctly
3. Open browser DevTools Network tab, throttle to "Slow 3G"
4. Change language from EN to KR using language selector
5. Verify the card grid **stays visible** (cards don't disappear)
6. Verify individual card names briefly show skeletons, then update
7. While names are loading, type a search query (e.g., "Faust")
8. Verify search returns empty (no matches) during load - this is expected
9. Wait for i18n to load, verify search now works correctly
10. Change language to JP, verify same behavior
11. Change back to EN, verify same behavior
12. Disable network throttling

**EGO Gift Page:**
1. Navigate to `/ego-gift` page
2. Wait for initial load, verify grid displays correctly
3. Open browser DevTools Network tab, throttle to "Slow 3G"
4. Change language from EN to KR using language selector
5. Verify the card grid **stays visible** (cards don't disappear)
6. Verify individual card names briefly show skeletons, then update
7. While names are loading, type a search query (e.g., "Combustion")
8. Verify search returns empty during load
9. Wait for i18n to load, verify search now works correctly
10. Disable network throttling

**Compare with EGO Page (Reference):**
1. Navigate to `/ego` page
2. Throttle network to "Slow 3G"
3. Change language
4. Verify behavior matches Identity and EGO Gift pages
5. This is the target behavior - all three should match

### Automated Functional Verification

- [ ] **Deferred hook returns empty object:** `useIdentityListI18nDeferred()` returns `{}` while loading, never undefined
- [ ] **Deferred hook returns data after load:** Returns populated `Record<string, string>` once query completes
- [ ] **Name component suspends:** `IdentityName` triggers Suspense boundary when used
- [ ] **Card internal Suspense works:** Each card shows name skeleton independently
- [ ] **Grid stays mounted:** No full grid re-render during language change
- [ ] **Search uses deferred data:** `IdentityList` uses deferred hook for name filtering
- [ ] **Type safety:** No TypeScript errors after changes (`yarn tsc`)
- [ ] **Build succeeds:** `yarn build` completes without errors

### Edge Cases

- [ ] **Initial load:** First page load still suspends at outer boundary (ListPageSkeleton) - this is correct
- [ ] **Rapid language switching:** Changing language multiple times quickly doesn't cause errors
- [ ] **Empty search during load:** Search bar with query during i18n load shows "No results" message
- [ ] **ID fallback:** Cards display ID as name when i18n data is missing (e.g., `i18n[id] || id`)
- [ ] **Filter + search combo:** Filters work correctly while search is temporarily disabled during load
- [ ] **Browser back/forward:** Navigation doesn't cause unexpected suspension behavior

### Integration Points

- [ ] **SearchMappingsDeferred:** Existing `useSearchMappingsDeferred()` continues to work alongside new deferred hooks
- [ ] **Filter sidebar:** Filters (Sinner, Keyword, etc.) work independently of name loading
- [ ] **Season/Association dropdowns:** These have their own Suspense boundaries, unaffected by this change
- [ ] **Card links work:** Clicking cards navigates to detail pages correctly with spec-only data
