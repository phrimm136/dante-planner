# Research: Refine EGO Gift Card

## Overview of Codebase

- EGOGiftCard currently uses Link wrapper with relative positioning enabling absolute positioned children for icon placement
- Card displays tier badge in upper-left with dynamic styling for EX tier using conditional className
- Gift name centered in card layout using text-lg font-semibold with line-clamp-2 truncation
- Keywords rendered as icon-only display in lower-right corner using absolute bottom-2 right-2 positioning
- Keyword icons already use onError handler replacing failed images with text span fallback
- Card uses border rounded-lg p-4 styling with hover:shadow-md transition-shadow effects
- Grid layout responsive with 1 column mobile to 4 columns large screens creating compact card size
- EGOGift type provides tier keywords cost name themePack fields but missing enhancement level data
- Tier values stored as strings like 1 3 EX requiring string comparison not numeric
- Enhancement level implicitly stored in i18n descs array length minus one not in gift spec data
- assetPaths.ts defines helper functions for icon paths following convention /images/icon/{category}/{name}.webp
- Vite config sets publicDir to ../static serving static/images directory as /images in browser
- IdentityCard and EGOCard use negative offset positioning like -top-2 -right-2 for icon overflow beyond card bounds
- pointer-events-none applied to decorative icons preventing interaction conflicts with card link
- Image fallback patterns in codebase use onError handler but current DOM manipulation bypasses React reconciliation

## Codebase Structure

- EGOGiftCard component located at frontend/src/components/egoGift/EGOGiftCard.tsx receiving gift prop from parent list
- EGOGift type definition at frontend/src/types/EGOGiftTypes.ts containing spec and i18n merged data
- Asset path helpers centralized in frontend/src/lib/assetPaths.ts for consistent icon path generation
- Static images stored in static/images directory with subdirectories for icon statusEffect sin sinners EGO UI categories
- Expected new directories static/images/egoGift for 128x128 gift icons and static/images/icon/egoGift for grade enhancement icons
- Gift spec data in static/data/EGOGiftSpecList.json and names in static/i18n/EN/EGOGiftNameList.json
- Card rendered within EGOGiftPage using grid layout with gap-4 spacing between cards
- Router configured with /ego-gift path for list and /ego-gift/$id for detail navigation
- getKeywordDisplayName utility in lib/utils.ts maps PascalCase keywords to display names for tooltips
- getStatusEffectIconPath function already exists returning /images/icon/statusEffect/{keyword}.webp for keyword icons

## Gotchas and Pitfalls

- Enhancement level data not part of EGOGift type requiring derivation from i18n descs array or addition to spec
- No actual image files exist in static/images/egoGift or static/images/icon/egoGift directories creating all broken images initially
- Tier stored as string not number requiring string comparison gift.tier === EX instead of numeric checks
- Current onError handler uses DOM manipulation creating span elements bypassing React declarative rendering
- Fallback areas must maintain 128x128px dimensions even when images fail requiring CSS min-height min-width constraints
- Grid responsive layout creates varying card sizes across breakpoints affecting icon positioning consistency
- Negative offset positioning for icon overflow requires testing across different card dimensions
- Enhancement icon should conditionally render only for enhanced gifts but current list has no enhancement data
- Multiple absolute positioned icons may overlap requiring careful z-index management through HTML element order
- Keyword icons array mapped with index key pattern should use stable unique key when available
- Grade icon path format grade{tier}.webp with tier as string EX creates gradeEX.webp filename
- Center gift icon 128x128px may dominate small cards on mobile requiring responsive sizing consideration
- Link wrapper clickability conflicts with icons requiring pointer-events-none on decorative overlays
- Card padding p-4 reduces available space for 128x128 central icon requiring layout adjustment
- Text fallback for missing icons inconsistent sizing creating layout shift when some load and others fail
