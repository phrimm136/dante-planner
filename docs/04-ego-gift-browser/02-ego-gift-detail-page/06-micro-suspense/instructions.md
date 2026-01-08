# Task: Apply Micro Suspense Pattern to EGO Gift Detail Page

## Description

Apply the Identity detail page's micro suspense pattern to the EGO Gift detail page to achieve granular loading states during language switching. Currently, the entire EGO Gift detail page suspends when the user changes language, showing a full-page skeleton. The target behavior is to suspend only the i18n-dependent sections (name, descriptions, theme pack names) while keeping the spec-based shell (card image, metadata labels, layout structure) stable and visible.

**Target sections for micro suspense:**
1. **Gift name**: The gift's translated name in the left column header
2. **Gift descriptions**: The enhancement level descriptions in the right column panel
3. **Theme pack names**: The translated theme pack names in the metadata section

**Expected behavior:**
- Initial page load: Show brief skeleton, then render complete page
- Language switch: Only name/descriptions/theme pack sections show loading skeletons
- Layout stability: Card, metadata labels, and page structure remain visible during language switch
- Independent loading: Each section loads and updates independently without blocking others
- Fallback consistency: Loading states use empty/placeholder versions of actual components (not spinners)

**Pattern source:** Identity detail page (`routes/IdentityDetailPage.tsx`) uses this pattern successfully with separate suspense boundaries for header, skills, passives, and sanity sections.

## Research

- Read `routes/IdentityDetailPage.tsx` to understand the micro suspense implementation pattern
- Study `hooks/useIdentityDetailData.ts` to see how spec/i18n hooks are separated
- Review `components/identity/IdentityHeaderWithI18n.tsx` as wrapper component pattern reference
- Examine current `routes/EGOGiftDetailPage.tsx` to understand existing structure
- Check `hooks/useEGOGiftDetailData.ts` current implementation (combined spec+i18n)
- Review `hooks/useThemePackListData.ts` for theme pack loading pattern
- Read `components/egoGift/GiftName.tsx` to understand name display component
- Review `components/egoGift/AllEnhancementsPanel.tsx` for description panel structure
- Check `components/egoGift/EGOGiftMetadata.tsx` for metadata display with theme packs

## Scope

**Files to READ for context:**
- `routes/IdentityDetailPage.tsx` - Reference implementation
- `hooks/useIdentityDetailData.ts` - Separated hooks pattern
- `components/identity/IdentityHeaderWithI18n.tsx` - Wrapper pattern
- `routes/EGOGiftDetailPage.tsx` - Current implementation
- `hooks/useEGOGiftDetailData.ts` - Current combined approach
- `hooks/useThemePackListData.ts` - Theme pack data loading
- `components/egoGift/GiftName.tsx` - Name component
- `components/egoGift/AllEnhancementsPanel.tsx` - Descriptions panel
- `components/egoGift/EGOGiftMetadata.tsx` - Metadata with theme packs
- `components/egoGift/EGOGiftCard.tsx` - Card display (uses spec data only)

## Target Code Area

**Files to MODIFY:**
- `hooks/useEGOGiftDetailData.ts` - Add `useEGOGiftDetailSpec()` and `useEGOGiftDetailI18n()` hooks
- `routes/EGOGiftDetailPage.tsx` - Restructure suspense boundaries

**Files to CREATE:**
- `components/egoGift/EGOGiftNameI18n.tsx` - Name wrapper with i18n loading
- `components/egoGift/AllEnhancementsPanelI18n.tsx` - Descriptions wrapper with i18n loading
- `components/egoGift/EGOGiftMetadataI18n.tsx` - Metadata wrapper with theme pack i18n loading

**Optional optimization:**
- `main.tsx` or `lib/router.tsx` - Add global theme pack caching at app root

## System Context (Senior Thinking)

**Feature domain:** EGO Gift Browser (architecture-map line 17)

**Core files in this domain:**
- `routes/EGOGiftPage.tsx`, `routes/EGOGiftDetailPage.tsx` (browser + detail)
- `hooks/useEGOGiftListData.ts` (list data)
- `hooks/useEGOGiftDetailData.ts` (detail data)
- `lib/egoGiftFilter.ts` (filtering logic)
- `components/egoGift/*` (gift components)

