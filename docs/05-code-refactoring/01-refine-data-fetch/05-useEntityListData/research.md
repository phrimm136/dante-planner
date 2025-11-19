# Research: useEntityListData Hook

## Overview of Codebase

- Current list data loading uses static JSON imports with useMemo for merging spec and i18n data without React Query caching
- Three existing hooks useIdentityData, useEGOData, useEGOGiftData follow similar patterns but lack query optimization benefits
- useEntityDetailData provides reference pattern with entity type discriminator, ENTITY_CONFIG mapping, and query key factory
- List data stored as JSON records mapping id to spec metadata in identitySpecList, EGOSpecList, EGOGiftSpecList files
- i18n name lists stored separately per language as id-to-name mappings in identityNameList, EGONameList, EGOGiftNameList
- Detail data uses two dependent queries combining data and i18n with conditional execution based on success state
- List components return merged objects with id, name, and entity-specific metadata like star, rank, tier, keywords
- Query configuration defines staleTime per entity: identity and ego use 7 days, egoGift uses 30 days
- TanStack Query provides caching, deduplication, background refetch, and DevTools integration not currently used for lists
- Language support for lists uses fallback to ID when translation missing unlike detail pages with full i18n queries
- Filtering and searching logic currently embedded within list components using useMemo for performance
- Type system uses distinct interfaces for Identity, EGO, EGOGift list items with shared pattern of metadata fields
- Project uses TypeScript with strict typing, React 18, TanStack Query v5, and i18next for internationalization
- Static data files organized by entity type in frontend/static/data with parallel i18n structure per language

## Codebase Structure

- List hooks located in frontend/src/hooks with useIdentityData, useEGOData, useEGOGiftData as existing implementations
- List components in frontend/src/components organized by entity: identity/IdentityList, ego/EGOList, egoGift/EGOGiftList
- Page components in frontend/src/routes including IdentityPage, EGOPage, EGOGiftPage orchestrating list display
- Static list data in frontend/static/data as specList JSON files with record structure mapping ids to metadata
- i18n name lists in frontend/static/i18n/{language} directories with nameList JSON files per entity type
- useEntityDetailData in frontend/src/hooks provides reference pattern for entity configuration and query structure
- Query configuration centralized in frontend/static/config/queryConfig.json defining staleTime per entity type
- Type definitions distributed with IdentityTypes, EGOTypes, EGOGiftTypes defining list item interfaces separately
- List components accept filtering props like selectedSinners, selectedKeywords, searchQuery for client-side filtering
- New useEntityListData hook should be created in frontend/src/hooks alongside useEntityDetailData for consistency

## Gotchas and Pitfalls

- Data pass-through inconsistency where Identity and EGO list components call hooks internally but EGOGift receives props from page component
- List hooks currently perform static imports and useMemo merging missing React Query benefits like deduplication and caching
- List i18n only loads name lists without additional translation fields unlike detail pages with full i18n query patterns
- Mixed filtering patterns with some logic in components and different approaches across identity, ego, egoGift implementations
- Language fallback to ID in lists differs from detail pages with dependent i18n queries potentially confusing users
- Static imports prevent code splitting and dynamic loading optimization compared to detail pages using dynamic import syntax
- Entity type discriminator must match useEntityDetailData pattern using identity, ego, egoGift literal types for consistency
- ENTITY_CONFIG paths differ between list and detail with specList versus individual id files requiring careful mapping
- Query key structure for lists needs hierarchical design like detail keys to enable proper invalidation and refetch patterns
- List data structure as id-keyed records requires Object.entries mapping to array format for component consumption
- Type safety requires generic parameters matching useEntityDetailData pattern with TData type parameter for list item types
- Existing list hooks have different naming conventions between useIdentityData and useEGOGiftData requiring standardization
- Stale time configuration must align with queryConfig.json values maintaining 7 day default for identity and ego
- Migration from static imports to query-based approach requires refactoring all three list components simultaneously for consistency
- Testing coverage gap exists with no unit tests for list data loading requiring parallel test implementation with hook creation
