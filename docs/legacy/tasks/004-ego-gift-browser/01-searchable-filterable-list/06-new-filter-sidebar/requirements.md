# Task: EGO Gift Filter Sidebar

## Description

Add a responsive filter sidebar to the EGO Gift browser page, following the established `FilterPageLayout` + `FilterSidebar` pattern used by Identity and EGO pages.

### Filter Criteria

1. **Keyword Filter** (existing)
   - Icon-based toggle filter for gift keywords (Combustion, Laceration, etc.)
   - Includes "None" option for gifts without keywords
   - Already implemented as `EGOGiftKeywordFilter`

2. **Difficulty Filter** (new)
   - Three toggle buttons: Normal, Hard, Extreme
   - Logic derived from data fields:
     - Neither `hardOnly` nor `extremeOnly` → Normal
     - `hardOnly: true` → Hard
     - `extremeOnly: true` → Extreme
   - Multiple selections allowed (OR logic)

3. **Tier Filter** (new)
   - Six toggle buttons with Roman numerals: I, II, III, IV, V, EX
   - Maps to data: `TIER_1` through `TIER_5`, `TIER_EX` in `tag` array
   - Multiple selections allowed (OR logic)

4. **Theme Pack Filter** (new)
   - Multi-select dropdown menu
   - Shows theme pack names from i18n (e.g., "The Forgotten", "The Outcast")
   - OR logic: gift matches if it belongs to ANY selected theme pack
   - Gifts with empty `themePack` array visible when no filter applied

5. **Attribute Type Filter** (new, secondary)
   - Icon-based toggle filter: CRIMSON, AMBER, SCARLET
   - Lower priority filter, placed in secondary (expandable) section
   - Included for completeness despite limited gameplay relevance

### Mobile/Desktop Behavior

- **Desktop (lg+)**: Sticky sidebar on left, fixed width matching other pages
- **Mobile (<lg)**: Expandable filter header at top
  - **Primary filters** (always visible): Keyword, Difficulty
  - **Secondary filters** (expandable): Tier, Theme Pack, Attribute Type
  - Expand/collapse button centered on bottom border

### Filter Behavior

- All filters use OR logic within filter type (select any matching)
- Across filter types, use AND logic (must match all active filter criteria)
- Empty selection = no filter (show all)
- "Reset All" button clears all filters and search query
- Active filter count displayed on Reset button

## Research

- **Pattern source**: `frontend/src/routes/IdentityPage.tsx` - full FilterPageLayout integration
- **Sidebar template**: `frontend/src/components/common/FilterSidebar.tsx` - responsive wrapper
- **Toggle button pattern**: `frontend/src/components/common/RankFilter.tsx` - for Tier/Difficulty
- **Dropdown pattern**: `frontend/src/components/common/SeasonDropdown.tsx` - for ThemePack
- **Icon filter pattern**: `frontend/src/components/common/CompactEGOTypeFilter.tsx` - for AttributeType
- **Theme pack data**: `static/data/themePackList.json` (IDs), `static/i18n/EN/themePack.json` (names)
- **Hook for theme packs**: `frontend/src/hooks/useThemePackListData.ts`

## Scope

Files to READ for context:
- `frontend/src/routes/IdentityPage.tsx` - FilterPageLayout usage pattern
- `frontend/src/routes/EGOPage.tsx` - Another filter page example
- `frontend/src/components/common/FilterSidebar.tsx` - Responsive sidebar wrapper
- `frontend/src/components/common/FilterPageLayout.tsx` - Layout component
- `frontend/src/components/common/FilterSection.tsx` - Section with title/count
- `frontend/src/components/common/RankFilter.tsx` - Toggle button pattern
- `frontend/src/components/common/SeasonDropdown.tsx` - Multi-select dropdown pattern
- `frontend/src/components/common/CompactEGOTypeFilter.tsx` - Icon filter pattern
- `frontend/src/components/egoGift/EGOGiftList.tsx` - Current list filtering logic
- `frontend/src/hooks/useEGOGiftListData.ts` - Data loading
- `frontend/src/types/EGOGiftTypes.ts` - Current types
- `frontend/src/schemas/EGOGiftSchemas.ts` - Current schemas
- `frontend/src/lib/constants.ts` - Existing constants

## Target Code Area

Files to CREATE:
- `frontend/src/components/common/DifficultyFilter.tsx` - 3 toggle buttons
- `frontend/src/components/common/TierFilter.tsx` - 6 toggle buttons (I-V, EX)
- `frontend/src/components/common/ThemePackDropdown.tsx` - Multi-select dropdown
- `frontend/src/components/common/CompactAttributeTypeFilter.tsx` - CRIMSON/AMBER/SCARLET icons