**Cross-cutting concerns touched:**
- **i18n** (lib/i18n.ts, static/i18n/{lang}/egoGift/*.json) - Primary concern
- **Validation** (schemas/EGOGiftSchemas.ts) - Data validation via Zod
- **Theme** (contexts/ThemeContext.tsx) - Theme pack names display
- **Constants** (lib/constants.ts) - DETAIL_PAGE ratios, possibly enhancement constants

**Related patterns:**
- Identity/EGO/Gift Browser Pattern (architecture-map line 301-362)
- Modular Detail Page Layout Pattern (architecture-map line 364-400)
- Deferred Hooks (Non-Blocking) Pattern (architecture-map line 108-150)
- Data Flow Patterns: Static JSON → Component (architecture-map line 89-107)

## Impact Analysis

**Files being modified:**

| File | Impact Level | Reason |
|------|--------------|--------|
| `hooks/useEGOGiftDetailData.ts` | Low | Adding new exports, existing function unchanged |
| `routes/EGOGiftDetailPage.tsx` | Low | Page-isolated, EGO Gift browser only |
| `components/egoGift/*` (new wrappers) | Low | New components, no existing dependencies |

**What depends on modified files:**
- `useEGOGiftDetailData()`: Currently used by `EGOGiftDetailPage.tsx` and `EGOGiftTooltipContent.tsx`
- `EGOGiftDetailPage.tsx`: No dependencies (leaf page component)
- New wrapper components: No existing dependencies (new files)

**Potential ripple effects:**
- **Tooltip component**: `EGOGiftTooltipContent.tsx` uses `useEGOGiftDetailData()` - verify it continues to work with combined hook
- **Query cache**: New query keys for separated hooks must align with existing keys to avoid double-fetching
- **Theme pack loading**: If moved to global cache, all pages using theme packs benefit from faster loads

**High-impact files to watch:**
- None - all modified files are low-impact and domain-isolated
- Architecture-map identifies `lib/constants.ts` as high-impact, but we're only reading from it, not modifying

## Risk Assessment

**Edge cases identified:**

1. **Language switch during page load**
   - Scenario: User switches language while gift data is still loading
   - Risk: Race condition between spec and i18n queries
   - Mitigation: Each section has independent Suspense, shows appropriate skeleton

2. **Theme pack ID not in i18n map**
   - Scenario: Gift references theme pack ID that doesn't exist in i18n data
   - Current behavior: Falls back to ID string via `themePackNames[id]?.name ?? id`
   - Risk: Low - fallback works, just shows ugly ID
   - Mitigation: Already handled, acceptable degradation

3. **Empty theme pack array**
   - Scenario: Gift has `themePack: []` (no associated theme packs)
   - Current behavior: Metadata shows "General" via `themePack.length > 0 ? ... : t('egoGift.general')`
   - Risk: None - already handled correctly
   - Mitigation: Verify empty array doesn't break i18n loading

4. **Multiple data dependencies race**
   - Scenario: Gift i18n, theme pack i18n, and spec all loading simultaneously
   - Risk: If one query fails, should other sections still render?
   - Mitigation: Wrap each Suspense in ErrorBoundary for isolated failure handling

5. **Query key collision**
   - Scenario: New `useEGOGiftDetailI18n()` creates duplicate query key
   - Risk: Data fetches twice, wastes bandwidth
   - Mitigation: New hook must use SAME query key as current i18n portion (`['egoGift', id, 'i18n', language]`)

**Performance concerns:**

1. **Query key explosion**
   - New separated hooks add query keys per gift
   - Impact: Low - TanStack Query caches with 7-day stale time
   - 100 gifts × 2 queries = manageable cache size

2. **Suspense waterfall**
   - If sections render sequentially, slower perceived load
   - Mitigation: All queries start in parallel at page level, then distribute to sections

3. **Theme pack global caching**
   - Loading at root level prevents per-page fetches
   - Benefit: Instant theme pack names on subsequent gift pages
   - Trade-off: Initial app load slightly slower (acceptable)

**Backward compatibility:**
- Not a concern - no backward compatibility required per user
- Can fully refactor to match Identity pattern
- Existing `useEGOGiftDetailData()` can remain for tooltip component or be deprecated

**Security considerations:**
- None - only changing data loading patterns, not handling user input or auth
- All data comes from static JSON files (no XSS risk)

## Testing Guidelines

### Manual UI Testing

**Initial Page Load:**
1. Open browser and navigate to `/ego-gift`
2. Click any EGO Gift card to open detail page
3. Observe loading behavior: Brief skeleton should appear, then full page renders
4. Verify all sections load: Gift name, card image, metadata (price, enhancement, theme pack), descriptions panel
5. Verify no console errors related to query loading

**Language Switch - Primary Test:**
1. With EGO Gift detail page open, click language selector in header
2. Switch from English to Korean (or any other language)
3. **Critical verification:**
   - Gift card image should remain visible (no flash)
   - Metadata labels ("Price:", "Enhancement:", "Theme Pack:") should remain visible
   - Gift name should briefly show empty state, then update to Korean
   - Theme pack names should briefly show placeholder, then update to Korean
   - Enhancement descriptions should briefly show empty rows, then update to Korean
4. Verify page does NOT show full-page skeleton
5. Verify layout does NOT shift during language switch
6. Repeat language switch back to English - verify same granular behavior

**Language Switch - Multiple Times:**
1. Rapidly switch between English → Korean → Japanese
2. Verify no race conditions cause incorrect text to display
3. Verify each section updates independently without blocking others
4. Check browser console for duplicate fetch warnings

**Navigation Between Gifts:**
1. Open first gift detail page, wait for full load
2. Navigate to second gift detail page
3. Observe theme pack names load instantly (global cache hit)
4. Switch language on second gift
5. Verify theme pack i18n updates without re-fetching entire theme pack list

**Empty State Handling:**
1. Find a gift with empty theme pack array (if exists)
2. Verify metadata shows "General" instead of theme pack names
3. Switch language - verify "General" translation updates correctly

**Error Recovery:**
1. Use browser DevTools Network tab to simulate failed i18n fetch (block request)
2. Verify error boundary catches failure
3. Verify other sections still render (isolated failure)
4. Refresh page - verify retry succeeds

### Automated Functional Verification

**Spec Data Stability:**
- [ ] Spec data (card, price, max enhancement, difficulty) loads once on page open
- [ ] Spec data does NOT re-fetch on language change
- [ ] Query key for spec has no language parameter: `['egoGift', id, 'spec']`

**i18n Data Loading:**
- [ ] Gift name loads with query key: `['egoGift', id, 'i18n', language]`
- [ ] Gift descriptions load with same query key (shared i18n data)
- [ ] Theme pack names load with query key: `['themePack', 'list', 'i18n', language]`
- [ ] Language change invalidates all i18n queries, not spec queries

**Granular Suspense:**
- [ ] Name section has independent Suspense boundary with `<GiftName name="" />` fallback
- [ ] Descriptions section has independent Suspense boundary with `<AllEnhancementsPanel descriptions={[]} />` fallback
- [ ] Metadata section has independent Suspense boundary with `<EGOGiftMetadata themePackNames={{}} />` fallback
- [ ] Each Suspense boundary resolves independently

**Fallback Components:**
- [ ] Name fallback shows empty string, preserves layout (no height shift)
- [ ] Descriptions fallback shows empty array (enhancement labels visible, descriptions empty)
- [ ] Metadata fallback shows empty theme pack map (falls back to ID strings)

**Query Cache Efficiency:**
- [ ] No duplicate fetches for same gift i18n data
- [ ] Theme pack data cached globally across all detail pages
- [ ] First gift page loads theme pack, subsequent pages hit cache

### Edge Cases

**Theme Pack Edge Cases:**
- [ ] Missing theme pack ID: Falls back to ID string, no crash
- [ ] Empty theme pack array: Shows "General" translation
- [ ] Multiple theme packs: All names render correctly with comma separation

**Loading Race Conditions:**
- [ ] Language switch during initial page load: Sections update independently
- [ ] Multiple rapid language switches: Last selected language wins, no stale data displayed
- [ ] Navigation during language switch: New page loads correctly without interference

**Error Scenarios:**
- [ ] Gift i18n fetch fails: ErrorBoundary shows message, spec data still visible
- [ ] Theme pack i18n fetch fails: ErrorBoundary shows message in metadata section only
- [ ] Invalid gift ID: Detail page shows 404 or error (existing behavior preserved)

**Type Safety:**
- [ ] `useEGOGiftDetailSpec()` returns `EGOGiftData` (not `EGOGiftData | undefined`)
- [ ] `useEGOGiftDetailI18n()` returns `EGOGiftI18n` (not `EGOGiftI18n | undefined`)
- [ ] No type assertions (`as`) needed in detail page component

### Integration Points

**Tooltip Component Integration:**
- [ ] `EGOGiftTooltipContent.tsx` continues to use `useEGOGiftDetailData()` (combined hook)
- [ ] Tooltip loading behavior unchanged (existing Suspense boundary works)
- [ ] No regression in planner gift tooltips

**Detail Page Layout Integration:**
- [ ] `DetailPageLayout` component continues to work with new structure
- [ ] `DetailLeftPanel` and `DetailRightPanel` preserve 4:6 ratio
- [ ] Mobile view (`MobileDetailTabs`) not affected by suspense changes

**Theme Pack Global Caching (if implemented):**
- [ ] Theme pack loads once at app startup
- [ ] All pages using theme packs (detail pages, planner) benefit from cache
- [ ] Language switch invalidates theme pack cache, re-fetches for new language
- [ ] No performance regression on initial app load

**Existing EGO Gift Features:**
- [ ] EGO Gift list page unaffected (uses `useEGOGiftListData`, different hook)
- [ ] EGO Gift filtering unaffected
- [ ] Navigation from list to detail page works correctly
- [ ] Back button from detail to list works correctly
