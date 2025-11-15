# Research: Sinner Filter Implementation

## Overview of Codebase

- IdentityCharacterFilter is currently a placeholder component displaying translated text in a styled card container
- Component uses h-20 fixed height to align with other filter components in horizontal layout
- Translation key pages.identity.characterFilter controls displayed text with i18n support for EN/JP/KR/CN
- identitySpecList.json contains sinner field with bracket notation like "[yiSang]" requiring parsing
- Twelve sinner icons available as webp files in camelCase naming: yiSang, faust, donQuixote, etc
- parseSinnerName utility function removes brackets from sinner values to create image paths
- useIdentityData hook loads all identities from JSON, merges with language-specific names, returns memoized array
- Identity interface includes id, name, star, sinner, traits, keywords fields with full TypeScript typing
- IdentityList component renders all identities in responsive grid without current filtering logic
- IdentityPage composes filters in top row with horizontal flex layout, list below in separate container
- Project uses React Context API pattern for global state following ThemeContext and AuthContext examples
- Button component from shadcn provides multiple variants including outline, ghost, and icon sizes
- Dropdown menu component built on Radix UI primitives available for multi-select implementation
- All UI text uses translation keys through react-i18next for internationalization
- Tailwind CSS with custom theme tokens provides consistent styling across components

## Codebase Structure

- Filter components live in frontend/src/components/identity/ alongside IdentityList and IdentityCard
- Identity data hook in frontend/src/hooks/useIdentityData.ts handles data loading and transformation
- Utility functions in frontend/src/lib/identityUtils.ts provide image path generation and sinner name parsing
- Static data in static/data/identitySpecList.json with sinner values as source of truth for filtering
- Sinner icons in static/images/sinners/ directory using camelCase webp format matching parsed names
- Page composition in frontend/src/routes/IdentityPage.tsx assembles filters and list components
- UI primitives in frontend/src/components/ui/ include button and dropdown-menu from shadcn
- Translation files in static/i18n/LANG/common.json store all UI text keys by language
- Context providers in frontend/src/contexts/ establish patterns for shared state management
- Vite publicDir configuration serves static directory at /images/ path for runtime asset loading

## Gotchas and Pitfalls

- Sinner field uses bracket notation requiring parseSinnerName before matching or displaying
- Only one identity exists in current identitySpecList.json limiting testing without mock data
- English language only has identity name translations, other languages fall back to ID
- Filter state management approach unclear - could use local useState or Context API depending on scope
- Grid layout uses justify-items-center which may affect alignment when adding filter controls
- Height constraint of h-20 on filter components restricts vertical space for multi-row content
- Sinner icon size in cards is w-12 h-12 but filter buttons may need different sizing
- No existing pattern for multi-select filters, will need to establish new UI convention
- Filter component rename to IdentitySinnerFilter affects imports in IdentityPage
- Responsive grid breakpoints may cause layout issues if filter width changes significantly
- No error handling pattern established for missing sinner icons or data validation failures
- Translation keys need updating when renaming component to match new filter purpose
- Filtering logic must handle empty selection state - show all or show none requires decision
- parseSinnerName regex uses character class syntax that triggered linter warnings previously
- Static asset paths depend on Vite publicDir configuration which could break if changed

