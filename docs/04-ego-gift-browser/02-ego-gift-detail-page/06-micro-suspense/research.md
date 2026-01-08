# Research: Micro Suspense Pattern for EGO Gift Detail Page

## Spec Ambiguities
**NONE FOUND** - Spec is clear with explicit requirements, testing guidelines, and reference implementations.

---

## Spec-to-Code Mapping

| Requirement | File | Modification |
|-------------|------|--------------|
| Spec data hook (no lang key) | `hooks/useEGOGiftDetailData.ts` | Add `useEGOGiftDetailSpec()` - query without language parameter |
| I18n data hook (with lang key) | `hooks/useEGOGiftDetailData.ts` | Add `useEGOGiftDetailI18n()` - query with language in key |
| Gift name suspense | `routes/EGOGiftDetailPage.tsx` | Wrap `GiftName` in Suspense with empty name fallback |
| Descriptions suspense | `routes/EGOGiftDetailPage.tsx` | Wrap `AllEnhancementsPanel` in Suspense with empty array fallback |
| Theme pack suspense | `routes/EGOGiftDetailPage.tsx` | Wrap `EGOGiftMetadata` in Suspense with empty map fallback |
| Name wrapper component | `components/egoGift/EGOGiftNameI18n.tsx` | New - fetches i18n name, wraps `GiftName` |
| Descriptions wrapper | `components/egoGift/AllEnhancementsPanelI18n.tsx` | New - fetches i18n descriptions, wraps panel |
| Metadata wrapper | `components/egoGift/EGOGiftMetadataI18n.tsx` | New - fetches theme pack i18n, wraps metadata |
| Keep spec stable | `routes/EGOGiftDetailPage.tsx` | Call `useEGOGiftDetailSpec()` once in content component |
| Keep card visible | `routes/EGOGiftDetailPage.tsx` | Card uses spec data only - no Suspense needed |

---

## Spec-to-Pattern Mapping

**New Components:**
- `EGOGiftNameI18n.tsx` → Pattern: `IdentityHeaderI18n.tsx` (line 28-38) - fetch i18n + pass name to base
- `AllEnhancementsPanelI18n.tsx` → Pattern: `PassiveCardI18n.tsx` + `SkillsSectionI18n.tsx` - wrapper with fallback
- `EGOGiftMetadataI18n.tsx` → Pattern: `IdentityHeaderWithI18n.tsx` - simple wrapper with i18n fetch

**Modified Hooks:**
- `useEGOGiftDetailSpec()` → Pattern: `useIdentityDetailSpec()` (line 53-56) - query WITHOUT language key
- `useEGOGiftDetailI18n()` → Pattern: `useIdentityDetailI18n()` (line 68-74) - query WITH language key
- Keep `useEGOGiftDetailData()` → Existing combined hook for tooltip backward compatibility

---

## Pattern Enforcement (MANDATORY READ FIRST)

| New File | MUST Read First | Pattern to Apply |
|----------|-----------------|------------------|
| `EGOGiftNameI18n.tsx` | `IdentityHeaderI18n.tsx` | Fetch i18n, pass name with empty string fallback |
| `AllEnhancementsPanelI18n.tsx` | `PassiveI18n.tsx` | Wrapper with empty array fallback |
| `EGOGiftMetadataI18n.tsx` | `IdentityHeaderI18n.tsx` | Fetch theme pack i18n, pass themePackNames map |
| Modified `useEGOGiftDetailData.ts` | `useIdentityDetailData.ts` | Add separated hooks; keep combined for backward compat |
| Modified `EGOGiftDetailPage.tsx` | `IdentityDetailPage.tsx` (line 34-434) | Use separated hooks; wrap sections in Suspense |

---

## Existing Utilities (Reusable Code)

| Category | Location | Existing Functions |
|----------|----------|-------------------|
| Query key factories | `hooks/useEGOGiftDetailData.ts:6-10` | `egoGiftDetailQueryKeys` - update to add `spec()` and `i18n()` keys |
| Theme pack loading | `hooks/useThemePackListData.ts:50-62` | `useThemePackListData()` - returns `{ themePackList, themePackI18n }` |
| Detail page layout | `components/common/DetailPageLayout.tsx` | `DetailPageLayout` - already imported, no changes |
| Detail page skeleton | `components/common/DetailPageSkeleton.tsx` | `DetailPageSkeleton` - for initial load fallback |

---

## Gap Analysis

**Currently Missing:**
- `useEGOGiftDetailSpec()` - spec-only hook without language dependency
- `useEGOGiftDetailI18n()` - i18n-only hook with language dependency
- `EGOGiftNameI18n.tsx` - wrapper for gift name with suspense
- `AllEnhancementsPanelI18n.tsx` - wrapper for descriptions with suspense
- `EGOGiftMetadataI18n.tsx` - wrapper for theme pack names with suspense

**Needs Modification:**
- `hooks/useEGOGiftDetailData.ts` - Add two new exports, keep existing for backward compat
- `routes/EGOGiftDetailPage.tsx` - Refactor content to use separated hooks + add Suspense boundaries

