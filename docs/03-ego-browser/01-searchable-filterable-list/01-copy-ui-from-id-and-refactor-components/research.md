# Research: Copy UI from Identity and Refactor Components

## Overview of Codebase

- Identity browser uses parent-managed state in IdentityPage with child components receiving props and callbacks
- Filter components follow adapter pattern: domain-specific wrappers configure generic IconFilter with custom options and icon resolvers
- IconFilter is already parameterized and reusable - accepts options array, selection Set, onChange callback, and icon path resolver function
- Search bar uses 100ms debounce on input with dual useEffect hooks for debounce and external sync
- IdentityList applies three-layer filtering: sinner filter (OR logic), keyword filter (AND logic), search (partial match on name/keyword/trait)
- All filters combined with AND logic between filter types - must pass sinner AND keyword AND search checks
- TanStack Router v1.135.2 used with createRoute pattern - EGO routes follow same structure with getParentRoute pointing to rootRoute
- Navigation uses Link component with dynamic paths like `/identity/${id}` - EGO will use `/ego/${id}`
- Data hooks like useIdentityData merge static JSON with i18n translations, memoized by language
- useSearchMappings provides Map structures converting natural language terms to bracketed notation values
- Tailwind utility classes follow consistent patterns: bg-card for containers, border-border for borders, responsive grid-cols-*
- CVA-based Button component from shadcn provides variants (default/outline/ghost) and sizes (sm/default/lg/icon)
- Theme and language managed via global context providers in ThemeSync and LanguageSync components
- GlobalLayout wraps all routes providing Header and Footer structure
- Utility functions in identityUtils.ts provide image path getters and bracket notation parsers

## Codebase Structure

- Route components live in `/frontend/src/routes/` - IdentityPage.tsx and IdentityDetailPage.tsx establish pattern
- Identity-specific components in `/frontend/src/components/identity/` include filters, search, list, cards, and detail page components
- Shared UI components live in `/frontend/src/components/ui/` - button, dropdown-menu from shadcn
- Common components in `/frontend/src/components/` include GlobalLayout, Header, Footer
- Router configuration centralized in `/frontend/src/lib/router.tsx` where route tree is assembled
- Type definitions in `/frontend/src/types/IdentityTypes.ts` define Identity and IdentityData interfaces
- Custom hooks in `/frontend/src/hooks/` - useIdentityData fetches and merges data, useSearchMappings provides search term mappings
- Utilities in `/frontend/src/lib/` include identityUtils (image paths), globalConstants (sin colors, sinner list), utils (color multiplication)
- Static data in `/static/data/` includes identitySpecList.json with identity specs
- i18n translations in `/static/i18n/{lang}/` include identityNameList.json, keywordMatch.json, traitMatch.json
- Component hierarchy: Page → FilterBar + SearchBar + List → Card items following unidirectional data flow
- Detail page components highly specialized: StatusPanel, ResistancePanel, StaggerPanel, TraitsDisplay, SkillCard ecosystem
- Frame caching system in frameCache.ts pre-renders 58 sin-colored frames at app startup for instant skill display

## Gotchas and Pitfalls

- IconFilter already generic but buried in identity folder - should be moved to common before EGO implementation to avoid duplication
- IdentitySearchBar hardcodes i18n key `pages.identity.searchBar` - needs generalization to accept placeholder prop for EGO reuse
- Filter state uses Set<string> requiring immutable updates - must pass new Set() instances not mutate existing ones
- Bracket notation appears throughout - sinner names like `[yiSang]` need parseBracketNotation before display or icon lookup
- IdentityList filtering logic couples data fetching with filtering - consider extracting filter logic into useFilteredList hook
- Search matching uses both keywordToValue and traitToValue Maps - EGO will need equivalent mapping structures
- Frame cache initialization is async and silent - no UI feedback during 58-frame pre-render, could cause brief flicker on first skill view
- Skill image paths have complex variant logic (uptie4 fallback) - error handling with useEffect and setState pattern needed
- Defense skills special-cased throughout (no attack type, only level 1 frames) - watch for similar EGO edge cases
- Responsive grid uses 8 breakpoints (grid-cols-2 to xl:grid-cols-8) - ensure EGO cards fit same sizing constraints
- Detail page data loaded via dynamic import of `/static/data/identity/{id}.json` - EGO needs parallel data structure
- Type definitions in IdentityTypes.ts mix list data (Identity) and detail data (IdentityData) - maintain separation for EGO types
- useSearchMappings merges multiple JSON files into Maps - performance acceptable but watch for memory with large EGO datasets
- No loading states in IdentityPage despite async data - acceptable for static JSON but may need loaders for future API integration
- Filter components don't persist state to URL params - navigating away loses filter selections, consider adding to route search params
