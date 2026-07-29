# Research: EGO Gift Detail Page UI Mock Up

## Layout Description from Mockup

**Three-Section Horizontal Layout:**

Left Section (narrow column):
- GiftImage component: Large centered artwork in red-bordered area
- GiftName component: Title text in orange-bordered area
- CostDisplay component: Icon and value pair in yellow-bordered area

Center Section (wide column):
- EnhancementLevels component: Green-bordered container with three stacked panels
  - EnhancementPanel Level 0: Gray panel containing
    - EnhancementIcon: Level indicator (0) in cyan box
    - EnhancementCost: Additional cost value (not shown for level 0)
    - Description: Effect text in purple box
  - EnhancementPanel Level +1: Gray panel containing
    - EnhancementIcon: Level indicator (+1) in cyan box
    - EnhancementCost: Additional cost value in pink box
    - Description: Effect text in purple box
  - EnhancementPanel Level +2: Gray panel containing
    - EnhancementIcon: Level indicator (+2) in cyan box
    - EnhancementCost: Additional cost value in pink box
    - Description: Effect text in purple box

Right Section (wide column):
- AcquisitionMethod component: Text explaining how to obtain gift in blue-bordered area

## Overview of Codebase

- DetailPageLayout component exists providing two-column responsive grid layout for all detail pages
- Two-phase data loading pattern used consistently with dynamic imports for game data then translations
- Detail pages use useParams hook to extract route ID parameter for data fetching
- LoadingState and ErrorState components handle async loading states with consistent UI feedback
- Identity and EGO detail pages serve as established patterns with similar header-panel-section structure
- Header components combine icon badge name and large centered image with action buttons overlay
- Info panels use border rounded padding with centered titles and icon-value grid displays
- Enhancement levels in skills use tabbed selector pattern with conditional styling for active state
- Translation files mirror data structure with matching ID-based organization in static directories
- Tailwind CSS used throughout with standard spacing patterns padding-3 or padding-4 for panels
- Common component patterns extracted to components/common directory for reuse across domains
- Domain-specific components live in subdirectories like components/identity and components/ego
- Grid layouts handle multiple items with grid-cols-3 for horizontal panels and grid-cols-7 for sin displays
- Typography hierarchy uses text-3xl for titles down to text-xs for small labels with semantic color classes
- Image sizing standardized with width-6 height-6 for icons and width-32 height-32 for skill composites

## Codebase Structure

- Detail page routes located in frontend/src/routes/ directory named with DetailPage suffix
- Domain components organized in frontend/src/components/{domain}/ subdirectories
- Common reusable layouts and utilities stored in frontend/src/components/common/
- Game data files stored at static/data/{type}/{id}.json with numeric ID-based naming
- Translation files mirror structure at static/i18n/{LANG}/{type}/{id}.json for each language
- Spec list aggregations stored at static/data/{Type}SpecList.json containing all entity metadata
- Name list translations stored at static/i18n/EN/{Type}NameList.json mapping IDs to display names
- Panel components named with Panel suffix like StatusPanel SinCostPanel following naming convention
- Header components named with domain prefix like IdentityHeader EGOHeader for top sections
- Route definitions centralized in frontend/src/lib/router.tsx using TanStack Router createRoute API

## Gotchas and Pitfalls

- Enhancement level data structure varies between features requiring careful interface design
- Translation loading must check language and gracefully fallback when files missing for non-English
- Dynamic imports use template literals requiring careful path construction to avoid build errors
- Grid column counts must match data array lengths or layouts break with empty cells
- Responsive breakpoints use lg prefix for desktop two-column switching to single column mobile
- Image paths must use exact casing matching static directory structure or imports fail
- Panel spacing relies on space-y utility requiring parent flex or block display context
- Color classes for tier badges use conditional logic needing type checking for string tier values
- Route parameters accessed via useParams requiring TypeScript assertions for ID string type
- Border and rounded classes must be combined as single class not separated for proper rendering
