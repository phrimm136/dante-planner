# Implementation Plan: EGO Gift Searchable List with Sorting

## Clarifications Needed

No clarifications needed - requirements are clear

## Task Overview

Implement EGO Gift browser page with searchable, filterable, and sortable list by adapting Identity page pattern. Remove sinner filter, add custom sorter component with dual-mode sorting (tier-first or keyword-first), implement three-category search (name, keywords, themePack) with reverse mapping optimization, and handle edge cases for non-filter keywords and empty keyword arrays.

## Steps to Implementation

1. **Create EGO Gift data hook**: Build useEGOGiftData hook to load EGOGiftSpecList.json and EGOGiftNameList.json with dynamic imports, merging data and i18n into unified gift objects with proper TypeScript types

2. **Create EGO Gift types**: Define TypeScript interfaces for EGOGift, EGOGiftSpec, and EGOGiftI18n matching the PascalCase keyword format and string tier type

3. **Create keyword constants**: Add KEYWORD_ORDER array to constants file with PascalCase internal keywords and create mapping to display names

4. **Implement Sorter component**: Build reusable Sorter component in common folder with state for sort mode (tier-first vs keyword-first) and UI to toggle between modes

5. **Implement EGO Gift sorting logic**: Create sort function that handles tier ordering (EX before numbers), keyword categorization (matching filter array or Common), and ID numeric parsing with proper fallback chain

6. **Create EGOGiftList component**: Build list component with filtering logic for keyword filter and search, integrating sorting before rendering, using same AND/OR patterns as Identity

7. **Extend useSearchMappings for EGO Gifts**: Add themePack reverse mapping alongside existing keyword mapping, ensuring PascalCase format compatibility

8. **Create EGOGiftCard component**: Build card component displaying gift name, tier badge, keyword icons, and cost following existing card patterns

9. **Build EGOGiftPage route**: Assemble page with state management for keyword filter, search query, and sort mode, layout with sorter before search bar, removing sinner filter section

10. **Optimize reverse mappings**: Apply useMemo with language dependency for all three reverse maps (name, keywords, themePack) to reduce loading overhead

## Success Criteria

- EGO Gift page displays all gifts from EGOGiftSpecList.json with proper name resolution from i18n
- Sorter component uses toggle buttons with i18n text labels and successfully toggles between tier->keyword->id and keyword->tier->id modes
- Sorter initializes to tier->keyword->id mode (tier button active) on every page load without persistence
- Sorting correctly handles "EX" tier as first priority, descending numeric tiers, keyword categorization using predefined array, and Common fallback for non-filter keywords
- Keyword filter shows only 11 predefined keywords and filters using OR logic (match any selected)
- Search functionality works across name (direct match), keywords (reverse mapped), and themePack (reverse mapped) with OR logic
- Empty state displays helpful message when no gifts match current filters and search criteria
- Gifts with empty keywords array or only non-filter keywords are categorized and sorted as Common
- Page layout matches Identity page structure minus sinner filter section with proper responsive grid
- No TypeScript errors and build succeeds with all type-only imports following verbatimModuleSyntax

## Assumptions Made

- Sorter component uses toggle buttons with i18n text showing "keyword|tier" order labels for clear mode indication
- Sort mode does NOT persist across page reloads - always initializes to tier->keyword->id (tier button active state)
- Empty filter/search results display empty message to user rather than hiding list component entirely
- Keyword filter displays only 11 predefined keywords - non-filter keywords like Haste are searchable via search bar but not filterable
- EGO Gift cards will have similar visual design to Identity/EGO cards but simplified for list view without requiring new assets
- Search optimization using useMemo is sufficient - no need for more complex lazy loading or debouncing beyond existing 100ms SearchBar debounce
- ThemePack reverse mapping will use same keywordMatch.json file structure or we'll need to identify separate mapping source
