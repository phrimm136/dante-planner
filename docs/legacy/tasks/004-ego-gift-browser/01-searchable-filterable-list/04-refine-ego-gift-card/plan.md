# Implementation Plan: Refine EGO Gift Card

## Clarifications Needed

No clarifications needed - requirements are clear

## Task Overview

Refine EGOGiftCard component layout with four icon layers using absolute positioning. Add enhancement field to EGOGift type defaulting to zero. Place 128x128px gift icon at top with name below, grade icon in upper-left corner, enhancement icon conditionally in upper-right corner, and keyword icons in lower-right corner. Card height remains dynamic based on name length with content arranged top-aligned. Create image path helpers for new icon types following assetPaths conventions.

## Steps to Implementation

1. **Add enhancement field to EGOGift type**: Extend EGOGift interface in EGOGiftTypes.ts with enhancement number field defaulting to 0 for list view
2. **Create asset path helper functions**: Add getEGOGiftIconPath getEGOGiftGradeIconPath and getEGOGiftEnhancementIconPath functions to assetPaths.ts following existing conventions
3. **Remove tier badge from card**: Delete existing tier badge element from upper-left since grade icon will replace it
4. **Restructure card layout**: Update card to vertical layout with 128x128 icon at top followed by name below arranging content top-aligned
5. **Add center gift icon**: Place 128x128px gift image at card top using object-contain with fallback maintaining dimensions
6. **Add grade icon in upper-left**: Position grade icon absolutely in upper-left corner using negative offset for overflow effect with tier-based path
7. **Add enhancement icon in upper-right**: Conditionally render enhancement icon in upper-right corner when enhancement greater than zero using absolute positioning
8. **Move name below image**: Position gift name text below 128x128 icon maintaining line-clamp-2 truncation for dynamic height
9. **Verify keyword icon positioning**: Ensure lower-right keyword icons display correctly with new layout maintaining existing tooltip and fallback functionality
10. **Build and verify**: Run build confirming no TypeScript errors and all icon paths resolve correctly despite missing image files

## Success Criteria

- EGOGift type includes enhancement number field defaulting to 0 for proper TypeScript typing
- Card displays 128x128px gift icon at top from /images/egoGift/{id}.webp path with name text below
- Grade icon displays in upper-left corner from /images/icon/egoGift/grade{tier}.webp path replacing tier badge
- Enhancement icon displays conditionally in upper-right from /images/icon/egoGift/enhancement{level}.webp when enhancement greater than zero
- Keyword icons remain functional in lower-right corner maintaining existing tooltip and fallback behavior
- Card height dynamic based on name length with all content top-aligned preventing excessive whitespace
- All image fallbacks maintain expected dimensions preventing layout shift on load failures
- Entire card remains clickable link to detail page without interaction conflicts
- Build completes successfully with proper TypeScript types for new helper functions

## Assumptions Made

- Existing tier badge removed completely since grade icon serves same purpose in upper-left position as confirmed by user
- Enhancement field added to EGOGift type with default value 0 for list view as confirmed by user will be populated by other components later
- Gift name displayed below 128x128 icon as confirmed by user providing clear text identification alongside visual
- Card height dynamic based on name length with top alignment as confirmed by user preventing layout jumping across different name lengths
- Enhancement icon displays only when enhancement field greater than zero hiding icon for base level gifts in list view
- Image fallback uses transparent or hidden approach maintaining 128x128px dimensions rather than text replacement
- Grade icon uses tier field directly without additional mapping since tier values match expected grade format
- pointer-events-none applied to all decorative corner icons preventing click interference with card link
- z-index layering handled through HTML element order rather than explicit z-index values following existing patterns
