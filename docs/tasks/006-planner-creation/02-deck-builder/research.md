# Deck Builder - Codebase Research

## Overview of Codebase

- **Existing List Components**: IdentityList.tsx and EGOList.tsx with filtering by sinner, keyword, and search query
- **Card Components**: IdentityCard.tsx and EGOCard.tsx render layered composites (image, frame, sinner icon)
- **Filter Logic**: Sinner/keyword filters use AND logic; search uses OR logic across name/keyword/trait
- **Responsive Grids**: Lists use 2-8 column grids depending on screen breakpoint
- **Data Loading**: useEntityListData hook loads and validates spec + i18n data via TanStack Query
- **Runtime Validation**: All data validated with Zod schemas on load
- **BASE_LEVEL Constant**: Defined as 55 in constants.ts - needs rename to MAX_LEVEL per instructions
- **12 Sinners**: Defined in SINNERS array (YiSang through Gregor)
- **7 Sins**: Wrath, Lust, Sloth, Gluttony, Gloom, Pride, Envy for affinities
- **Uptie/Threadspin**: Currently hardcoded to level 4 in detail pages - needs configurable selector
- **PlannerMDNewPage**: Has KeywordSelector component with icon grid and multi-select pattern
- **Asset Paths**: Centralized in assetPaths.ts with helpers for all image/icon paths
- **Card Styling**: Uses shadcn/ui primitives with Tailwind; selected state uses border/background changes
- **EGO Ranks**: Zayin, Teth, He, Waw, Aleph - maps to tier display in cards
- **Sin Colors**: Used for skill frames, EGO info panels, attack type frames via path helpers

## Codebase Structure

- **frontend/src/components/identity/**: IdentityList.tsx, IdentityCard.tsx
- **frontend/src/components/ego/**: EGOList.tsx, EGOCard.tsx
- **frontend/src/components/ui/**: shadcn/ui primitives (Button, Card, Dialog, etc.)
- **frontend/src/routes/**: Page components including PlannerMDNewPage.tsx
- **frontend/src/hooks/**: useEntityListData.ts, useEntityDetailData.ts for data fetching
- **frontend/src/types/**: IdentityTypes.ts (skills with uptie 3/4), EGOTypes.ts (threadspin 3/4)
- **frontend/src/lib/constants.ts**: SINNERS, SINS, STATUS_EFFECTS, BASE_LEVEL, PLANNER_KEYWORDS
- **frontend/src/lib/assetPaths.ts**: All image path functions (sinner icons, sin icons, frames)
- **frontend/src/lib/router.tsx**: TanStack Router code-based routing config
- **static/data/identity/**: JSON files per identity ID (skills, passives, stats)
- **static/data/EGO/**: JSON files per EGO ID (costs, resistances, skills with threadspins)
- **static/i18n/EN/**: Localized name files for identities and EGOs

## Gotchas and Pitfalls

- **Uptie vs Threadspin Split**: Instructions note threadspin tiers expanding to 5 - must separate from uptie
- **Hardcoded Tier Display**: EGOCard.tsx has tier hardcoded to 4 - needs dynamic selection
- **Level Input Pattern**: Can't use slider for small cards - use increment/decrement buttons per instructions
- **Default Equipment**: Default identity/EGO IDs end with 01 (e.g., 10101 for sinner 1)
- **EGO Slot Logic**: If HE slot empty, clicking HE EGO fills it; if occupied, clicking replaces
- **Deployment Count**: 7 deployed + 5 backup from 12 total sinners
- **Card Links**: Current cards are Links to detail pages - need different behavior for deck builder
- **Identity Skills**: Have sin affinity per skill slot (skill1/2/3) - display as colored icons
- **EGO Costs**: Array of 7 values corresponding to 7 sins - need EA calculation
- **Keyword EA**: Must calculate keyword counts across all selected identities
- **Sin Casing**: Data uses PascalCase (Wrath) but some asset paths expect lowercase
- **Layered Rendering**: Cards use z-index stacking for image composites - maintain order
- **Selected Indicator**: Need visual indicator for equipped items (not currently in list components)
