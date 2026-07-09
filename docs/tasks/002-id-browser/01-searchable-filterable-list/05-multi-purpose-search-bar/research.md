# Research: Multi-Purpose Search Bar

## Overview of Codebase

- React 19 with TypeScript 5.9, Vite build tool, TanStack Router for routing
- i18next integration for internationalization with 4 languages: EN, KR, JP, CN
- Custom hook pattern: useIdentityData returns typed identity array with useMemo caching
- State management: React useState for local state, Context API for global state, TanStack Query for server data
- IdentityPage lifts state up: selectedSinners and selectedKeywords passed to child components
- Current search bar is placeholder component, needs full implementation
- Filtering done client-side in IdentityList using Array.filter() method
- AND logic between filter types: sinner filter AND keyword filter AND search
- AND logic within keyword filter: identity must have ALL selected keywords
- parseBracketNotation utility strips brackets from game data values
- Set<string> used for filter state providing efficient lookup and uniqueness
- IconFilter generic component pattern: accepts options, selected, onChange, getIconPath
- useIdentityData hook watches i18n.language and re-processes data on language change
- Path aliases configured: @/ for src imports, @static/ for static data imports
- Tailwind CSS v4 with dark mode support via CSS variables

## Codebase Structure

- Identity components in /frontend/src/components/identity/: IdentityList, IdentitySinnerFilter, IdentityKeywordFilter, IconFilter, IdentitySearchBar
- Custom hooks in /frontend/src/hooks/: useIdentityData pattern to follow
- Utility functions in /frontend/src/lib/: identityUtils.ts has parseBracketNotation and icon path helpers
- Parent component at /frontend/src/routes/IdentityPage.tsx manages filter state
- Static data at /static/data/identitySpecList.json with bracketed notation for all game values
- Translation data at /static/i18n/{language}/: identityNameList.json, keywordMatch.json, traitMatch.json
- identitySpecList.json structure: ID-keyed objects with star, sinner, traits, keywords arrays
- Only EN language has complete mapping files, other languages need creation
- keywordMatch.json maps bracketed keywords to natural language: "[combustion]" -> "burn"
- traitMatch.json maps trait keys to display names: "HCorp." -> "H Corp."
- identityNameList.json maps identity IDs to full names directly

## Gotchas and Pitfalls

- Search requires reverse mapping: user types "burn" but data has "[combustion]" keyword
- Partial matching requirement: Korean "파" must match "파열" then map to "[rupture]"
- Debouncing needed for live search with typical 300-500ms delay to avoid performance issues
- Search logic is OR between categories: match name OR keyword OR trait, then AND with existing filters
- Case sensitivity must be handled with toLowerCase normalization on both input and mappings
- Bracket notation bidirectional conversion: display strips brackets, search adds them internally
- Missing mapping files for KR, JP, CN languages require fallback logic or graceful degradation
- Current data has only 1 identity but production will have 300+ identities requiring useMemo optimization
- State lifting required: IdentityPage needs searchQuery state passed to both IdentitySearchBar and IdentityList
- Controlled component pattern needed: search input value and onChange handler
- Multiple filter combination: search results must respect active sinner and keyword filters
- Search algorithm complexity: check identityNameList for names, check keywordMatch for keywords, check traitMatch for traits
- Whitespace and special characters in user input need sanitization
- Empty search should show all identities matching current filters, not hide everything
