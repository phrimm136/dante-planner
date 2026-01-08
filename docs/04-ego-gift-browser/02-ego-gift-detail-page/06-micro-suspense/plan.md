# Execution Plan: Micro Suspense Pattern for EGO Gift Detail Page

## Planning Gaps
**NONE** - Research is complete and comprehensive. All patterns are documented, files identified, and implementation path is clear.

## Execution Overview

This is a **pattern application task** (not greenfield development). We're copying the Identity detail page's micro suspense pattern to the EGO Gift detail page. The strategy is:

1. **Phase 1 - Data Layer**: Separate the combined `useEGOGiftDetailData()` hook into spec-only and i18n-only hooks, following Identity pattern
2. **Phase 2 - Interface Layer**: Create three wrapper components that fetch i18n and wrap base components in Suspense
3. **Phase 3 - Integration**: Restructure EGOGiftDetailPage to use separated hooks and add Suspense boundaries for each i18n section
4. **Phase 4 - Tests**: Write unit tests for hooks and wrapper components, then manual verification

This approach minimizes risk by keeping existing code intact (backward compatibility for tooltip) and following proven patterns.

## Dependency Analysis (Senior Thinking)

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `hooks/useEGOGiftDetailData.ts` | Low | `schemas/EGOGiftSchemas.ts` | `EGOGiftDetailPage.tsx`, `EGOGiftTooltipContent.tsx` |
| `routes/EGOGiftDetailPage.tsx` | Low | New hooks, new wrapper components | None (leaf page) |
| `components/egoGift/EGOGiftNameI18n.tsx` (new) | Low | `useEGOGiftDetailI18n()`, `GiftName.tsx` | `EGOGiftDetailPage.tsx` |
| `components/egoGift/AllEnhancementsPanelI18n.tsx` (new) | Low | `useEGOGiftDetailI18n()`, `AllEnhancementsPanel.tsx` | `EGOGiftDetailPage.tsx` |
| `components/egoGift/EGOGiftMetadataI18n.tsx` (new) | Low | `useThemePackListData()`, `EGOGiftMetadata.tsx` | `EGOGiftDetailPage.tsx` |

### Ripple Effect Map

**If `useEGOGiftDetailData.ts` changes:**
- `EGOGiftDetailPage.tsx` → Will use new separated hooks (step 11)
- `EGOGiftTooltipContent.tsx` → No change (continues using combined hook for backward compat)
- New query keys created → No collision (different keys for spec vs i18n)

**If wrapper components are created:**
- No existing dependencies → Safe to create
- Only `EGOGiftDetailPage.tsx` will consume them → Isolated integration

**If `EGOGiftDetailPage.tsx` restructures:**
- No downstream consumers → Page is a leaf component
- Base components (`GiftName`, `AllEnhancementsPanel`, `EGOGiftMetadata`) → No changes to these

### High-Risk Modifications

**NONE** - All modified files are low-impact and domain-isolated per architecture-map.md. No high-impact files (constants.ts, queryClient.ts, router.tsx) are touched.

## Execution Order

### Phase 1: Data Layer (Hooks)

**1. Add `useEGOGiftDetailSpec()` to `hooks/useEGOGiftDetailData.ts`**
   - Depends on: none
   - Enables: [F1] Spec data loads once without language dependency
   - Pattern: Copy `useIdentityDetailSpec()` structure
   - Query key: `['egoGift', id]` (no language parameter)

**2. Add `useEGOGiftDetailI18n()` to `hooks/useEGOGiftDetailData.ts`**
   - Depends on: none
   - Enables: [F2] I18n data re-fetches on language change
   - Pattern: Copy `useIdentityDetailI18n()` structure
   - Query key: `['egoGift', id, 'i18n', language]`

**3. Verify existing `useEGOGiftDetailData()` still works for backward compatibility**
   - Depends on: steps 1-2
   - Enables: [F3] Tooltip component continues working
   - No code change - just verification that combined hook remains functional

### Phase 2: Interface Layer (Wrapper Components)

