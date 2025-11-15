# Implementation Plan: Identity Browser UI Mock

## Task Overview

Create a new `/identity` page with layout placeholders matching the design mockup in `identity, ego overview.png`. The page contains a blue container (global body) with four sections inside: orange box (character filter), yellow box (keyword filter), green box (search bar), and red box (identity list). Components will be properly named for future agent discovery and use theme-consistent colors instead of literal mockup colors.

## Steps to Implementation

1. **Create placeholder components for identity feature**
   - Build IdentityCharacterFilter, IdentityKeywordFilter, IdentitySearchBar, and IdentityList components
   - Each component renders a themed placeholder with descriptive label indicating its purpose
   - Place components in `frontend/src/components/identity/` directory following feature organization pattern

2. **Create IdentityPage route component**
   - Build main page component with container matching blue box layout from mockup
   - Arrange components in correct positions: character filter and keyword filter on top row, search bar in second row, identity list as large section below
   - Use Tailwind grid or flex layout to match the spatial arrangement shown in design

3. **Register route in TanStack Router**
   - Add identityRoute definition to `frontend/src/lib/router.tsx`
   - Configure route with `/identity` path and IdentityPage component
   - Add route to routeTree children array for proper routing integration

4. **Add navigation link to Header**
   - Update Header component to include Identity navigation button
   - Follow existing nav button pattern with Link component and i18n
   - Position appropriately within existing navigation structure

5. **Add i18n translations for all languages**
   - Create translation keys for Identity page (title, description, component labels)
   - Add entries to all four language files (EN, JP, KR, CN) in `/static/i18n/`
   - Follow existing translation structure under `pages.identity` namespace

6. **Apply layout matching mockup structure**
   - Character filter (left) and keyword filter (middle-left) on top row with search bar (right) aligned
   - Identity list takes full width below the filter/search row with larger height
   - Use theme colors (bg-card, bg-muted, borders) instead of literal mockup colors
   - Add appropriate spacing, padding, and borders to distinguish sections

7. **Test page accessibility and navigation**
   - Verify route navigation works from Header link
   - Confirm layout matches mockup spatial arrangement
   - Validate all translation keys display properly across all four languages

## Success Criteria

- `/identity` route is accessible and renders without errors
- Layout matches spatial structure from mockup: top row with filters and search, large list below
- Four placeholder components positioned correctly per design mockup
- Navigation link appears in Header and routes to Identity page correctly
- All UI text uses i18n translations available in all four languages (EN/JP/KR/CN)
- Theme-consistent styling applied (light/dark mode compatible)
- Component names clearly indicate purpose for future implementation
- Page integrates seamlessly with GlobalLayout (Header/Footer visible)
- No console errors or warnings in browser
