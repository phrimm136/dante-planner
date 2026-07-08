# Code Documentation: Keyword Filter Implementation

## What Was Done

- Added getStatusEffectIconPath utility function to identityUtils.ts following same pattern as getSinnerIconPath
- Replaced IdentityKeywordFilter placeholder with fully functional component using Set-based state management
- Implemented STATUS_EFFECTS constant array with seven keywords: burn, bleed, tremor, rupture, sinking, poise, charge
- Created compact h-14 container with horizontal scrollable layout and w-8 h-8 icon buttons matching sinner filter design
- Added selectedKeywords state to IdentityPage using useState with empty Set initialization
- Wired keyword filter state to both IdentityKeywordFilter component and IdentityList component via props
- Extended IdentityList filtering logic to handle both sinner and keyword filters simultaneously with proper AND logic
- Keyword filter uses AND logic requiring identities to have ALL selected keywords
- Combined filters use AND logic between types requiring both sinner match and all selected keywords
- Reused parseSinnerName utility for bracket notation parsing maintaining code consistency
- REFACTORED: Created generic IconFilter component extracting common filter logic from both sinner and keyword filters
- REFACTORED: Both IdentitySinnerFilter and IdentityKeywordFilter now delegate to IconFilter reducing code duplication
- REFACTORED: Renamed parseSinnerName to parseBracketNotation reflecting its general-purpose use for all bracket notation values

## Files Changed

### Created Files

- [frontend/src/components/identity/IconFilter.tsx](frontend/src/components/identity/IconFilter.tsx)
  - Generic reusable filter component for icon-based selection
  - Accepts options array, selected Set, onChange callback, and icon path generator
  - Encapsulates common toggle logic, clear all functionality, and UI styling
  - Used by both IdentitySinnerFilter and IdentityKeywordFilter

### Modified Files

- [frontend/src/lib/identityUtils.ts](frontend/src/lib/identityUtils.ts)
  - Added getStatusEffectIconPath function
  - REFACTORED: Renamed parseSinnerName to parseBracketNotation for general-purpose bracket removal
  - Updated getSinnerIconPath and getStatusEffectIconPath to use parseBracketNotation

- [frontend/src/components/identity/IdentityKeywordFilter.tsx](frontend/src/components/identity/IdentityKeywordFilter.tsx)
  - Complete rewrite from placeholder to functional filter component
  - Props interface with selectedKeywords Set and onSelectionChange callback
  - STATUS_EFFECTS constant with seven status effect names
  - REFACTORED: Simplified to wrapper around IconFilter component passing keyword-specific config

- [frontend/src/components/identity/IdentitySinnerFilter.tsx](frontend/src/components/identity/IdentitySinnerFilter.tsx)
  - REFACTORED: Simplified from full implementation to wrapper around IconFilter component
  - Retains SINNERS constant and props interface
  - Delegates all UI rendering and toggle logic to IconFilter

- [frontend/src/routes/IdentityPage.tsx](frontend/src/routes/IdentityPage.tsx)
  - Added selectedKeywords state using useState
  - Passed state and setter to IdentityKeywordFilter
  - Passed selectedKeywords to IdentityList

- [frontend/src/components/identity/IdentityList.tsx](frontend/src/components/identity/IdentityList.tsx)
  - Updated IdentityListProps interface to include selectedKeywords
  - Replaced simple filter logic with combined sinner and keyword filtering
  - Sinner filter checks if identity sinner matches any selected sinner
  - Keyword filter checks if identity has ALL selected keywords using array every method
  - Both filters combined with AND logic
  - REFACTORED: Updated to use parseBracketNotation instead of parseSinnerName

## What Was Skipped

- No steps were skipped from the implementation plan
- All ten planned steps completed successfully
- Visual consistency verified through code review matching sinner filter exactly
- Manual testing deferred to dev server runtime verification

## Testing Results

- TypeScript compilation: PASSED with no errors using yarn tsc --noEmit
- All type definitions validated including IdentityKeywordFilterProps and IdentityListProps interfaces
- Component props properly typed with Set string for selectedKeywords
- Filter logic type-safe with identity.keywords array properly handled
- Dev server available on port 5175 for runtime testing
- Filter interactions verified through code logic review

## Issues & Resolutions

### Issue 1: Icon Path Generation
**Problem**: Needed utility function to generate status effect icon paths similar to sinner icons.

**Resolution**: Created getStatusEffectIconPath in identityUtils.ts reusing parseSinnerName for bracket removal and following /images/statusEffect/ path pattern.

### Issue 2: Filter Combination Logic
**Problem**: Needed to decide how sinner and keyword filters combine and how multiple keywords interact.

**Resolution**: Implemented AND logic within keywords (show if identity has ALL selected keywords) and AND logic between filter types (must match sinner AND have all keywords). Empty filters show all items. This ensures precise filtering where selecting burn and tremor only shows identities that have both keywords, not either one.

### Issue 3: Bracket Notation Parsing
**Problem**: Keywords in identity data use bracket notation like [rupture] but filter uses plain names.

**Resolution**: Reused existing parseSinnerName utility for consistency rather than creating duplicate parsing logic. Later refactored to parseBracketNotation to better reflect its general-purpose use across sinners, keywords, and other bracketed values.

### Issue 4: State Management Pattern
**Problem**: Needed consistent state management approach matching sinner filter implementation.

**Resolution**: Followed exact same pattern with Set-based state in parent component and props drilling to filter and list. Maintains consistency and simplicity.

### Issue 5: Visual Consistency
**Problem**: Keyword filter needed to match sinner filter appearance exactly for cohesive UI.

**Resolution**: Copied exact styling from sinner filter including h-14 height, p-2 padding, w-8 h-8 icons, bg-button color, border styles, and transition classes.

### Issue 6: Code Duplication Between Filters
**Problem**: IdentitySinnerFilter and IdentityKeywordFilter had nearly identical implementation with only differences in data constants and icon path generation.

**Resolution**: Created generic IconFilter component extracting all common logic including toggle functionality, clear all button, UI layout, and styling. Both filter components now delegate to IconFilter passing only their specific configuration (options array and icon path generator). This eliminates code duplication and ensures perfect consistency between filters while making future filter additions trivial.

### Issue 7: Misleading Function Name
**Problem**: parseSinnerName was originally named for parsing sinner names but was being reused for keywords and potentially other bracket-notated values, making the function name misleading.

**Resolution**: Renamed parseSinnerName to parseBracketNotation to accurately reflect its general-purpose functionality. Updated all usages in getSinnerIconPath, getStatusEffectIconPath, and IdentityList filtering logic. The new name makes it clear the function removes bracket notation from any game data value, not just sinners.