Files to MODIFY:
- `frontend/src/types/EGOGiftTypes.ts` - Add `hardOnly?: boolean`, `extremeOnly?: boolean`
- `frontend/src/schemas/EGOGiftSchemas.ts` - Add optional difficulty fields
- `frontend/src/lib/constants.ts` - Add tier/difficulty/attribute constants
- `frontend/src/routes/EGOGiftPage.tsx` - Integrate FilterPageLayout, add filter state
- `frontend/src/components/egoGift/EGOGiftList.tsx` - Add filter props and logic

## Testing Guidelines

### Manual UI Testing

**Desktop Testing:**
1. Navigate to EGO Gift page (`/ego-gift`)
2. Verify filter sidebar appears on left side (sticky)
3. Verify all filter sections visible: Keyword, Difficulty, Tier, Theme Pack, Attribute Type
4. Click "Hard" in Difficulty filter
5. Verify only gifts with `hardOnly: true` are shown
6. Click "Extreme" in Difficulty filter (both Hard and Extreme selected)
7. Verify gifts matching either difficulty are shown (OR logic)
8. Click Tier "III" button
9. Verify only Tier 3 gifts from Hard/Extreme difficulty are shown (AND across filter types)
10. Open Theme Pack dropdown, select "The Outcast"
11. Verify gifts further filtered to those in The Outcast theme pack
12. Click "Reset All" button
13. Verify all filters cleared and all gifts shown
14. Verify search bar works in combination with filters

**Mobile Testing:**
1. Resize browser to mobile width (< 1024px)
2. Verify sidebar transforms to expandable header
3. Verify Keyword and Difficulty filters visible (primary)
4. Verify Tier, Theme Pack, Attribute Type are hidden
5. Click expand button (chevron at bottom)
6. Verify secondary filters appear
7. Apply filters and verify they work
8. Click collapse button
9. Verify secondary filters hidden but remain active

### Automated Functional Verification

- [ ] Difficulty filter: Normal shows gifts with no difficulty flags
- [ ] Difficulty filter: Hard shows gifts with `hardOnly: true`
- [ ] Difficulty filter: Extreme shows gifts with `extremeOnly: true`
- [ ] Difficulty filter: Multiple selections use OR logic
- [ ] Tier filter: Each tier button filters correctly (I→TIER_1, etc.)
- [ ] Tier filter: EX button filters TIER_EX gifts
- [ ] Tier filter: Multiple selections use OR logic
- [ ] Theme pack dropdown: Shows all theme packs from i18n
- [ ] Theme pack dropdown: Filters by ANY selected pack (OR logic)
- [ ] Theme pack dropdown: Gifts with empty themePack shown when no filter
- [ ] Attribute type filter: CRIMSON/AMBER/SCARLET filter correctly
- [ ] Cross-filter: AND logic applies across different filter types
- [ ] Active count: Shows correct count on Reset button
- [ ] Reset All: Clears all filters including search

### Edge Cases

- [ ] Empty results: Shows appropriate message when no gifts match filters
- [ ] All filters selected: Shows all gifts (equivalent to no filter)
- [ ] Gifts without theme pack: Visible when theme pack filter not applied
- [ ] Page navigation: Filters reset on unmount (prevent stale state)
- [ ] Rapid filter changes: UI remains responsive
- [ ] Theme pack with special characters in name: Displays correctly

### Integration Points

- [ ] FilterPageLayout: Follows same pattern as IdentityPage
- [ ] FilterSidebar: Uses same responsive breakpoint (lg)
- [ ] Search bar: Works in combination with all filters
- [ ] Sorter: Sort mode preserved when filters applied
- [ ] EGOGiftList: Receives all filter props and applies correctly
- [ ] i18n: Filter labels use translation keys where applicable

## Implementation Notes

### Difficulty Derivation Logic
```
function getDifficulty(gift: EGOGiftSpec): 'normal' | 'hard' | 'extreme' {
  if (gift.extremeOnly) return 'extreme'
  if (gift.hardOnly) return 'hard'
  return 'normal'
}
```

### Tier Extraction Logic
```
function extractTier(tags: string[]): string {
  const tierTag = tags.find(t => t.startsWith('TIER_'))
  return tierTag?.replace('TIER_', '') || 'UNKNOWN'
}
```

### Constants to Add
```typescript
export const EGO_GIFT_TIERS = ['I', 'II', 'III', 'IV', 'V', 'EX'] as const
export const EGO_GIFT_TIER_TAGS = ['TIER_1', 'TIER_2', 'TIER_3', 'TIER_4', 'TIER_5', 'TIER_EX'] as const
export const EGO_GIFT_DIFFICULTIES = ['normal', 'hard', 'extreme'] as const
export const EGO_GIFT_ATTRIBUTE_TYPES = ['CRIMSON', 'AMBER', 'SCARLET'] as const
```
