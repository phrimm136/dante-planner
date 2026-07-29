# Implementation Plan: Apply Value Map and Link

## Clarifications Needed

No clarifications needed - requirements are clear

## Task Overview

Apply keyword icon-only display positioned in lower-right of EGO Gift cards with tooltips showing user-friendly display names from keywordMatch.json mapping. Wrap each gift card in TanStack Router Link component to enable navigation to detail page. Create shared utility function for keyword-to-display-name mapping and refactor existing mapping functions. Remove bracketed keyword entries from keywordMatch.json. Implement text fallback for missing keyword icons.

## Steps to Implementation

1. **Remove bracketed keywords from keywordMatch.json**: Delete entries with bracketed format like [combustion] while keeping PascalCase entries like Combustion
2. **Create shared keyword mapping utility function**: Add reusable function to map PascalCase keywords to display names from keywordMatch.json and refactor existing mapping functions to use it
3. **Wrap EGOGiftCard in Link component**: Import Link from TanStack Router and wrap entire card with route path /ego-gift/$id and params with gift id
4. **Add keyword icon display in lower-right**: Position keyword icons in lower-right corner of card using getStatusEffectIconPath showing icons only without text
5. **Add tooltips with display names**: Implement title attribute on keyword icons showing user-friendly display names mapped from keywordMatch.json
6. **Implement text fallback for missing icons**: Handle missing icon files gracefully by displaying keyword display name as text when icon fails to load
7. **Update card styling for Link**: Adjust CSS classes to preserve hover effects transitions and position keyword icons in lower-right corner
8. **Update EGOGiftKeywordFilter tooltips**: Modify IconFilter to show display names instead of raw PascalCase keywords in tooltips
9. **Build and test**: Run build to verify no TypeScript errors and test navigation keyword icons tooltips and text fallback manually

## Success Criteria

- Bracketed keyword entries removed from keywordMatch.json leaving only PascalCase entries for mapping
- Shared utility function created for keyword-to-display-name mapping and existing mapping functions refactored to use it
- EGOGiftCard displays keyword icons only in lower-right corner using getStatusEffectIconPath without any text labels
- Keyword icons have tooltips showing user-friendly display names like burn bleed tremor from keywordMatch.json mapping
- Missing keyword icons display text fallback showing display name instead of broken image
- Clicking any EGO Gift card navigates to detail page at route /ego-gift/{id} with correct id parameter
- Card hover effects and visual styling preserved when wrapped in Link component
- EGOGiftKeywordFilter tooltips show display names instead of raw PascalCase keywords for user-friendly experience
- Build completes successfully without TypeScript errors
- Manual testing confirms cards are clickable keyword icons positioned correctly tooltips work and text fallback functions

## Assumptions Made

- Entire card is clickable link following pattern from IdentityCard and EGOCard components as confirmed by user
- Keyword icons display only in lower-right corner without text labels showing icon-only presentation as confirmed by user
- Tooltips show user-friendly display names from keywordMatch.json values like burn bleed for better user experience as confirmed by user
- Text fallback displays when icon fails to load showing display name instead of hiding or showing placeholder as confirmed by user
- Shared utility function should be created and existing mapping functions refactored for code reusability as confirmed by user
- Removing bracketed entries is safe because they are only used for text parsing not for filtering or display
- Existing card styling classes can be moved to Link component without breaking responsive or hover behavior
- Lower-right positioning uses absolute positioning within relatively positioned card container
- No loading state needed for keyword mapping since keywordMatch.json is static English translation file
