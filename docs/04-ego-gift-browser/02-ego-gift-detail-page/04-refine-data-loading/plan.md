# Implementation Plan: Refine Data Loading

## Clarifications Received

- **Error handling**: Use toast notifications for errors (global QueryCache callback)
- **Loading messages**: Use generic loading message (no phase differentiation)
- **EGOGift pattern**: Keep existing specList loading pattern
- **Hook architecture**: Create single generic hook for all entity types
- **Error handling approach**: Global error handling via QueryCache callbacks
- **Data freshness**: JSON data updates every 2 weeks - requires appropriate staleTime configuration

## Task Overview

Refactor all three detail pages (Identity, EGO, EGOGift) to use TanStack Query instead of manual useState/useEffect patterns. This consolidates data loading code, provides automatic caching, loading states, error handling, and retry logic. Each page will use parameterized query hooks that load entity data and i18n translations using dynamic imports with proper dependency management.

## Steps to Implementation

1. **Setup global error handling**: Add toast notification system and configure QueryCache with onError callback for global error handling
2. **Create generic query hook**: Build single reusable hook that accepts entity type, id, and language parameters for loading any entity data
3. **Create query key factory**: Define hierarchical query key structure that works for all entity types (Identity, EGO, EGOGift)
4. **Build generic data query options**: Create queryOptions function that handles dynamic imports for all entity types with appropriate staleTime (7 days)
5. **Build generic i18n query options**: Create dependent queryOptions function for translations with enabled conditions based on data loading success
6. **Refactor IdentityDetailPage**: Replace useState/useEffect with generic query hook, simplify to use isPending for loading states
7. **Refactor EGODetailPage**: Replace useState/useEffect with generic query hook, simplify to use isPending for loading states
8. **Refactor EGOGiftDetailPage**: Replace useState/useEffect with generic query hook, maintain specList loading pattern
9. **Remove manual state management**: Delete all useState and useEffect code related to data loading from all detail pages
10. **Test functionality**: Verify loading states, error toasts, language switching, caching, and data freshness work correctly

## Success Criteria

- All three detail pages use single generic TanStack Query hook for data loading
- Loading states managed automatically via useQuery isPending property with generic message
- Error states handled globally via QueryCache callback with toast notifications
- Data properly cached and reused when navigating back to previously viewed details
- Language changes trigger only i18n query refetch without reloading entity data
- Query keys include all parameters (entity type, id, language) for proper cache invalidation
- JSON data configured with 7-day staleTime to balance freshness with performance
- No manual useState for isLoading, data, or i18n in detail page components
- EGOGift maintains specList loading pattern within the generic hook structure

## Assumptions Made

- Keep LoadingState component for consistent UI, ErrorState as fallback for non-toast error display
- Create single generic hook named useEntityDetailData that handles all three entity types
- Use dependent queries with enabled option for sequential data then i18n loading
- Use 7-day staleTime for JSON data (half of 2-week update cycle) to balance freshness and performance
- Toast library (likely sonner or react-hot-toast) is already available or will be added
- Global error logging to console maintained for debugging alongside toast notifications
- EGOGift specList pattern integrated into generic hook via conditional logic based on entity type
- Global QueryClient configuration will be modified to add QueryCache onError callback
