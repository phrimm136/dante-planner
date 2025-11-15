# Code Documentation: Multi-Purpose Search Bar Implementation

## What Was Done

- Created useSearchMappings custom hook to load language-specific keyword and trait mappings from JSON files
- Built reverse mapping logic converting natural language to bracketed notation using Map with string array values
- Implemented IdentitySearchBar component with controlled input and 500ms debounce delay using useEffect
- Updated search bar UI to match filter components with h-14 height, p-2 padding, and matching border styles
- Added magnifier icon to search bar using inline SVG positioned before the input field
- Added searchQuery state to IdentityPage and wired to IdentitySearchBar and IdentityList via props
- Extended IdentityList filter logic to support search matching across name, keywords, and traits with partial matching
- Search uses OR logic within categories (name OR keyword OR trait) and AND logic with existing filters
- Implemented case-insensitive partial matching with toLowerCase and whitespace trimming via trim()

## Files Changed

### Created Files

- frontend/src/hooks/useSearchMappings.ts

### Modified Files

- frontend/src/routes/IdentityPage.tsx
- frontend/src/components/identity/IdentitySearchBar.tsx
- frontend/src/components/identity/IdentityList.tsx

## What Was Skipped

- No steps were skipped from the implementation plan
- All ten planned steps completed successfully
- Placeholder text uses existing i18n translation key

## Testing Results

- TypeScript compilation: PASSED with no errors using yarn tsc --noEmit
- All type definitions validated including IdentitySearchBarProps and updated IdentityListProps
- Debounce constant DEBOUNCE_DELAY set to 500ms as specified
- Search state properly typed and wired through component tree

## Issues & Resolutions

### Issue 1: Reverse Mapping Data Structure
**Problem**: Needed to support multiple bracketed values mapping to the same natural language term for future extensibility.

**Resolution**: Used Map<string, string[]> for reverse mappings allowing array of bracketed values per natural language key, iterating with nested some() checks in filter logic.

### Issue 2: Search Matching Logic
**Problem**: Needed to handle partial matching on natural language then lookup bracketed values in identity data.

**Resolution**: Implemented two-step matching: first check if query is substring of natural language key, then check if any corresponding bracketed value exists in identity keywords or traits array using nested some() calls.

### Issue 3: Filter Combination Logic
**Problem**: Required AND logic between search and existing filters while maintaining OR logic within search categories.

**Resolution**: Extended existing filter chain in IdentityList to add search filter block after sinner and keyword filters, using early return pattern for AND logic between filter types.

### Issue 4: Language-Specific Data Loading
**Problem**: Mapping files only exist for EN language currently, other languages would cause import errors.

**Resolution**: Conditional loading in useSearchMappings checking i18n.language and only loading EN mappings when language is EN, falling back to empty objects for other languages.

### Issue 5: Debounce Implementation
**Problem**: Needed live search without triggering on every keystroke to avoid performance issues.

**Resolution**: Used two-state pattern with inputValue for immediate UI updates and debounced searchQuery update via useEffect with setTimeout cleanup, configurable via DEBOUNCE_DELAY constant set to 500ms.

### Issue 6: Search Bar Visual Consistency
**Problem**: Search bar needed to match existing filter components visual design and height.

**Resolution**: Updated search bar to use h-14 height, p-2 padding, gap-2 spacing matching IconFilter component exactly, added magnifier SVG icon with 20x20 size in muted-foreground color.

### Issue 7: Trait Bracket Notation
**Problem**: Trait mapping JSON keys already include brackets unlike keyword mappings.

**Resolution**: Removed bracket wrapping for trait values in reverse mapping construction, using bracketedKey directly instead of wrapping with `[${bracketedKey}]`.