**4. Create `components/egoGift/EGOGiftNameI18n.tsx`**
   - Depends on: step 2
   - Enables: [F4] Name section suspends independently
   - Pattern: Similar to `IdentityHeaderWithI18n` (simpler - just name)
   - Props: `{ id: string, attributeType: AttributeType }`
   - Suspends: Uses `useEGOGiftDetailI18n()` to fetch name, passes to `GiftName`

**5. Create `components/egoGift/AllEnhancementsPanelI18n.tsx`**
   - Depends on: step 2
   - Enables: [F5] Descriptions panel suspends independently
   - Pattern: Similar to `PassiveCardI18n` wrapper structure
   - Props: `{ giftId: string, costs: EGOGiftEnhancementCost[] }`
   - Suspends: Fetches `giftI18n.descs`, passes to `AllEnhancementsPanel`

**6. Create `components/egoGift/EGOGiftMetadataI18n.tsx`**
   - Depends on: none (uses existing `useThemePackListData`)
   - Enables: [F6] Metadata (theme pack names) suspends independently
   - Pattern: Wrapper that fetches theme pack i18n, passes to `EGOGiftMetadata`
   - Props: `{ price: number, maxEnhancement: number, themePack: string[], difficulty: string }`
   - Suspends: Uses `useThemePackListData()` for `themePackI18n`

### Phase 3: Integration (Detail Page Restructure)

**7. Modify `EGOGiftDetailContent` in `routes/EGOGiftDetailPage.tsx` - Import new hooks and wrappers**
   - Depends on: steps 1-2, 4-6
   - Enables: All features
   - Add imports for `useEGOGiftDetailSpec`, new wrapper components

**8. Replace `useEGOGiftDetailData()` with `useEGOGiftDetailSpec()`**
   - Depends on: step 7
   - Enables: [F1] Spec loads once, stable on language change
   - Change: `const giftData = useEGOGiftDetailSpec(id)`
   - Remove: Old destructure `{ spec, i18n }`

**9. Update card and metadata to use spec-only data**
   - Depends on: step 8
   - Enables: [F7] Card remains visible during language switch
   - Change: Use `giftData` directly for card props
   - Remove i18n dependencies from card rendering

**10. Wrap `GiftName` in Suspense with `EGOGiftNameI18n`**
   - Depends on: steps 4, 8
   - Enables: [F4] Name suspends independently
   - Pattern: `<Suspense fallback={<GiftName name="" attributeType={...} />}><EGOGiftNameI18n id={id} attributeType={giftData.attributeType} /></Suspense>`

**11. Wrap `AllEnhancementsPanel` in Suspense with `AllEnhancementsPanelI18n`**
   - Depends on: steps 5, 8
   - Enables: [F5] Descriptions suspend independently
   - Pattern: `<Suspense fallback={<AllEnhancementsPanel descriptions={[]} costs={costs} />}><AllEnhancementsPanelI18n giftId={id} costs={costs} /></Suspense>`

**12. Wrap `EGOGiftMetadata` in Suspense with `EGOGiftMetadataI18n`**
   - Depends on: steps 6, 8
   - Enables: [F6] Theme pack names suspend independently
   - Pattern: `<Suspense fallback={<EGOGiftMetadata themePackNames={{}} ... />}><EGOGiftMetadataI18n price={...} themePack={...} ... /></Suspense>`

### Phase 4: Tests

**13. Write unit tests for `useEGOGiftDetailSpec()`**
   - Depends on: step 1
   - Enables: [T1] Verify spec hook loads without language key
   - Tests: Query key format, suspends on initial load, returns typed data

**14. Write unit tests for `useEGOGiftDetailI18n()`**
   - Depends on: step 2
   - Enables: [T2] Verify i18n hook includes language in key
   - Tests: Query key includes language, suspends on language change

**15. Write unit tests for wrapper components**
   - Depends on: steps 4-6
   - Enables: [T3] Verify wrappers fetch and pass correct props
   - Tests: Each wrapper renders base component with fetched data

**16. Manual verification - Initial page load**
   - Depends on: steps 7-12
   - Enables: [V1] Verify full page loads correctly
   - Test: Navigate to detail page, observe brief skeleton then full render

