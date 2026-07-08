# Implementation Plan: Copy UI from Identity and Refactor Components

## Clarifications Needed

No clarifications needed - requirements are clear

## Task Overview

Create a new `/ego` route that mirrors the Identity browser UI with same layout, filter categories (Sinner and Keyword), and search functionality. Extract all shared components (IconFilter, SearchBar, List pattern) to `/components/common/` directory to eliminate code duplication. Implement mock EGOCard component as placeholder while setting up real data infrastructure using `/static/data/EGO/{id}.json` and `/static/i18n/{lang}/EGO/{id}.json` following Identity data patterns.

## Steps to Implementation

1. **Move IconFilter to common directory**: Relocate IconFilter from components/identity/ to components/common/ since it will be shared by both Identity and EGO browsers
2. **Create generic SearchBar in common**: Extract search bar logic into common/SearchBar.tsx accepting placeholder prop, removing hardcoded i18n dependency
3. **Update Identity components to use common components**: Modify IdentitySinnerFilter, IdentityKeywordFilter, and IdentitySearchBar to import from common directory
4. **Create EGO types**: Define EGO, EGOData interfaces in types/EGOTypes.ts mirroring IdentityTypes.ts structure for consistency
5. **Create mock EGOCard component**: Build placeholder card in components/ego/EGOCard.tsx displaying temporary mock content in same card dimensions as IdentityCard
6. **Create EGO filter components**: Build EGOSinnerFilter and EGOKeywordFilter in components/ego/ wrapping common IconFilter with EGO-specific configuration
7. **Create EGOSearchBar wrapper**: Build thin wrapper in components/ego/ configuring common SearchBar with EGO i18n key
8. **Create EGOList component**: Implement components/ego/EGOList.tsx applying same three-layer filtering logic and rendering grid of EGOCard components
9. **Create EGOPage route**: Build routes/EGOPage.tsx with identical layout structure to IdentityPage using common and EGO-specific components
10. **Register EGO route in router**: Add egoRoute to router.tsx with path '/ego' following TanStack Router createRoute pattern

## Success Criteria

- IconFilter and SearchBar successfully moved to common directory and imported by both Identity and EGO without breaking functionality
- Identity browser continues working exactly as before after component extraction
- EGO route accessible at `/ego` with identical UI layout to Identity route
- Mock EGOCard displays placeholder content in same responsive grid layout (2-8 columns)
- EGO filter and search functionality working with Set-based state and debounce
- No code duplication between Identity and EGO for shared filter/search/list components
- Data structure prepared for future real EGO data in /static/data/EGO/ and /static/i18n/{lang}/EGO/
- All shared components properly exported from common directory with clean imports

## Assumptions Made

- EGO uses exact same filter categories as Identity (Sinner filter with OR logic, Keyword filter with AND logic)
- EGO search matches same fields as Identity (name, keyword, trait) with case-insensitive partial matching
- EGO data structure will mirror Identity structure when real data is added (id, name, star, sinner, traits, keywords fields)
- Mock EGOCard will be replaced later so minimal styling needed now, just matching card container dimensions
- Common components live in new `/components/common/` directory created during this task
