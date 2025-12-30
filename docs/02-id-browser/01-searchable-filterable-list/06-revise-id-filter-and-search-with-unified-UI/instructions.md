# Task: Redesign Identity Filter/Search with Responsive Sidebar Layout

## Description

Redesign the filter/search system for the Identity page (and later EGO/EGOGift pages) with a responsive layout that adapts between desktop and mobile views.

### Desktop Layout (lg+ breakpoint)
- Filter sidebar positioned on the LEFT side (280px width, sticky)
- Main content (search bar + identity list) on the right
- Sidebar contains all filter sections in vertical stack

### Mobile Layout (< lg breakpoint)
- Filters accessible via a toggle button that opens a bottom Sheet drawer
- Search bar remains visible at top of main content
- Sheet contains same filters as desktop sidebar

### Filter Types Required

| Filter | Component Type | Display | Default State |
|--------|----------------|---------|---------------|
| Sinners | IconFilter (existing) | 12 character icons | Expanded |
| Keywords | IconFilter (existing) | 7 status effect icons | Expanded |
| Skill Attributes | IconFilter (new) | 8 icons (NEUTRAL + 7 affinities) | Collapsed |
| Attack Types | IconFilter (new) | 3 icons (SLASH, PENETRATE, HIT) | Collapsed |
| Rank | Toggle buttons (new) | 3 buttons for ranks 1, 2, 3 | Collapsed |
| Season | Dropdown with checkboxes (new) | Localized season names | Collapsed |
| Association | Dropdown with checkboxes (new) | Localized association names | Collapsed |

### Collapsible Sections Behavior
- Each filter group is a collapsible accordion section
- Core filters (Sinner, Keyword) start expanded
- Extra filters (Attributes, Attack Types, Rank, Season, Association) start collapsed
- Collapsed sections show active filter count indicator (e.g., "▶ Season (2)")
- Click section header to toggle expand/collapse

### Filter Logic
- All filter types use AND logic between each other
- Within each filter type, selections use OR logic (identity matches if it has ANY selected value)
- Existing keyword filter behavior: identity must have ALL selected keywords (AND within)

### Filter State
- Reset all filters when leaving the page (no persistence)
- Provide "Reset All" button to clear all filters at once
- Show total active filter count on mobile toggle button

### Reusability
- FilterSidebar component must be generic and reusable for EGO and EGOGift pages
- Each page composes its own filter set by passing children to FilterSidebar
- Filter components are standalone and can be mixed/matched per page

### i18n Requirements
- Season names need localization (add to common.json or new file)
- Association names need localization (add to common.json or new file)

## Research

- Existing IconFilter pattern in `components/common/IconFilter.tsx`
- Existing SinnerFilter/KeywordFilter wrapper patterns
- shadcn Sheet component (need to add via `yarn run shadcn add sheet`)
- shadcn Collapsible component (may need for accordion behavior)
- Current filter state management in IdentityPage.tsx
- DropdownMenu component for Season/Association dropdowns
- Data structure of identitySpecList.json for available values
- i18n file structure for adding season/association translations

## Scope

Files to READ for context:
- `frontend/src/routes/IdentityPage.tsx` - current filter implementation
- `frontend/src/components/common/IconFilter.tsx` - base filter component
- `frontend/src/components/common/SinnerFilter.tsx` - wrapper pattern
- `frontend/src/components/common/KeywordFilter.tsx` - wrapper pattern
- `frontend/src/components/identity/IdentityList.tsx` - current filtering logic
- `frontend/src/types/IdentityTypes.ts` - Identity type definition
- `frontend/src/schemas/IdentitySchemas.ts` - validation schemas
- `frontend/src/lib/constants.ts` - existing constants
- `frontend/src/components/ui/dropdown-menu.tsx` - dropdown pattern
- `static/data/identitySpecList.json` - data structure
- `static/i18n/EN/common.json` - i18n structure

## Target Code Area

Files to CREATE:
- `frontend/src/components/common/FilterSidebar.tsx` - responsive sidebar wrapper
- `frontend/src/components/common/FilterSection.tsx` - collapsible accordion section
- `frontend/src/components/common/SkillAttributeFilter.tsx` - skill attribute icons
- `frontend/src/components/common/AttackTypeFilter.tsx` - attack type icons
- `frontend/src/components/common/RankFilter.tsx` - rank toggle buttons
- `frontend/src/components/common/SeasonDropdown.tsx` - season multi-select
- `frontend/src/components/common/AssociationDropdown.tsx` - association multi-select
- `frontend/src/components/ui/sheet.tsx` - shadcn Sheet component (via CLI)
- `frontend/src/components/ui/collapsible.tsx` - shadcn Collapsible (optional, via CLI)

