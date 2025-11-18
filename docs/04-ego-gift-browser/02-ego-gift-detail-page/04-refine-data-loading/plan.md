# Implementation Plan: Refine Data Loading

## Clarifications Needed

- Should error handling include toast notifications or continue using ErrorState component only?
- Should loading messages differentiate between "Loading data..." vs "Loading translations..."?
- Should EGOGift's specList loading pattern be refactored to match Identity/EGO (individual files)?
- Should we create a single generic hook for all entity types or separate hooks per entity?
- Should we add global error handling via QueryCache callbacks or keep local error handling?

## Task Overview

Refactor all three detail pages (Identity, EGO, EGOGift) to use TanStack Query instead of manual useState/useEffect patterns. This consolidates data loading code, provides automatic caching, loading states, error handling, and retry logic. Each page will use parameterized query hooks that load entity data and i18n translations using dynamic imports with proper dependency management.

## Steps to Implementation

1. **Create query key factories**: Define hierarchical query key structures for Identity, EGO, and EGOGift entities including detail and i18n keys
2. **Build data query options**: Create queryOptions functions that load entity data from dynamic imports with proper typing and staleTime configuration
3. **Build i18n query options**: Create dependent queryOptions functions that load translations based on language with enabled conditions
4. **Refactor IdentityDetailPage**: Replace useState/useEffect with useQuery hooks, update loading/error handling to use query states
5. **Refactor EGODetailPage**: Replace useState/useEffect with useQuery hooks, update loading/error handling to use query states
6. **Refactor EGOGiftDetailPage**: Replace useState/useEffect with useQuery hooks, handle specList pattern, update loading/error handling
7. **Remove manual state management**: Delete all useState and useEffect code related to data loading from detail pages
8. **Test functionality**: Verify loading states, error handling, language switching, and navigation caching work correctly
9. **Update error handling**: Ensure errors are properly caught, logged, and displayed using existing ErrorState component

## Success Criteria

- All three detail pages use TanStack Query hooks instead of useState/useEffect for data loading
- Loading states managed automatically via useQuery isPending/isFetching properties
- Error states handled via useQuery error property with ErrorState component display
- Data properly cached and reused when navigating back to previously viewed details
- Language changes trigger only i18n query refetch without reloading entity data
- Query keys include all parameters (id, language) for proper cache invalidation
- Static JSON data configured with staleTime: Infinity
- No manual useState for isLoading, data, or i18n in detail page components

## Assumptions Made

- Keep existing LoadingState and ErrorState components for consistent UI presentation
- Create separate hooks per entity type following existing useIdentityData/useEGOData pattern
- Use dependent queries with enabled option for sequential data then i18n loading
- Maintain current error logging to console for debugging purposes
- Follow naming convention: useIdentityDetailData, useEGODetailData, useEGOGiftDetailData hooks
- EGOGift will continue using specList pattern unless explicitly changed in clarifications
- Global QueryClient configuration remains unchanged (1min staleTime, 5min gcTime defaults)
