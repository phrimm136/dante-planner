# Research: EGO Gift Searchable List with Sorting

## Overview of Codebase

### EGO Gift Data Structure
- EGOGiftSpecList.json contains: id (string key), keywords array (PascalCase strings), themePack array, cost number, tier string
- Currently three entries: ID "0" (Hellterfly's Dream), "1" (Fellterfly's Dream), "2" (Cellterfly's Dream)
- Keywords use PascalCase format without brackets: "Combustion", "Pierce", etc (NOT "[combustion]")
- Keywords can be empty array - items with no matches should be treated as "Common" category for sorting
- Tier is string type with values "EX", "1", "2", "3", "4", "5" - NOT numeric
- Tier ordering is "EX" first, then descending "5" to "1" - must handle "EX" as special case in string comparison
- ThemePack field exists for grouping gifts by theme or event

### Identity Page UI Pattern
- IdentityPage uses two-row layout: filters/search on top, list grid on bottom
- State management with useState for selectedSinners Set, selectedKeywords Set, searchQuery string
- Filter row uses flex layout with filters section on left and search bar on right with shrink-0
- IdentitySinnerFilter and IdentityKeywordFilter wrap IconFilter common component
- IdentitySearchBar wraps SearchBar common component with 100ms debounce
- IdentityList receives all state props and handles filtering logic internally
- Grid layout with gap-4 and responsive columns for card display

### Filtering and Search Logic
- Filter algorithm uses three-stage AND logic: sinner filter AND keyword filter AND search filter
- Sinner filter uses OR logic within selection - match any selected sinner
- Keyword filter uses AND logic within selection - must have all selected keywords
- Search filter uses OR logic across categories - match name OR keywords OR themePack
- Search is case-insensitive with toLowerCase comparison and includes partial matching
- All filtering happens in List component using Array.filter with early returns for performance
- Empty selections are skipped in filter logic to avoid unnecessary checks

### Reverse Mapping Pattern
- useSearchMappings hook creates reverse Map from natural language to internal PascalCase keywords
- Loads keywordMatch.json which maps internal keywords to display names (Combustion to burn, Laceration to bleed)
- useMemo ensures recalculation only on language change for performance optimization
- Map structure provides O(1) lookup time during search filtering
- Reverse mapping builds Map with lowercase natural language keys and PascalCase value arrays
- Search checks if natural language query matches Map key then verifies item has PascalCase keyword
- Pattern enables searching "burn" to find items with "Combustion" keyword
- For EGO Gifts, keywords are already in PascalCase format so no bracket removal needed

### Common Reusable Components
- IconFilter component in common folder provides base filter UI with icon grid and selection state
- SearchBar component in common folder provides debounced input with 100ms delay and trim
- Both components are framework-agnostic and accept props for customization
- useSearchMappings hook is shared across Identity, EGO, and will be used for EGO Gifts

## Codebase Structure

### Data Location
- Static data lives in /static/data/ with EGOGiftSpecList.json for spec data
- Internationalization files in /static/i18n/{lang}/ with EGOGiftNameList.json for names
- Detail descriptions in /static/i18n/{lang}/gift/{id}.json with name, descs array, obtain fields
- keywordMatch.json in i18n folder provides bracket to natural language mappings

### Component Organization
- Route components in /frontend/src/routes/ like IdentityPage.tsx and EGOPage.tsx
- Feature components in /frontend/src/components/{feature}/ like identity/ and ego/ folders
- Common components in /frontend/src/components/common/ for reusable UI like IconFilter and SearchBar
- Hooks in /frontend/src/hooks/ for data loading and utility functions like useSearchMappings
- Type definitions should follow existing pattern of creating EGOGiftTypes.ts in types folder

### Import Patterns
- Asset paths imported from @/lib/assetPaths for image and icon paths
- Constants imported from @/lib/constants for shared values like SINS array
- Type imports use type-only syntax with import type for verbatimModuleSyntax compliance
- Dynamic imports for data files use await import with @static alias for static folder

## Gotchas and Pitfalls

### Sorting Implementation
- Task requires custom sorter component placed before search bar in UI
- Sorting mechanism needs two modes: tier-keyword-id or keyword-tier-id ordering
- Internal keyword order (PascalCase): Combustion, Laceration, Vibration, Rupture, Sinking, Poise, Charge, Slash, Pierce, Blunt, Common
- External keyword order (display names): burn, bleed, tremor, rupture, sinking, poise, charge, slash, pierce, blunt, common
- Sorting function must find keywords in item's keywords array that match the predefined order array
- Keywords not in predefined array (like "Haste") should be ignored - treat gift as "Common" category
- Items with empty keywords array should be treated as "Common" category
- Items with keywords array containing only non-filter keywords (not in predefined list) also treated as "Common"
- Only keywords present in both the gift's keywords array AND the predefined order array determine category
- Tier order is "EX" first, then descending "5" to "1" - tier is string type not numeric
- Must handle "EX" as special case in comparison (not alphabetical, placed before all numbers)
- ID ordering is incremental numeric - parse string IDs to numbers for proper numerical sorting
- No existing sorter component in codebase - must create new reusable Sorter component
- Multi-criteria sorting requires careful comparison with fallback to next criteria when values are equal

### Reverse Mapping Performance
- Task specifically asks about handling three reverse mapping objects to reduce loading time
- Current pattern uses useMemo with language dependency to avoid recalculation on every render
- Building Map structures instead of objects provides faster O(1) lookups during search
- Must create separate reverse maps for name, keywords, and themePack search categories
- Consider loading mapping data lazily only when search is actually used to defer cost
- String comparison overhead can be reduced by building lowercase index once upfront

### Search Category Handling
- Three search categories required: name (NOT converted), keywords (converted), themePack (converted)
- Name search must use raw display name without any conversion - direct string matching
- Keywords search requires reverse mapping from natural language (burn) to PascalCase internal format (Combustion)
- ThemePack search also requires reverse mapping similar to keywords for user-friendly search
- Must check all three categories with OR logic - match any category to include item in results
- Keyword data is already in PascalCase format without brackets - simplifies comparison logic
- Order of keywords in predefined array matters for sorting - must preserve exact sequence for sort index lookup

### Filter Removal
- Task explicitly requires removing sinner filter from Identity page pattern
- Cannot simply copy all filters - must exclude IdentitySinnerFilter component
- Layout may need adjustment since only keyword filter and sorter remain before search
- Filter row spacing and alignment should be reviewed after removing sinner filter
