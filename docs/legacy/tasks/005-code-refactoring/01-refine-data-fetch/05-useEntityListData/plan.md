# Implementation Plan: useEntityListData Hook

## Clarifications Received

- ✅ Pages call hook and pass data to components as props for consistency across all entity types
- ✅ Hook follows useEntityDetailData pattern with entity type discriminator and configuration-driven approach
- ✅ Use dynamic imports for code splitting optimization following detail page pattern
- ✅ Filtering and sorting logic remains in components but review should note potential for hook-level centralization
- ✅ Migration replaces all three existing hooks simultaneously ensuring consistent behavior

## Task Overview

Create generic useEntityListData hook following useEntityDetailData pattern with entity type discriminator and configuration-driven approach. Hook will add React Query caching with proper staleTime for list data currently using static imports and useMemo. Standardize data pass-through pattern across all three list components eliminating current inconsistency between identity, ego, and egoGift implementations. Refactor existing list pages and components to use unified hook replacing useIdentityData, useEGOData, useEGOGiftData.

## Steps to Implementation

1. **Create hook file structure**: Add useEntityListData.ts in frontend/src/hooks alongside useEntityDetailData with entity type discriminator and list-specific configuration
2. **Define ENTITY_CONFIG for lists**: Map entity types to spec list paths, i18n name list paths, and staleTime values from queryConfig matching existing configuration
3. **Build query key factory**: Create entityListQueryKeys with hierarchical structure for spec lists, name lists, and combined list queries enabling proper invalidation
4. **Implement spec query function**: Create query options for loading spec list data from identitySpecList, EGOSpecList, EGOGiftSpecList using dynamic imports for code splitting
5. **Implement i18n query function**: Create query options for loading name list data from language-specific directories using dynamic imports with language parameter and proper caching
6. **Combine queries in hook**: Merge spec and i18n queries returning combined list data with loading and error states matching useEntityDetailData pattern
7. **Refactor list pages**: Update IdentityPage, EGOPage, EGOGiftPage to call useEntityListData and pass results as props standardizing data flow
8. **Update list components**: Modify IdentityList, EGOList, EGOGiftList to accept data as props removing internal hook calls for consistency
9. **Add TypeScript types**: Define generic type parameters for list item types ensuring type safety across all three entity implementations
10. **Remove deprecated hooks**: Delete useIdentityData, useEGOData, useEGOGiftData after successful migration verifying no remaining references

## Success Criteria

- Single generic useEntityListData hook handles identity, ego, egoGift with type safety and configuration-driven entity-specific behavior
- React Query caching enabled with staleTime matching queryConfig values providing deduplication and background refetch capabilities
- Consistent data pass-through pattern with pages calling hook and passing data to list components eliminating implementation inconsistency
- Dynamic imports enable code splitting for list data files reducing initial bundle size and improving load performance
- Query key structure enables proper invalidation and refetch patterns following hierarchical organization from useEntityDetailData
- All three list pages display correctly with filtering, searching, sorting functionality preserved without regression
- TypeScript compilation succeeds without type errors and generic parameters provide proper type inference for list items
- Existing hooks removed cleanly with no remaining imports or references in codebase after migration completion
- Query DevTools integration shows list queries with proper keys and cache management for debugging and monitoring

## Assumptions Made

- Following useEntityDetailData pattern with ENTITY_CONFIG mapping and query key factory for consistency with existing detail implementation
- Standardizing on props-based data flow where pages call hooks and pass data to components per clarification
- Using single combined query merging spec and i18n rather than two dependent queries since lists need both immediately
- Using dynamic imports for code splitting optimization matching detail page pattern per clarification
- Migrating all three entity types simultaneously in single implementation per clarification ensuring consistent behavior across application
- Filtering and sorting logic remains in list components per clarification maintaining separation of concerns with review noting centralization potential
