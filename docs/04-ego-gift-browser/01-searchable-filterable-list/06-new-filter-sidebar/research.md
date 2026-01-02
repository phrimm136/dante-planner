# Research: EGO Gift Filter Sidebar

## Clarifications Resolved

**Difficulty Data:** Confirmed present in `egoGiftSpecList.json` (verified via grep). Fields `hardOnly` and `extremeOnly` exist but types/schemas need updating.

---

## Spec-to-Code Mapping

| Requirement | Status | Location | Action |
|-------------|--------|----------|--------|
| Keyword Filter | COMPLETE | `EGOGiftKeywordFilter.tsx` | Reuse existing |
| Difficulty Filter | DATA EXISTS | `hardOnly`/`extremeOnly` in spec list | New component + type update |
| Tier Filter | DATA EXISTS | `tag` array with `TIER_1`...`TIER_EX` | New component |
| Theme Pack Filter | DATA EXISTS | `themePack` array + i18n names | New dropdown |
| Attribute Type Filter | DATA EXISTS | `attributeType` field | New icon filter |
| FilterPageLayout | PATTERN EXISTS | `IdentityPage.tsx` | Adapt pattern |

---

## Spec-to-Pattern Mapping

| Requirement | Source Pattern | Notes |
|-------------|----------------|-------|
| Toggle buttons (Difficulty/Tier) | `RankFilter.tsx` | Set-based selection, clear button |
| Theme Pack dropdown | `SeasonDropdown.tsx` | Multi-select checkbox dropdown |
| Attribute Type icons | `CompactEGOTypeFilter.tsx` | Icon grid with CompactIconFilter |
| Page layout | `IdentityPage.tsx` | FilterPageLayout + state management |
| List filtering | `IdentityList.tsx` | OR within type, AND across types |

---

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `DifficultyFilter.tsx` | `RankFilter.tsx` | Toggle layout, Set selection, styling |
| `TierFilter.tsx` | `RankFilter.tsx` | Same pattern, 6 buttons (I-V, EX) |
| `ThemePackDropdown.tsx` | `SeasonDropdown.tsx` | Dropdown structure, checkbox items, i18n |
| `CompactAttributeTypeFilter.tsx` | `CompactEGOTypeFilter.tsx` | Icon filter wrapper |
| `EGOGiftPage.tsx` (modify) | `IdentityPage.tsx` | FilterPageLayout integration |
| `EGOGiftList.tsx` (modify) | `IdentityList.tsx` | Filter props and logic |

---

## Existing Utilities

| Category | Location | Found |
|----------|----------|-------|
| Constants | `lib/constants.ts` | `KEYWORD_ORDER`, `AFFINITIES`, `FILTER_SIDEBAR_WIDTH` |
| Icon paths | `lib/assetPaths.ts` | `getStatusEffectIconPath()`, need `getAttributeTypeIconPath()` |
| Hooks | `hooks/useThemePackListData.ts` | Theme pack data loading exists |
| Components | `components/common/` | `FilterSidebar`, `FilterSection`, `FilterPageLayout`, `CompactIconFilter` |

---

## Gap Analysis

**Missing (create new):**
- `DifficultyFilter.tsx` - 3 toggle buttons
- `TierFilter.tsx` - 6 toggle buttons with Roman numerals
- `ThemePackDropdown.tsx` - multi-select dropdown
- `CompactAttributeTypeFilter.tsx` - icon filter wrapper
- Constants: `EGO_GIFT_TIERS`, `EGO_GIFT_TIER_TAGS`, `EGO_GIFT_DIFFICULTIES`, `EGO_GIFT_ATTRIBUTE_TYPES`
- Asset helper: `getAttributeTypeIconPath()` if icons exist

**Needs modification:**
- `EGOGiftTypes.ts` - add `hardOnly?: boolean`, `extremeOnly?: boolean`
- `EGOGiftSchemas.ts` - add optional difficulty fields
- `EGOGiftPage.tsx` - FilterPageLayout wrapper, filter state, reset handler
- `EGOGiftList.tsx` - add filter props and logic
- `lib/constants.ts` - add 4 new constant arrays

**Can reuse (no changes):**
- `FilterPageLayout`, `FilterSidebar`, `FilterSection`
- `EGOGiftKeywordFilter`, `EGOGiftSearchBar`
- `Sorter`, `ResponsiveCardGrid`, `EGOGiftCardLink`
- `RankFilter` pattern, `SeasonDropdown` pattern

---

## Testing Requirements

### Manual UI Tests
- Desktop: sidebar sticky left, all 5 filter sections visible
- Mobile: expandable header, primary (Keyword/Difficulty) always visible
- Expand button reveals secondary filters (Tier/ThemePack/Attribute)
- Reset All clears all filters and search
- Filters work in combination with search and sorter

### Automated Tests
- Difficulty: Normal (no flags), Hard (`hardOnly`), Extreme (`extremeOnly`)
- Tier: I→TIER_1 through EX→TIER_EX mapping
- Theme Pack: OR logic, empty array visible when no filter
- Cross-filter: AND logic across filter types
- Reset: clears all selections

---

## Technical Constraints

- Types/schemas MUST be updated before Difficulty filter works
- Tier extracted from `tag` array at runtime (no dedicated field)
- Theme Pack uses numeric IDs mapped to i18n names
- Mobile breakpoint: `lg:` (1024px) consistent with FilterSidebar
- Set-based selection for performance
- All hooks must use `useSuspenseQuery`

---

## Implementation Order

1. Add constants to `lib/constants.ts`
2. Update `EGOGiftTypes.ts` and `EGOGiftSchemas.ts`
3. Create 4 filter components (Difficulty, Tier, ThemePack, AttributeType)
4. Update `EGOGiftPage.tsx` with FilterPageLayout
5. Update `EGOGiftList.tsx` with filter logic
6. Manual test desktop/mobile
7. Automated test filter logic
