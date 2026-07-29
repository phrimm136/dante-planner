# EGO Gift Observation - Research Findings

## Clarifications Needed

No clarifications needed - requirements are clear:
- Observation UI integrates alongside other sections in PlannerMDNewPage
- Gift selection list supports keyword filtering/search like main ego-gift page
- Clicking selected gift in showroom removes it from selection
- Tier indicator uses letter representations (I, II, III, IV, V)
- Background scale ratios hardcoded using Tailwind w- and h- classes

## Overview of Codebase

### Existing Patterns and Practices

- All static data loading uses TanStack Query with queryOptions pattern for cache management
- Zod schemas validate all JSON imports at runtime with strict parsing
- 7-day staleTime standard for static game data queries
- useEntityListData hook merges spec lists with i18n name lists for display
- Custom hooks created per data type with queryOptions factory pattern
- Lazy loading pattern for descriptions - only fetch on hover to reduce initial load
- React i18next for all user-facing text with t() function
- Consistent color scheme: #fcba03 for gold selection rings, #f8c200 for enhanced costs
- Component naming: Card suffix for full components, MiniCard for compact selection cards
- Selection state managed in parent containers using Set<string> for IDs
- Max selection enforcement via useEffect watching selection size changes
- Props drilling for callbacks: onSelect, onGiftSelectionChange patterns
- Image asset paths centralized in assetPaths.ts utility functions
- Tooltip displays on hover using conditional rendering based on isHovered state
- Grid layouts with auto-cols-max for dynamic content sizing
- Absolute positioning for overlay elements on card backgrounds
- Enhancement levels (0, 1, 2) managed locally in cards, selection state in parents
- State propagation to editor/db happens via parent component callbacks
- All components follow frontend-dev-guidelines for structure and patterns
- TypeScript strict mode with explicit interface definitions
- Validation errors thrown with descriptive [dataType] prefixes
- Icon sizing pattern: 128x128 full cards, 24x24 mini cards, 16x16 keywords
- Hover states use dedicated image overlays (onHover.webp, onSelect.webp)
- Cost display uses conditional styling - white for base, gold for enhanced
- Click event handlers prevent propagation when nested in Links
- Selection ring styling: ring-2 ring-[#fcba03] with rounded corners
- Background images sized with object-contain or object-cover based on needs

### Coding Standards and Frameworks

- React with TypeScript for all frontend components
- TanStack Router for file-based routing
- TanStack Query v5 for data fetching and caching
- Zod for runtime type validation of static data
- Tailwind CSS for styling with custom color values
- React i18next for internationalization
- Vite for build tooling and dev server
- Frontend follows SSR-ready architecture patterns

### How Current Code Relates to Task

- EgoGiftMiniCard already implements selection card with hover tooltips
- StartGiftRow shows multi-gift selection pattern with keyword grouping
- StartBuffSection demonstrates cost display with starlight icon
- EGOGiftList shows filtering by ID list for subset display
- useEntityListData provides data loading pattern to replicate
- PlannerMDNewPage already has sections for start buffs and start gifts
- Enhancement indicator images already exist for +1 and +2 levels
- Tier badge rendering logic exists in EGOGiftCard component
- Keyword icon mapping available via getStatusEffectIconPath
- Selection propagation pattern established in StartGiftSection

## Codebase Structure

### Project File Organization

- /frontend/src/components/ - React component library organized by feature
- /frontend/src/components/egoGift/ - EGO gift display components
- /frontend/src/components/startBuff/ - Start buff selection UI
- /frontend/src/components/startGift/ - Start gift selection UI (template reference)
- /frontend/src/hooks/ - Custom React hooks for data fetching
- /frontend/src/types/ - TypeScript type definitions
- /frontend/src/schemas/ - Zod validation schemas
- /frontend/src/routes/ - TanStack Router page components
- /frontend/src/utils/ - Utility functions including assetPaths.ts
- /static/data/ - Static JSON game data files
- /static/data/MD6/ - MD6-specific data including observation pools
- /static/data/egoGift/ - Individual EGO gift spec files (9001.json etc)
- /static/i18n/ - Internationalization data by language
- /static/i18n/EN/egoGift/ - English EGO gift names and descriptions
- /static/images/UI/egoGift/ - EGO gift UI backgrounds and overlays
- /static/images/UI/MD6/ - MD6 UI elements including starLight.webp
- /static/images/egoGift/ - Individual EGO gift icon images
- /static/images/icon/egoGift/ - Grade and enhancement indicators
- /static/images/icon/statusEffect/ - Keyword/effect icons

### Where Relevant Code Lives

- EGOGiftCard.tsx - Full card with routing, tier/keyword display logic
- EgoGiftMiniCard.tsx - Compact selection card with hover tooltip
- EGOGiftList.tsx - List component with filtering and sorting
- StartBuffCard.tsx - Reference for starlight cost display pattern
- StartGiftRow.tsx - Reference for multi-selection UI with keyword grouping
- StartGiftSection.tsx - Reference for selection state management
- PlannerMDNewPage.tsx - Main editor page to integrate observation section
- useEntityListData.ts - Data loading hook pattern to replicate
- useStartGiftPools.ts - Pool data loading pattern for observation
- useEgoGiftDescription.ts - Lazy description loading on hover
- EGOGiftTypes.ts - Interface definitions for gift data structures
- StartGiftSchemas.ts - Template for creating observation schemas
- assetPaths.ts - Centralized image path functions
- observationData.json - Contains cost list and eligible gift ID list (291 gifts)

### How Structure Affects the Task

- Must create new components in /frontend/src/components/egoGiftObservation/ or similar
- Observation section added to PlannerMDNewPage alongside existing sections
- New hook needed in /frontend/src/hooks/ for observation data loading
- New Zod schema in /frontend/src/schemas/ for validation
- Reuse existing EgoGiftMiniCard without modification
- Can import and reuse cost display pattern from StartBuffCard
- Must filter entity list by observationEgoGiftDataList IDs
- No routing needed - cards trigger selection not navigation
- Selection state propagated to PlannerMDNewPage for DB save
- i18n keys added to translation files for new UI text
- Filtering/search logic adapted from EGOGiftList component

## Gotchas and Pitfalls

### Technical Debt or Legacy Patterns to Avoid

- Don't create separate EGOGiftCard variant - reuse EgoGiftMiniCard for selection
- Avoid duplicating cost display code - extract reusable component
- Don't hardcode starlight costs - read from observationEgoGiftCostDataList
- Avoid fetching all 291 gift descriptions upfront - use lazy loading on hover
- Don't manage enhancement in observation section - gifts are base level only
- Avoid creating custom selection ring styles - reuse ring-[#fcba03] pattern
- Don't duplicate entity list merging logic - use useEntityListData
- Avoid inline image paths - use assetPaths.ts functions
- Don't create new validation patterns - follow existing Zod strict() parsing
- Avoid fetching observation data on every render - use TanStack Query cache

### Configuration Quirks

- observationData.json has gaps in ID sequence (not continuous 9001-9843)
- Cost list has exactly 3 entries for 1, 2, 3 gifts - not a general formula
- Gift IDs are numbers in observation list but strings in component props
- Enhancement images exist but observation gifts stay at base level
- Tier indicator uses letter 'I' not Roman numeral for Tier 1
- Keyword can be null or "None" string - both mean no icon displayed
- Background image sizes differ - bgEnhanced needs scaling via Tailwind classes
- EX tier uses special image tierEX.webp instead of text
- Hover tooltip shows only descs[0] even if gift has multiple levels
- i18n data split across nameList and individual gift files
- Asset paths assume /images/ prefix but static files in /static/images/
- Showroom max selection is 3 gifts - clicking removes from selection

### Known Issues, Common Mistakes, or Anti-patterns

- EgoGiftMiniCard expects isSelected prop but selection logic in parent
- Must prevent default Link behavior when card is clickable for selection
- Forgetting to handle null keyword results in broken icon path
- Not checking Set.has() before adding can exceed max selection
- Enhancement level state persists if card is reused - reset on mount
- Tooltip positioning can overflow viewport on screen edges
- Grid auto-cols-max causes horizontal scroll if too many items
- Zod validation throws on parse failure - wrap in try-catch or safeParse
- useQuery returns data: undefined initially - check isPending before render
- Image imports fail silently if path typo - verify in browser network tab
- Tailwind custom colors must be quoted strings in style prop
- React key prop required for mapped lists - use gift ID
- Event handlers on nested divs can fire multiple times - stopPropagation
- Set state updates don't trigger re-render unless new Set created
- useEffect dependency arrays missing callbacks cause stale closures

### Dependency Constraints

- TanStack Query v5 API differs from v4 - use queryOptions not config objects
- Zod strict() mode rejects unknown keys - data must match schema exactly
- React 18 concurrent features may cause double renders in dev mode
- Tailwind JIT requires exact class strings - no dynamic class concatenation
- i18next requires namespace configuration for egoGift translations
- Image formats must be webp for consistency with existing assets
- Vite import.meta.glob pattern for dynamic JSON imports
- TypeScript strict mode requires explicit null checks before access
- Tailwind w- and h- classes for scaling bgEnhanced images to match heptagon
