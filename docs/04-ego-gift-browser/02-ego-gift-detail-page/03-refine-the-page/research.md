# Research: Refine the Page

## Overview of Codebase

- EGOGiftDetailPage uses inline two-column grid layout with gift image, name, and cost stacked vertically in left column
- Gift components separated into dedicated files in /frontend/src/components/gift/ directory
- GiftImage displays 32x32 centered image with bg-gray-50 background in bordered container
- CostDisplay contains 6x6 gray rounded square placeholder next to "Cost: {number}" text requiring replacement
- EnhancementPanel displays each level with cyan square labeled "Level", pink square for cost, and purple-100 description background
- EnhancementLevels wrapper generates "Level X" labels for each enhancement panel needing removal
- Current implementation uses hardcoded Tailwind color classes without consistent theme system
- Similar detail pages (EGO, Identity) use DetailPageLayout component with leftColumn/rightColumn props pattern
- DetailPageLayout provides standardized container mx-auto p-8 grid structure with space-y-6 for vertical spacing
- Other detail pages use minimal panel styling with border rounded p-3 without background colors
- bg-muted used in EGOGiftList and IdentityDetailPage sanity sections suggesting preferred theme neutral
- EGO components use sin-based image overlays for themed backgrounds rather than Tailwind color classes
- Asset path helpers already exist for gift icons, grade icons, enhancement icons in assetPaths.ts
- Cost icon path should be /static/images/icon/egoGift/coin.webp per instructions but file not provided yet
- Related EGOGiftCard component recently refactored with vertical layout and four icon layers
- Icons use pointer-events-none pattern preventing click interference with interactive wrappers
- getEGOGiftIconPath, getEGOGiftGradeIconPath, getEGOGiftEnhancementIconPath functions available for icon paths
- Container flex patterns use items-center justify-center for centered image display with object-contain

## Codebase Structure

- Detail page implementation at /frontend/src/routes/EGOGiftDetailPage.tsx with inline grid layout
- Gift-specific components in /frontend/src/components/gift/ directory awaiting consolidation to egoGift
- EGOGiftCard and filter components already in /frontend/src/components/egoGift/ directory
- Asset path helpers centralized in /frontend/src/lib/assetPaths.ts following naming conventions
- DetailPageLayout common component at /frontend/src/components/common/DetailPageLayout.tsx provides standardized structure
- Two-column grid uses grid-cols-1 lg:grid-cols-2 gap-6 responsive breakpoint pattern
- Left column contains GiftImage, GiftName, CostDisplay, AcquisitionMethod stacked with implicit spacing
- Right column contains EnhancementLevels wrapper with multiple EnhancementPanel child components
- Component hierarchy: EGOGiftDetailPage > gift components > individual panels and displays
- Global theme colors use muted, border, and muted-foreground from design system
- Static image assets stored in /static/images/ with icon subdirectories for different types
- Icon naming follows pattern of category/identifier.webp like egoGift/coin.webp or sin/Wrath.webp
- Enhancement data stored with level-specific descriptions and cost values from JSON data
- Task instructions suggest consolidating /frontend/src/components/gift/ into /frontend/src/components/egoGift/

## Gotchas and Pitfalls

- Gray and colored square placeholders throughout requiring replacement with actual icon images
- Instructions specify coin icon path but file not provided creating broken image initially
- Background colors hardcoded with purple-100, gray-100, cyan-200, pink-200 requiring theme migration
- No consistent theme system currently applied to gift detail page unlike other detail pages
- EnhancementLevels component generates "Level X" text through getLevelLabel function requiring suppression
- Current vertical stack layout for image-name-cost needs restructuring to horizontal pairing
- DetailPageLayout pattern used in EGO/Identity pages not applied to gift page creating inconsistency
- Gift components in separate /frontend/src/components/gift/ directory pending consolidation causing duplication
- Icon dimensions need consistency with existing patterns like 32x32 for main images, smaller for decorative
- Missing cost icon path helper function in assetPaths.ts requiring addition
- Component reuse between list cards and detail page may create unintended coupling
- Theme color migration requires identifying equivalent muted/border classes for current backgrounds
- Enhancement icon conditional rendering pattern from card component may inform panel icon display
- Image error handling not implemented for missing coin icon requiring fallback consideration
- Moving image position affects responsive layout requiring testing at different breakpoints
