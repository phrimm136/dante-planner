# Implementation Plan: Refactor if-else Chain

## Clarifications Received

- Rename EntityType from 'egogift' to 'egoGift' for consistency with file paths - breaking change affecting all usage sites
- Rename i18n subdirectory from 'gift' to 'egoGift' matching data path pattern for consistency
- Undefined id issue deferred to separate task - keep existing empty string fallback pattern for now
- Entity configuration object remains internal to useEntityDetailData hook - not exported
- StaleTime values extracted to separate JSON configuration file in appropriate location

## Task Overview

Eliminate if-else chains in createDataQueryOptions and createI18nQueryOptions by introducing entity configuration object mapping entity types to file paths and settings. Resolve naming inconsistency between egogift type discriminator and egoGift file paths. Centralize all entity-specific configuration including data paths, i18n paths, and staleTime values for improved maintainability and extensibility.

## Steps to Implementation

1. **Create staleTime configuration JSON file**: Create queryConfig.json in appropriate location containing staleTime values per entity type with proper structure
2. **Rename EntityType from egogift to egoGift**: Update EntityType union type definition and all usage sites across detail pages and hooks
3. **Rename i18n subdirectory from gift to egoGift**: Rename physical directory in static/i18n/EN/ from gift to egoGift for consistency
4. **Create entity configuration constant**: Define ENTITY_CONFIG object mapping each entity type to dataPath and i18nPath properties with proper TypeScript typing
5. **Load staleTime from configuration file**: Import queryConfig.json and reference staleTime values in ENTITY_CONFIG
6. **Refactor createDataQueryOptions queryFn**: Replace if-else chain with configuration object property lookup to construct data file path
7. **Extract staleTime to configuration lookup**: Replace conditional staleTime logic with configuration object lookup using imported values
8. **Refactor createI18nQueryOptions queryFn**: Replace if-else chain with configuration object property lookup to construct i18n file path
9. **Add type safety to configuration**: Use satisfies operator or mapped types ensuring configuration object matches expected structure
10. **Build and verify**: Run TypeScript build confirming no compilation errors and all paths resolve correctly including renamed directories

## Success Criteria

- Both if-else chains in createDataQueryOptions and createI18nQueryOptions completely eliminated
- Single ENTITY_CONFIG object centralizes entity-specific path settings
- StaleTime values externalized to queryConfig.json for easy adjustment without code changes
- EntityType uses 'egoGift' consistently matching file path naming convention
- i18n subdirectory renamed from 'gift' to 'egoGift' establishing consistent naming pattern
- All three entity types load data and i18n from correct paths with proper staleTime values
- TypeScript build succeeds with no type errors after EntityType rename
- Configuration object properly typed preventing accidental misconfiguration
- Adding new entity type requires only configuration object entry plus JSON staleTime value instead of touching multiple if-else chains

## Assumptions Made

- Configuration object pattern preferred over factory functions or strategy pattern based on research recommendation for this use case
- EntityType renamed from 'egogift' to 'egoGift' accepting breaking change to establish consistent naming throughout codebase
- i18n subdirectory physically renamed to match data path pattern accepting file system change
- Empty string fallback for undefined id preserved deferring this issue to separate dedicated task
- Configuration object remains internal to useEntityDetailData hook not exported for external use per clarification
- Closure pattern for queryFn preserved as it follows TanStack Query best practices and works correctly
- Type assertions remain unchanged as runtime validation is separate refactoring task per docs structure
- StaleTime values preserved exactly: 30 days for egoGift, 7 days for identity and ego
- QueryConfig.json placed in static/config/ directory as appropriate location for query-related configuration
- All i18n language directories (EN, KR, JP) require gift to egoGift rename maintaining consistency across languages
