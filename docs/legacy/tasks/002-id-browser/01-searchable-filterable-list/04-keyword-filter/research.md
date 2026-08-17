# Research: Keyword Filter Implementation

## Overview of Codebase

- IdentitySinnerFilter provides excellent pattern to follow with Set-based state management and controlled props
- Filter uses immutable Set operations creating new instances on changes for React state updates
- Component pattern uses selectedItems Set and onSelectionChange callback for state lifting to parent
- Visual feedback uses border-primary and bg-primary/10 for selected state, bg-button for unselected state
- Layout uses h-14 fixed height container with horizontal scrolling for overflow items
- Icon buttons are w-8 h-8 with rounded-md border-2 styling for compact square appearance
- Clear all button uses × symbol with Button component variant outline and size icon-sm
- Toggle logic creates new Set, adds or deletes item, then calls onSelectionChange callback
- Title attributes on buttons provide tooltip text matching sinner filter accessibility pattern
- Component uses getSinnerIconPath utility to generate icon paths from bracketed names
- parseSinnerName utility strips brackets from data format converting [name] to name
- IdentityPage manages all filter state using useState with Set string and passes to both filter and list
- IdentityList receives filter state as props and applies filtering logic before rendering cards
- Empty selection shows all items following common filter UX pattern with size zero check
- Theme uses --button color (oklch 0.75 in light mode) for icon background visibility
- All components use TypeScript with strict typing and const assertions for readonly arrays
- i18next integration with translation keys already defined in common.json files
- Tailwind CSS for all styling with transition-all for smooth state changes
- React 19 with functional components and hooks exclusively no class components

## Codebase Structure

- Identity components live in frontend/src/components/identity/ directory for filter list and card components
- IdentityKeywordFilter placeholder currently shows translated text label needs complete replacement
- Utility functions centralized in frontend/src/lib/identityUtils.ts for shared parsing and path generation
- Status effect icons located at static/images/statusEffect/ as webp files for all seven effects
- Identity data in static/data/identitySpecList.json with keywords field as bracketed string array
- IdentityPage in frontend/src/routes/ manages state for all filters and coordinates filter and list
- Current keywords in data are only [rupture] and [tremor] but all seven icons exist
- Required keywords are burn bleed tremor rupture sinking poise and charge matching icon filenames
- Keywords field format matches sinner field using bracket notation like [keyword]
- IdentityList filtering happens in component using array filter method with Set has lookups
- UI components from shadcn pattern in components/ui/ like Button used across filters
- Translation files in static/i18n/ with EN JP KR CN subdirectories for internationalization
- Data hook useIdentityData provides Identity interface with keywords string array field
- Filter state management pattern established in IdentityPage can extend for keyword state

## Gotchas and Pitfalls

- Keywords field uses bracket notation like [rupture] requiring parsing before display or comparison
- Only rupture and tremor currently exist in identity data despite having seven status effect icons
- Placeholder uses h-20 but should use h-14 to match sinner filter compact height
- Must reuse parseSinnerName utility for keywords despite name since it generically strips brackets
- Filter combination logic needs careful handling for multiple filters AND versus OR semantics
- Keyword filtering likely uses OR within keywords (show if ANY selected keyword matches)
- Sinner and keyword filters combined should use AND (must match sinner AND must have keyword)
- Empty selection must show all items not hide all following established UX pattern
- Icon path construction must match exact filename format like burn.webp not Burn or [burn]
- Set state mutations must create new Set instances not modify existing for React updates
- Props interface must match sinner filter pattern for consistency with selectedKeywords and onSelectionChange
- Missing accessibility features like keyboard navigation and ARIA labels inherited from sinner filter
- Mobile usability concerns with w-8 icons and horizontal scrolling apply to keyword filter too
- No error handling for missing icons means broken images if paths incorrect
- IdentityList needs modification to accept and apply both sinner and keyword filters simultaneously

