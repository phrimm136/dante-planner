# Research: Refactor if-else Chain

## Overview of Codebase

- useEntityDetailData.ts contains two if-else chains in createDataQueryOptions and createI18nQueryOptions functions discriminating on entity type
- queryFn successfully captures type and id parameters from outer scope through JavaScript closures which is standard TanStack Query pattern
- Three entity types supported: identity, ego, egogift with different file path patterns for data and i18n
- EntityType discriminator uses lowercase egogift but actual file paths use camelCase egoGift creating naming inconsistency
- i18n path for egogift uses completely different name gift instead of egoGift
- Query key factory pattern already implemented with entityQueryKeys object following TanStack Query hierarchical key management best practices
- Conditional staleTime logic embedded in createDataQueryOptions: 30 days for egogift vs 7 days for identity and ego
- Generic type parameters use union types requiring explicit type narrowing by callers despite entity type determining exact return type
- Type assertions with as keyword used throughout queryFn implementations bypassing compile-time type checking
- Dynamic imports with template literals used for code splitting creating separate chunks per JSON file
- No existing configuration objects mapping entity types to behaviors found in codebase
- No other hooks use discriminated unions or type-based dispatch similar to useEntityDetailData
- Three separate legacy hooks exist for list data: useIdentityData, useEGOData, useEGOGiftData without type discrimination
- i18n query uses enabled parameter for dependent query pattern ensuring data loads before translations
- Hook receives id as string or undefined but creates queries even with empty string fallback
- Current implementation requires touching four locations when adding new entity type: type definition plus two if-else chains

## Codebase Structure

- Target file at frontend/src/hooks/useEntityDetailData.ts contains generic hook combining data and i18n queries
- Data files located at static/data/ with subdirectories: identity, EGO, egoGift using individual JSON files per entity
- i18n files at static/i18n/EN/ with subdirectories: identity, EGO, gift showing path inconsistency with data directory naming
- Type definitions split across frontend/src/types/: IdentityTypes.ts, EGOTypes.ts, EGOGiftTypes.ts
- Detail page components at frontend/src/routes/: IdentityDetailPage.tsx, EGODetailPage.tsx, EGOGiftDetailPage.tsx all using useEntityDetailData hook
- Static alias resolves to ../static from frontend directory enabling @static imports
- Entity-specific components organized in frontend/src/components/ subdirectories: identity, ego, egoGift
- Asset paths configuration in assetPaths.ts uses egoGift camelCase for image paths
- Requirements document at docs/05-code-refactoring/01-refine-data-fetch/requirements.md explicitly states goal to remove if-else chain
- Related task for undefined id handling indicates need to disable queries when id is undefined or empty

## Gotchas and Pitfalls

- Naming inconsistency between egogift type discriminator and egoGift file paths currently works but creates confusion requiring decision on standard
- i18n path for gifts uses gift subdirectory name completely different from egoGift data path breaking naming pattern consistency
- File system paths are case-sensitive requiring exact match with actual directory names: identity, EGO, egoGift
- Type assertions bypass runtime validation creating risk if JSON structure diverges from TypeScript interfaces without detection
- Empty string fallback for undefined id causes queries to execute with invalid id instead of being disabled
- Adding new entity type requires modifying multiple locations: EntityType union, both if-else chains, type parameters, tests
- Configuration object approach needs careful typing to maintain type safety with proper mapping between entity types and return types
- Dynamic imports are async requiring queryFn to be async function which is already correctly implemented
- StaleTime differences between entity types currently handled with inline conditional requiring extension point for future entities
- Query keys must remain serializable limiting options for complex configuration objects as keys
- Generic type parameters default to unions forcing callers to explicitly narrow even though entity type parameter determines exact type
- Runtime branching in queryFn cannot be completely eliminated but can be reduced to single object property lookup
- Closure pattern must be preserved as queryFn needs access to type and id from outer scope
- Code splitting behavior depends on Vite's handling of dynamic imports with template literals requiring consistent path patterns
- Related task about undefined id handling suggests need to add enabled parameter to data query not just i18n query