**Can Reuse:**
- `useThemePackListData()` - Already loads theme pack i18n
- `DetailPageLayout` - Already used, no changes
- `GiftName`, `AllEnhancementsPanel`, `EGOGiftMetadata` - Base components, no changes
- `EGOGiftCard` - Uses spec data only, stays visible

---

## Testing Requirements

### Manual UI Tests (Human Verification)

**Initial Page Load:**
- Navigate to any EGO Gift detail page
- Verify brief skeleton, then full page renders
- Verify all sections present: card, name, metadata, descriptions

**Language Switch - Primary Test (CRITICAL):**
- Open detail page in English, switch to Korean
- **Must remain visible:** Card image, metadata labels
- **Must briefly show empty:** Gift name, theme pack names, descriptions
- **Must NOT happen:** Full page skeleton, layout shift
- Switch back to English - verify same granular behavior

**Language Switch - Rapid Test:**
- Rapidly switch English ↔ Korean ↔ Japanese
- Verify no race conditions (stale text)
- Verify each section updates independently
- Check console for duplicate fetch warnings

**Theme Pack Global Cache (if implemented):**
- Open first gift, wait for theme pack i18n load
- Navigate to different gift
- Verify theme pack names load instantly (cache hit)

---

### Automated Functional Verification

**Spec Data Stability:**
- Spec loads once on page mount
- Spec does NOT re-fetch on language change
- Query key: `['egoGift', id, 'spec']` (no language parameter)

**i18n Data Loading:**
- Gift name/descriptions: `['egoGift', id, 'i18n', language]`
- Theme pack names: `['themePack', 'list', 'i18n', language]`

**Granular Suspense:**
- Name Suspense fallback: `<GiftName name="" />`
- Descriptions Suspense fallback: `<AllEnhancementsPanel descriptions={[]} />`
- Metadata Suspense fallback: `<EGOGiftMetadata themePackNames={{}} />`

**Backward Compatibility:**
- `useEGOGiftDetailData()` still works for `EGOGiftTooltipContent.tsx`
- Tooltip loading unchanged

---

### Unit Tests

**Hook Tests:**
- `useEGOGiftDetailSpec()` returns `EGOGiftData`, suspends on initial load
- `useEGOGiftDetailI18n()` returns `EGOGiftI18n`, suspends on language change
- Query keys match expected format
- No duplicate fetches for same gift

**Component Tests:**
- `EGOGiftNameI18n` renders with i18n name, shows empty during loading
- `AllEnhancementsPanelI18n` renders with i18n descriptions, shows empty array during loading
- `EGOGiftMetadataI18n` renders with theme pack names, shows empty map during loading

---

### Integration Tests

**Data Flow:**
- Detail page loads spec once, i18n updates on language change
- Each wrapper component fetches its own i18n independently
- Theme pack i18n shared across all sections

**Suspense Coordination:**
- Multiple Suspense boundaries resolve independently
- Parent Suspense shows skeleton on initial load
- Child Suspense boundaries show section-specific fallbacks on language change

---

## Technical Constraints

| Constraint | Impact | Mitigation |
|-----------|--------|------------|
| Query key format | New hooks must match Identity pattern | Follow `useIdentityDetailSpec/I18n()` structure |
| Language dependency | Theme pack i18n updates on language change | Already handled by `useThemePackListData()` |
| Suspense requirement | All `useSuspenseQuery` need Suspense ancestor | Each wrapper + route level verify |
| Type assertions | Current code uses `as EGOGiftListItem` | Keep in detail page, not in wrappers |
| Fallback consistency | Fallbacks must match component shape | Use empty values (string, array, object) |
| Error boundaries | Failures should not block other sections | Consider per-section error boundaries |

---

## Data Flow After Implementation

```
EGOGiftDetailPage (Suspense wrapper)
  └─ EGOGiftDetailContent
      ├─ useEGOGiftDetailSpec(id)              // No language, stable
      │   └─ Query: ['egoGift', id, 'spec']
      │
      ├─ Left column
      │   ├─ EGOGiftCard (spec only)           // No Suspense
      │   └─ Suspense → EGOGiftNameI18n        // Suspends on language change
      │       └─ Query: ['egoGift', id, 'i18n', language]
      │
      └─ Right column
          ├─ Suspense → AllEnhancementsPanelI18n    // Suspends on language change
          └─ Suspense → EGOGiftMetadataI18n         // Suspends on language change
              └─ Query: ['themePack', 'list', 'i18n', language]
```

---

## Implementation Summary

**Files to Create (3):**
- `components/egoGift/EGOGiftNameI18n.tsx` (~28 lines)
- `components/egoGift/AllEnhancementsPanelI18n.tsx` (~30 lines)
- `components/egoGift/EGOGiftMetadataI18n.tsx` (~50 lines)

**Files to Modify (2):**
- `hooks/useEGOGiftDetailData.ts` (+35 lines)
- `routes/EGOGiftDetailPage.tsx` (~20 lines changed)

**Total Estimated Changes:** ~163 lines

**Pattern Source:** All patterns exist in Identity detail page - copy-paste + rename task.
