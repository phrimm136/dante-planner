# Implementation Plan: Keyword Filter

## Task Overview

Replace the IdentityKeywordFilter placeholder component with a fully functional filter that allows users to filter identities by status effect keywords (burn, bleed, tremor, rupture, sinking, poise, charge). The implementation will follow the established IdentitySinnerFilter pattern using Set-based state management, icon-based selection UI, and integration with the existing filter system. This completes the filtering capabilities for the identity browser alongside the sinner filter.

## Steps to Implementation

1. **Add status effect icon utility function**: Create getStatusEffectIconPath function in identityUtils.ts to generate icon paths from keyword names, following the same pattern as getSinnerIconPath for consistency across the codebase.

2. **Implement IdentityKeywordFilter component**: Replace placeholder with working component that accepts selectedKeywords Set and onSelectionChange callback props, defines STATUS_EFFECTS constant array with seven keywords, and implements toggle and clear all functionality matching sinner filter pattern.

3. **Build keyword filter UI**: Create compact h-14 container with horizontal scrollable layout containing w-8 h-8 icon buttons for each status effect, using same visual feedback styling as sinner filter with selected and unselected states.

4. **Add keyword state management to IdentityPage**: Create selectedKeywords state using useState with empty Set, pass state and setter to IdentityKeywordFilter component, and pass selectedKeywords to IdentityList for filtering logic.

5. **Extend IdentityList filtering logic**: Update filter logic to handle both sinner and keyword filters simultaneously, using AND logic between filter types and OR logic within keyword selection so identities match if they have any selected keyword.

6. **Handle bracket notation parsing**: Ensure keyword values from identity data are properly parsed to remove brackets before comparison with filter selection, reusing parseSinnerName utility for consistency.

7. **Test filter interactions**: Verify empty selection shows all identities, single keyword selection filters correctly, multiple keyword selection uses OR logic, and combining sinner and keyword filters uses AND logic between the two filter types.

8. **Verify visual consistency**: Ensure keyword filter matches sinner filter height, spacing, icon size, border styling, and color usage including bg-button for icon visibility in light mode.

9. **Type check and validate**: Run TypeScript compiler to verify all type definitions are correct, props interfaces match expected patterns, and no type errors exist in modified components.

10. **Update documentation**: Document the keyword filter implementation including filter combination logic, icon path generation, and any decisions made about empty state behavior or filter precedence.

## Success Criteria

- IdentityKeywordFilter component displays all seven status effect icons in horizontal scrollable layout with h-14 height
- Clicking status effect icons toggles selection state with visual feedback matching sinner filter styling
- Clear all button resets keyword selection to empty Set showing all identities
- Selecting one or more keywords filters identity list to show only identities with any selected keyword
- Combining sinner and keyword filters shows identities matching selected sinner AND having any selected keyword
- Empty keyword selection shows all identities following established filter UX pattern
- TypeScript compilation passes with no errors and all components properly typed
- Filter state properly lifts to IdentityPage and flows down to both filter and list components
- Status effect icons load correctly from static/images/statusEffect/ directory
- Component maintains visual consistency with sinner filter using same button color border radius and spacing

