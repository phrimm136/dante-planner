# Implementation Plan: EGO Card Component

## Clarifications Needed

No clarifications needed - requirements are clear

## Task Overview

Replace placeholder EGOCard component with fully layered visual design featuring circular EGO image, static frame overlay, sinner icon with background in top-right corner, sin-colored info panel at bottom rendered through pre-cached frames, and dual rank indicators. The info panel contains three sections with rank icon left, EGO name center, and stretched tier icon right at 30-degree angle. Sin affinity field already added to EGOSpecList.json enabling proper panel coloring using existing frame cache system. Both Identity and EGO data updated to PascalCase format without brackets.

## Steps to Implementation

1. **Update data hooks for PascalCase format**: Modify useIdentityData and useEGOData to handle PascalCase sinner and keywords without brackets from updated spec JSON files
2. **Update utility functions for PascalCase**: Modify parseBracketNotation calls and icon path functions to work with PascalCase values directly without bracket parsing
3. **Add EGO image path utilities**: Create helper functions for getEGOImagePath, getEGOFramePath, getEGORankIconPath, getEGOTierIconPath, and getEGOInfoPanelPath
4. **Extend frame cache for EGO panels**: Add EGO info panel background pre-rendering to frameCache.ts with sin-colored variants for all seven sins
5. **Build layered EGO card structure**: Replace placeholder with multi-layer absolute positioning for circular image, static frame, sinner icon and background, rank indicators, and cached panel
6. **Create info panel component**: Implement three-section flex layout with small rank icon left, name center, stretched tier icon right
7. **Apply tier icon stretching**: Transform tier icon with skewX or scaleX at 30 degrees creating stretched appearance toward upper-right
8. **Position all overlay elements**: Set sinner icon and 1StarSinnerBG in top-right corner and large rank indicator above panel
9. **Add EGO detail route**: Register /ego/$id route in router.tsx for navigation from card to detail page
10. **Test with PascalCase data**: Verify filters, search, and display work correctly with updated sinner and keyword format

## Success Criteria

- Both Identity and EGO components work with PascalCase data format without brackets for sinner and keywords
- Circular EGO image displays with static frame overlay using clip-path or border-radius for proper clipping
- Sinner icon and background positioned in top-right corner using negative offsets matching identity pattern
- Info panel renders with pre-cached sin-colored background from frameCache loaded synchronously
- Panel three-section layout shows rank icon left, name center, tier 4 icon right with proper alignment
- Tier icon stretched at 30-degree angle toward upper-right without text overlap
- Large rank indicator above panel same size as panel rank icon
- Card navigation to /ego/{id} works correctly
- All icon paths resolve with PascalCase sinner names without bracket notation

## Assumptions Made

- Frame cache pre-rendering used for info panel coloring as specified
- Tier icon uses skewX or scaleX transform starting at 30 degrees subject to adjustment
- Large rank indicator and panel rank icon render at identical size using same assets
- Card maintains w-40 h-56 dimensions matching placeholder and identity sizing
- PascalCase migration applies to both Identity and EGO data requiring coordinated updates across filters and utilities