Files to MODIFY:
- `frontend/src/types/IdentityTypes.ts` - add attributeType, atkType, season, associationList fields
- `frontend/src/hooks/useIdentityListData.ts` - include new fields in data transformation
- `frontend/src/routes/IdentityPage.tsx` - refactor to use FilterSidebar layout
- `frontend/src/components/identity/IdentityList.tsx` - extend filtering logic
- `frontend/src/lib/constants.ts` - add SEASONS, ASSOCIATIONS constants
- `static/i18n/EN/common.json` - add season/association translations
- `static/i18n/KR/common.json` - add season/association translations
- `static/i18n/JP/common.json` - add season/association translations
- `static/i18n/CN/common.json` - add season/association translations

## Testing Guidelines

### Manual UI Testing

**Desktop (viewport >= 1024px):**
1. Navigate to /identity page
2. Verify filter sidebar appears on the LEFT side of the page
3. Verify sidebar is approximately 280px wide
4. Verify Sinner and Keyword sections are expanded by default
5. Verify Skill Attributes, Attack Types, Rank, Season, Association sections are collapsed
6. Click a collapsed section header (e.g., "Skill Attributes")
7. Verify section expands to show filter options
8. Click the header again
9. Verify section collapses
10. Select 2 seasons in the Season dropdown
11. Verify collapsed Season header shows "(2)" indicator
12. Click "Reset All" button
13. Verify all filters are cleared
14. Scroll down the page
15. Verify sidebar remains sticky (stays visible while scrolling)

**Mobile (viewport < 1024px):**
16. Resize browser to mobile width (< 1024px)
17. Verify sidebar is hidden
18. Verify a filter toggle button is visible (with filter count badge)
19. Click the filter toggle button
20. Verify a Sheet drawer slides up from the bottom
21. Verify all filter sections are present in the Sheet
22. Select a sinner filter
23. Verify the filter count badge on toggle button updates
24. Click outside the Sheet or swipe down
25. Verify Sheet closes
26. Verify the sinner filter is still applied to the identity list

**Filter Functionality:**
27. On desktop, select "YiSang" sinner
28. Verify only YiSang identities are shown
29. Select "Sinking" keyword
30. Verify only YiSang identities with Sinking keyword are shown
31. Expand Skill Attributes and select "AZURE"
32. Verify results are further filtered to those with AZURE attribute
33. Expand Attack Types and select "SLASH"
34. Verify results show identities matching all selected filters
35. Expand Rank and select "3"
36. Verify only rank 3 identities matching other filters are shown
37. Open Season dropdown and select "Season 1"
38. Verify only Season 1 identities are shown
39. Open Association dropdown and select "SEVEN"
40. Verify only SEVEN association identities are shown
41. Click Reset All
42. Verify all identities are shown again

### Automated Functional Verification

- [ ] Desktop sidebar visibility: Sidebar visible at lg+ breakpoint, hidden below
- [ ] Mobile sheet trigger: Toggle button visible below lg breakpoint
- [ ] Sinner filter: Filters identities by sinner ID prefix
- [ ] Keyword filter: Filters identities that have ALL selected keywords
- [ ] Attribute filter: Filters identities that have ANY selected attribute
- [ ] Attack type filter: Filters identities that have ANY selected attack type
- [ ] Rank filter: Filters identities by exact rank match
- [ ] Season filter: Filters identities by exact season match
- [ ] Association filter: Filters identities that have ANY selected association
- [ ] Combined filters: All filter types work together with AND logic
- [ ] Reset all: Clears all filter selections
- [ ] Active count badge: Shows correct count of active filters
- [ ] Collapsed indicator: Shows filter count on collapsed sections
- [ ] Section toggle: Click expands/collapses filter sections
- [ ] Sticky sidebar: Sidebar stays fixed while scrolling content

### Edge Cases

- [ ] No results: Shows appropriate empty state when filters match nothing
- [ ] All filters active: Page remains responsive with many filters applied
- [ ] Rapid toggling: No race conditions when quickly toggling filters
- [ ] Page navigation: Filters reset when navigating away and returning
- [ ] Sheet scroll: Sheet content scrolls if filters exceed viewport height
- [ ] Keyboard navigation: Filter options accessible via keyboard
- [ ] Missing data: Gracefully handles identities with missing attributeType/atkType

### Integration Points

- [ ] Identity type: New fields (attributeType, atkType, season, associationList) properly typed
- [ ] Data hook: useIdentityListData includes all new fields in transformation
- [ ] i18n: Season and Association names display in current language
- [ ] Search bar: Search still works alongside filter sidebar
- [ ] Identity cards: Filtered results display correctly in IdentityList

## Reference Sites

- https://baslimbus.info/identity - Left sidebar layout, collapsible sections
- https://limbus.haneuk.info/egogift - Hamburger menu, expandable sections
