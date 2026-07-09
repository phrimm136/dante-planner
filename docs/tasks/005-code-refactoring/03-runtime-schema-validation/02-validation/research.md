# Research: Runtime Validation Integration

## Overview of Codebase

- useEntityDetailData hook loads JSON detail data via dynamic imports for Identity EGO and EGOGift entities using entity configuration pattern
- useEntityListData hook loads spec lists and i18n name lists for all three entity types using similar dynamic import patterns
- Seven type assertions identified across both hooks with most critical being line 39 in useEntityListData using completely untyped Record<string, any>
- Type assertions at lines 45 and 64 in useEntityDetailData cast imported JSON to entity interfaces without runtime validation
- React Query queryFn is the correct integration point for validation occurring before data enters cache
- QueryClient already configured with global error handler using toast notifications at lib/queryClient.ts
- Error handling flow complete with ErrorBoundary for render errors and toast for query errors requiring no changes
- All twelve zod schemas already exist in schemas directory covering IdentityData EGOData EGOGiftData plus i18n variants and list schemas
- Schemas use strict validation with z.object().strict() rejecting additional fields and comprehensive enum validation for sins and ranks
- Components remain unchanged when validation integrated as hooks maintain same return types and query keys
- Current unsafe flow allows malformed JSON to pass type assertions causing runtime crashes in components with confusing error messages
- Validation errors will automatically trigger toast notifications through existing QueryClient error handler without additional configuration
- Entity configuration pattern in useEntityDetailData maps entity types to data paths schemas can be mapped similarly
- Hook signatures unchanged maintaining backward compatibility with existing component usage
- Successfully validated data provides type safety eliminating need for type assertions throughout codebase

## Codebase Structure

- useEntityDetailData located at frontend/src/hooks/useEntityDetailData.ts with createDataQueryOptions and createI18nQueryOptions functions
- useEntityListData located at frontend/src/hooks/useEntityListData.ts with createSpecListQueryOptions and createI18nNameListQueryOptions functions
- Schemas centralized in frontend/src/schemas/ with IdentitySchemas EGOSchemas EGOGiftSchemas SharedSchemas and index.ts for exports
- Entity configuration object in useEntityDetailData defines dataPath i18nPath and relativePath for each entity type
- JSON data files stored in static/data/ directory with entity-specific subdirectories for identity ego and egoGift
- I18n files stored in static/i18n/ with language subdirectories containing entity-specific i18n JSON files
- QueryClient configuration at frontend/src/lib/queryClient.ts with staleTime cacheTime and global error handler
- ErrorState component at frontend/src/components/common/ErrorState.tsx displays error messages from failed queries
- List pages use useEntityListData hook to fetch spec lists combining with i18n names to build display data
- Detail pages use useEntityDetailData hook to fetch both spec data and i18n data merging results for display
- Entity types defined as const enum with values identity ego and egoGift used throughout hooks for routing

## Gotchas and Pitfalls

- Type assertion at useEntityListData line 39 uses Record<string, any> completely bypassing TypeScript checking creating highest risk area
- Dynamic imports with template literals prevent TypeScript from validating JSON structure at compile time requiring runtime validation
- Schema selection must match entity type correctly requiring helper function to map entity type to appropriate schema
- List schemas use z.record() expecting Record<string, EntityData> structure different from single entity validation patterns
- I18n data structures differ from spec data structures requiring separate schema validation for each data kind
- Validation errors must provide clear context about which entity ID and data kind failed to aid debugging
- Performance consideration for large spec lists validating hundreds of entities simultaneously may need optimization
- Error messages from zod validation can be verbose requiring formatting helper to extract meaningful user-facing messages
- Removing type assertions after validation requires careful verification that zod inferred types match interface definitions
- Optional fields in schemas like corrosion skill in EGO must match actual JSON structure to avoid false validation failures
- Strict mode in schemas will reject JSON with additional undocumented fields requiring schema updates when game data structure changes
- Entity configuration pattern currently maps types to paths needs extension to map types to validation schemas
- React Query cache populated with validated data means validation only runs once per entity reducing performance impact
- Test data must match schema constraints exactly or tests will fail requiring JSON fixture updates
- Migration requires updating all seven type assertion locations atomically to maintain consistency across both hooks
