# Implementation Plan: Refine the Page

## Task Overview

Refactor EGO Gift detail page layout from vertical stack to horizontal image-name-cost arrangement and replace placeholder UI elements with proper icons throughout. Update CostDisplay component to use coin icon instead of gray square placeholder. Modify EnhancementPanel component to display enhancement icons and cost icon-value pairs instead of colored placeholder squares. Remove level text headings from enhancement sections relying on enhancement icons for level identification. Migrate hardcoded Tailwind background colors to global theme system matching patterns used in other detail pages.

## Steps to Implementation

1. **Add coin icon path helper function**: Create getEGOGiftCoinIconPath function in assetPaths.ts following existing naming conventions and documentation style.

2. **Refactor main layout in EGOGiftDetailPage**: Restructure left column from vertical stack of separate components to horizontal flex layout with gift image on left and name-cost vertical pair on right, and pass tier prop to EnhancementLevels component for cost calculation.

3. **Update CostDisplay component**: Replace gray square placeholder and "Cost:" text with coin icon and numeric value only using new path helper function.

4. **Extend EnhancementPanel component props**: Add level and cost props to EnhancementPanel interface enabling component to receive enhancement level number and calculated per-level cost value.

5. **Replace enhancement panel placeholders**: Remove cyan square with "Level" text replacing with level-specific enhancement icon using getEGOGiftEnhancementIconPath, remove pink square replacing with coin icon and calculated cost value.

6. **Update EnhancementLevels wrapper**: Add tier prop to component interface, implement cost calculation function based on tier and level (50/100 for tier 1, 60/120 for tier 2, 75/150 for tier 3, 100/200 for tier 4, none for tier 5/EX), pass level index and calculated cost to each EnhancementPanel, and remove h3 heading displaying "Level X" text.

7. **Migrate background colors to theme**: Replace bg-gray-100, bg-purple-100, bg-gray-50 with theme color classes matching global theme pattern from other detail pages.

8. **Update GiftImage component path**: Change image source from /images/gift/ to /images/egoGift/ matching path conventions established in refine card task.

9. **Build and verify**: Run build process confirming TypeScript compilation succeeds and all icon paths resolve correctly despite missing image files.

10. **Test layout responsiveness**: Verify horizontal image-name-cost layout adapts properly at different breakpoints and enhancement icons display correctly for each level.

## Success Criteria

- Gift image positioned horizontally to left of name-cost vertical pair instead of stacked vertically
- CostDisplay shows coin icon with numeric value without "Cost:" text label
- Enhancement panels display level-specific enhancement icons with no "Level X" text heading above panel
- Enhancement panel cost section shows coin icon followed by calculated per-level cost value based on tier without pink square placeholder
- All background colors migrated from hardcoded values to theme color classes
- GiftImage uses correct path pattern matching other EGO gift components
- Build completes successfully with no TypeScript errors
- Layout remains responsive and functional across mobile and desktop breakpoints

## Assumptions Made

- Enhancement panels display per-level costs calculated from tier: 50/100 for tier 1, 60/120 for tier 2, 75/150 for tier 3, 100/200 for tier 4, no enhancement for tier 5/EX
- Theme background colors should use bg-muted pattern following EGODetailPage and IdentityDetailPage conventions
- Coin icons use consistent sizing throughout main display and enhancement panels
- Enhancement icons are level-specific using enhancement0.webp, enhancement1.webp, enhancement2.webp pattern
- Gift image set to 128x128px dimensions in horizontal layout matching size used in card component
- Coin icon path at /static/images/icon/egoGift/coin.webp will be broken initially per instructions
- Icon-first ordering pattern used for cost icon-value pairs matching existing UI patterns throughout application
