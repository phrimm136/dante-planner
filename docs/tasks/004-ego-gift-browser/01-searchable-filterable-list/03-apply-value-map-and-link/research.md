# Research: Apply Value Map and Link

## Overview of Codebase

- keywordMatch.json contains two types of entries: bracketed format like [combustion] for in-game text parsing and PascalCase format like Combustion for display mapping
- Bracketed entries map game text placeholders to lowercase display names while PascalCase entries map internal keyword names to display names
- Both formats map to same display value: [combustion] and Combustion both map to burn
- EGOGiftCard currently displays plain div with tier badge name and keywords as simple text spans without icons or links
- Keywords in EGOGiftCard rendered as blue badge text showing raw PascalCase values like Combustion instead of display names like burn
- EGOGiftKeywordFilter already uses getStatusEffectIconPath function to display keyword icons in filter buttons
- IconFilter component shows tooltips with raw keyword names but should show display names
- IdentityCard and EGOCard use Link component from TanStack Router wrapping entire card as clickable element
- Link pattern uses to prop with route path and params prop with dynamic id parameter
- Routes already registered in router.tsx: /ego-gift for browser and /ego-gift/$id for detail page
- getStatusEffectIconPath function takes PascalCase keyword and returns path to icon like /images/icon/statusEffect/Combustion.webp
- KEYWORD_ORDER constant defines eleven keywords used for EGO Gift filtering including Combustion Laceration Vibration plus attack types
- STATUS_EFFECTS constant defines seven display-friendly names like Burn Bleed Tremor for Identity and EGO filtering
- useSearchMappings hook creates reverse mapping from display names to PascalCase keywords for search functionality
- EGO Gift data in EGOGiftSpecList.json stores keywords in PascalCase format matching KEYWORD_ORDER values
- Keyword filter already filters out Common keyword from KEYWORD_ORDER preventing it from appearing as filter button
- Common keyword used internally for gifts with no specific keywords but should not be displayed
- Translation system loads keywordMatch.json dynamically for mapping keywords to display names

## Codebase Structure

- Keyword mapping data stored at static/i18n/EN/keywordMatch.json containing seventeen entries with dual format
- EGO Gift card component located at frontend/src/components/egoGift/EGOGiftCard.tsx receiving gift prop with id name tier keywords
- Keyword filter component at frontend/src/components/egoGift/EGOGiftKeywordFilter.tsx using IconFilter for display
- Icon path helper function defined in frontend/src/lib/assetPaths.ts alongside other asset path utilities
- Keyword constants defined in frontend/src/lib/constants.ts with KEYWORD_ORDER and STATUS_EFFECTS arrays
- Search mappings hook at frontend/src/hooks/useSearchMappings.ts loading keywordMatch.json for reverse mapping
- Router configuration at frontend/src/lib/router.tsx with routes already registered for gift browser and detail pages
- Detail page component at frontend/src/routes/EGOGiftDetailPage.tsx with placeholder components and TODO comments
- Common IconFilter component at frontend/src/components/common/IconFilter.tsx providing reusable icon button grid
- EGO Gift spec data at static/data/EGOGiftSpecList.json containing metadata with keywords in PascalCase format

## Gotchas and Pitfalls

- Three different keyword sources must stay synchronized: keywordMatch.json for display names KEYWORD_ORDER for filter list EGOGiftSpecList.json for actual data
- Keyword display name mismatch between internal format Combustion Laceration Vibration and display format burn bleed tremor
- STATUS_EFFECTS uses display-friendly names while KEYWORD_ORDER uses internal game format names creating potential confusion
- Removing bracketed entries from keywordMatch.json will affect text-based keyword matching used by search functionality
- Common keyword appears in KEYWORD_ORDER but must be filtered out from display since it represents absence of keywords
- Icon paths expect exact PascalCase matching like Combustion.webp requiring consistent casing throughout system
- Multiple cards may link to same detail page requiring consideration of navigation state and back button behavior
- getStatusEffectIconPath function has no fallback for missing icons potentially causing broken image displays
- Gift keywords stored as array in data requiring iteration and mapping for each keyword display
- Link component changes card from div to anchor requiring CSS adjustments to maintain hover and transition effects
- Tooltip display in IconFilter currently shows raw keyword requiring prop change to pass display names
- useSearchMappings creates reverse mapping loading keywordMatch.json dynamically requiring loading state consideration
- Router params type safety requires string id but gift data uses numeric ids needing conversion consistency
- Card link styling must preserve existing hover effects border radius and shadow transitions when wrapping in Link component
- Missing keyword icons will cause runtime errors requiring validation or graceful fallback handling
