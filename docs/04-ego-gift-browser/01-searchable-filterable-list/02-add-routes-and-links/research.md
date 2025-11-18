# Research: Add Routes and Links for EGO Gifts

## Overview of Codebase

- TanStack Router v1.135.2 handles all routing with type-safe navigation and route parameters
- Routes registered centrally in router.tsx using createRoute function with declarative API
- Each route specifies parent, path pattern, and component reference
- RouterProvider wraps entire app in main.tsx providing router context
- Route tree auto-generated in routeTree.gen.ts but source of truth is router.tsx
- Header component serves as primary navigation hub with centered menu layout
- Navigation uses Link component from TanStack Router for client-side transitions
- Translation system integrated with useTranslation hook for all navigation labels
- EGOGiftPage component already exists and follows established page patterns
- Browse pages use local state for filters and search with dedicated list components
- Detail pages use useParams hook to extract route parameters like dollar-sign id
- Dynamic imports load JSON data and i18n translations for detail pages

## Codebase Structure

- Route definitions live in frontend/src/lib/router.tsx as single source of truth
- Page components stored in frontend/src/routes/ directory with descriptive names
- Header navigation component at frontend/src/components/Header.tsx controls site-wide links
- Currently registered routes include home, info, identity, ego, planner, community, about
- Identity and EGO have both browse pages and parameterized detail pages
- Root layout minimal at routes/__root.tsx with actual routing logic in router.tsx
- Card components for Identity and EGO contain links to their respective detail pages
- Three-section Header layout: title left, navigation center, settings right
- Navigation menu currently shows info, identity, planner, community but omits ego and ego-gift
- Route parameters use dollar-sign prefix syntax following TanStack Router conventions

## Gotchas and Pitfalls

- EGOGiftPage component exists but not registered in router causing 404 errors
- Header navigation incomplete missing both ego and ego-gift links despite pages existing
- Must add route to router.tsx addChildren array or page remains inaccessible
- Translation keys required for navigation labels across all supported languages
- Route path should follow kebab-case convention like ego-gift not egoGift or EGOGift
- Generated routeTree.gen.ts should not be manually edited as it auto-regenerates
- Link component requires to prop with exact path string matching route definition
- Missing route registration causes TypeScript errors due to strict route typing
- Navigation order in Header affects user discovery so placement matters
- Detail page routes must specify parent relationship using getParentRoute callback