**17. Manual verification - Language switch (CRITICAL)**
   - Depends on: steps 7-12
   - Enables: [V2] Verify granular suspense behavior
   - Test: Switch languages, verify card stays visible, only text sections update

**18. Manual verification - Tooltip backward compatibility**
   - Depends on: step 3
   - Enables: [F3] Verify tooltip still works
   - Test: Hover gift in planner, verify tooltip shows correctly

## Test Steps (MANDATORY)

Tests are explicit steps in execution order (steps 13-18). Key tests:

**Unit Tests (Steps 13-15):**
- Hook query key correctness
- Suspense behavior (initial load vs language change)
- Wrapper component data flow

**Manual Verification (Steps 16-18):**
- Initial page load skeleton → full render
- Language switch granular behavior (CRITICAL - primary feature test)
- Backward compatibility (tooltip component)

## Verification Checkpoints

**After Phase 1 (Step 3):**
- ✓ New hooks export correct types
- ✓ Query keys match pattern (spec: no lang, i18n: with lang)
- ✓ Combined hook still works (no regression)

**After Phase 2 (Step 6):**
- ✓ All wrapper components compile without errors
- ✓ Props match base component requirements
- ✓ No circular dependencies introduced

**After Phase 3 (Step 12):**
- ✓ Detail page compiles without type errors
- ✓ All Suspense boundaries have fallbacks
- ✓ No missing imports

**After Phase 4 (Step 18):**
- ✓ Unit tests pass
- ✓ Manual verification confirms granular loading
- ✓ No console errors during language switch

## Risk Mitigation (from instructions.md Risk Assessment)

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| Language switch during page load | Steps 16-17 | Each Suspense boundary resolves independently - race is acceptable, last query wins |
| Theme pack ID not in i18n map | Step 6, 12 | Fallback already exists: `themePackNames[id]?.name ?? id` - no code change needed |
| Empty theme pack array | Step 6, 12 | Conditional already exists: `themePack.length > 0 ? ... : 'General'` - no code change |
| Multiple data dependencies race | Steps 10-12 | Wrap each section in own ErrorBoundary if needed (low priority - can defer) |
| Query key collision | Steps 1-2 | New hooks use different query keys - spec: `['egoGift', id]`, i18n: `['egoGift', id, 'i18n', lang]` |
| Tooltip regression | Step 3, 18 | Keep combined hook for backward compatibility, verify in step 18 |

## Dependency Verification Steps

**After modifying `useEGOGiftDetailData.ts` (Steps 1-3):**
- Verify `EGOGiftTooltipContent.tsx` still compiles (uses combined hook)
- Check no duplicate imports in detail page

**After creating wrapper components (Steps 4-6):**
- Verify no naming conflicts with existing components
- Check import paths resolve correctly

**After detail page integration (Steps 7-12):**
- Verify card component still receives correct props (spec data only)
- Verify base components (`GiftName`, `AllEnhancementsPanel`, `EGOGiftMetadata`) not modified
- Check no type assertions needed beyond existing usage

## Rollback Strategy

**If step fails:**

**Phase 1 failures (Steps 1-3):**
- Safe stopping point: Keep new hook exports, revert integration
- Rollback: Comment out new exports, detail page unchanged

**Phase 2 failures (Steps 4-6):**
- Safe stopping point: Delete new wrapper files, detail page unchanged
- Rollback: Remove new component files, no integration happened yet

**Phase 3 failures (Steps 7-12):**
- Safe stopping point after any step: Detail page reverts to using combined hook
- Rollback: Revert `EGOGiftDetailPage.tsx` to original structure, new components unused

**Phase 4 failures (Steps 13-18):**
- Safe stopping point: Implementation complete, tests can be fixed separately
- Rollback: If critical bug found, revert Phase 3 changes

**Global rollback:**
- All changes are additive (new exports, new files) or isolated (single page)
- Full rollback: Revert hook file, delete new components, revert detail page
- Backward compatibility maintained: Tooltip component unaffected
