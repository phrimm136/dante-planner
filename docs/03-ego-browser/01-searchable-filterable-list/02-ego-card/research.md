# Research: EGO Card Component Implementation

## Overview of Codebase

- IdentityCard uses 4-layer absolute positioning with w-40 h-56 dimensions for card container and centered overflow-hidden for image cropping
- EGO images are circular unlike rectangular identity images requiring different clipping or masking strategy for display
- EGO frame is static egoFrame.webp that does not change color with sin type unlike identity frames with sin coloring
- Only EGO info panel background receives sin coloring through pixel multiplication not the main frame overlay
- Layer composition pattern stacks image, frame overlay, sinner background, and sinner icon using pointer-events-none to maintain clickability
- Sinner icon and background positioned in top-right corner using negative offsets -top-1 -right-1 for icon and -top-2 -right-2 for background
- Frame caching system pre-renders sin-colored frames using Canvas pixel multiplication stored as data URLs in Map for instant retrieval
- Cache initialization happens at app startup with multiplyImageColor utility performing RGB multiplication for each pixel
- SIN_COLORS in globalConstants provides foreground and background hex values for all seven sins plus defense
- Image path utilities in identityUtils.ts provide getters for identity images, frames, sinner icons, and backgrounds with bracket notation parsing
- TanStack Router Link component wraps entire card for navigation to detail pages preserving visual hierarchy
- EGO data structure currently includes id, name, rank, sinner, and keywords but lacks sin affinity field needed for panel coloring
- Static assets confirmed available include circular EGO images in /images/EGO/{id}/awaken_info.webp and static frame in /images/UI/formation/egoFrame.webp
- Rank icons exist in both /images/UI/EGO/{rank}.webp for large display and /images/icon/EGO/{rank}.webp for panel use
- Tier icons located at /images/UI/common/tier{1-5}.webp for threadspin tier display requiring rotation transform toward upper-right
- EGO info panel background image at /images/UI/formation/egoInfoPanel.webp serves as base for sin color multiplication
- Current EGOCard placeholder uses same w-40 h-56 dimensions with Link navigation to /ego/{id} already wired
- Over 100 EGO IDs available from 20101 through 21203+ with consistent directory structure under /images/EGO/

## Codebase Structure

- Identity components in /frontend/src/components/identity/ establish card layering patterns to adapt for circular EGO images
- Common components in /frontend/src/components/common/ provide reusable IconFilter and SearchBar already used by EGO pages
- EGO components in /frontend/src/components/ego/ include current placeholder EGOCard plus filters, search, and list components
- Image utilities centralized in /frontend/src/lib/identityUtils.ts need extension with EGO-specific path getters
- Frame cache system in /frontend/src/lib/frameCache.ts handles pre-rendering with getCachedFrame for synchronous retrieval
- Global constants in /frontend/src/lib/globalConstants.ts define SIN_COLORS used for sin-based panel coloring
- EGO types in /frontend/src/types/EGOTypes.ts define interfaces but EGOData remains minimal requiring extension for sin field
- Static data in /static/data/EGOSpecList.json provides rank, sinner, keywords but missing sin affinity mapping
- EGO images organized as /static/images/EGO/{id}/awaken_info.webp with circular format requiring clip-path or border-radius
- UI assets in /static/images/UI/formation/ contain static egoFrame.webp and egoInfoPanel.webp for sin coloring
- Route configuration in /frontend/src/lib/router.tsx already includes /ego route but needs /ego/$id detail route
- Hook in /frontend/src/hooks/useEGOData.ts loads and merges spec data with i18n names ready for card consumption

## Gotchas and Pitfalls

- EGO data lacks sin affinity field requiring decision on whether to extend EGOSpecList.json or derive from rank mapping
- Sin color determination critical for info panel rendering but no clear mapping exists between EGO rank and sin type
- Circular EGO images need border-radius or clip-path circular clipping unlike rectangular identity image cropping
- EGO frame is static without sin coloring simplifying implementation compared to identity frames with color multiplication
- Only info panel background requires sin color pre-rendering through frame cache not the main EGO frame
- Adding EGO panel variants to frameCache initialization increases startup time requiring performance consideration
- CSS blend mode alternative for panel coloring avoids cache complexity but may have browser compatibility concerns
- Tier icon rotation transform requires careful positioning to avoid overlapping with name text or extending beyond panel bounds
- Sinner background uses star-based paths like 1StarSinnerBG.webp but EGO uses rank requiring clarification on which star level to use
- Negative positioning offsets for corner elements fragile if card dimensions change requiring coordinated updates
- parseBracketNotation needed for sinner field but EGO keywords may already be parsed requiring verification
- Image loading errors need graceful fallbacks since 100+ EGO IDs may have missing assets during development
- Info panel flex layout requires careful width distribution between rank icon, name text, and tier icon sections
- Rank icon appears twice - once large above panel and once small inside panel left section requiring different size variants
- Frame caching uses cache key pattern framePath|color so adding panels requires unique key generation for each sin variant
