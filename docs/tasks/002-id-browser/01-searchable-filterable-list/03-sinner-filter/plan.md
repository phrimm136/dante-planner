# Implementation Plan: Sinner Filter

## Task Overview

Replace the IdentityCharacterFilter placeholder with a functional IdentitySinnerFilter component that allows users to filter identity cards by selecting one or more sinners. The filter will display all twelve sinner icons as clickable buttons, track selected sinners in state, and pass the filter criteria to IdentityList to show only matching identities. When no sinners are selected, all identities will be displayed.

## Steps to Implementation

1. **Rename component file and exports**: Change IdentityCharacterFilter to IdentitySinnerFilter in filename, component definition, and all imports in IdentityPage.

2. **Create sinner list constant**: Define array of all twelve sinner names in camelCase matching the available icon filenames to serve as the filter options.

3. **Add filter state management**: Implement local state using useState to track the set of currently selected sinner names, defaulting to empty set showing all identities.

4. **Build sinner button grid UI**: Create horizontal scrollable container displaying all twelve sinner icons as toggle buttons with active/inactive visual states.

5. **Implement toggle selection logic**: Add click handlers to buttons that add or remove sinners from selected set, with visual feedback for active selections.

6. **Pass filter state to IdentityList**: Lift selected sinners state up or create shared context to communicate filter criteria from IdentitySinnerFilter to IdentityList component.

7. **Add filtering logic to IdentityList**: Filter the identities array before rendering by matching identity.sinner field against selected sinners set using parseSinnerName for comparison.

8. **Handle empty selection state**: Ensure that when selectedSinners is empty, all identities are shown rather than none, implementing the "show all when nothing selected" behavior.

9. **Add clear all functionality**: Include button or mechanism to deselect all sinners at once for quick reset to showing all identities.

10. **Update translation keys**: Modify i18n common.json files to use appropriate key for the renamed sinner filter component across all languages.

## Success Criteria

- IdentityCharacterFilter renamed to IdentitySinnerFilter throughout codebase with no broken imports
- All twelve sinner icons displayed as clickable buttons in horizontal layout matching filter component height
- Clicking sinner button toggles selection state with clear visual indication of active selections
- Identity list dynamically filters to show only identities matching selected sinners using bracket-notated sinner field
- Empty selection state displays all identities rather than hiding everything
- Multiple sinners can be selected simultaneously showing union of their identities
- Filter component maintains h-20 height and visual consistency with other filter placeholders
- Sinner name parsing correctly matches bracketed values from JSON to camelCase icon names
