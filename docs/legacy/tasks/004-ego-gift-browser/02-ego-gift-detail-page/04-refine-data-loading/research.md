# Research: Refine Data Loading with TanStack Query

## Overview of Codebase

- All three detail pages (Identity, EGO, EGOGift) use identical manual data loading pattern with useState + useEffect
- Two-phase loading: first load entity data JSON, then load i18n translations based on current language
- Manual loading state management via `isLoading` boolean flag
- Manual error handling with try/catch blocks logging to console
- No retry logic or caching between navigation
- TanStack Query v5 already installed and configured with QueryClient in main.tsx
- QueryClient defaults: 1min staleTime, 5min gcTime, 1 retry, no refetchOnWindowFocus
- useExampleQuery.ts exists as template showing basic useQuery pattern
- Existing hooks (useIdentityData, useEGOData, useEGOGiftData) use useMemo for static list data, not for detail page dynamic imports
- Detail pages use dynamic imports: `import(\`@static/data/{type}/{id}.json\`)` pattern
- i18n loading depends on both id parameter and i18n.language from react-i18next
- Common components LoadingState and ErrorState already exist for consistent UI
- All data files are static JSON imports, never change at runtime
- Identity/EGO load from individual files, EGOGift loads spec from specList then individual i18n file
- Current implementation has race condition potential between data and i18n effects

## Codebase Structure

- Detail pages live in `/frontend/src/routes/{EntityName}DetailPage.tsx`
- Custom hooks directory: `/frontend/src/hooks/` - place for new query hooks
- Query client config: `/frontend/src/lib/queryClient.ts` with global defaults
- Common components: `/frontend/src/components/common/` for LoadingState, ErrorState, DetailPageLayout
- Static data location: `@static/data/{type}/{id}.json` for entity data
- i18n data location: `@static/i18n/{lang}/{type}/{id}.json` for translations
- Type definitions: `/frontend/src/types/{Entity}Types.ts` for IdentityData, EGOData, EGOGiftSpec, etc.
- Naming convention for hooks: `use{Capability}.ts` per tech-brief.md
- All three pages follow same left/right column layout via DetailPageLayout component
- TanStack Router provides useParams hook for id extraction
- react-i18next provides i18n.language for current language detection

## Gotchas and Pitfalls

- TanStack Query v5 removed onError/onSuccess/onSettled callbacks from useQuery - must use global QueryCache handlers or useEffect
- Dynamic imports return modules with .default property - must access imported.default for JSON data
- Query keys must include ALL variables affecting query (id AND language) for proper cache invalidation
- Dependent queries need `enabled` option to prevent premature execution, but creates request waterfall
- isPending vs isFetching distinction: isPending = no data yet, isFetching = any request in progress including background refetch
- EGOGiftDetailPage loads from specList then individual i18n - different pattern than Identity/EGO which load individual data files
- Current code has two separate useEffects - can cause inconsistent states if one fails but other succeeds
- Static JSON imports should use `staleTime: Infinity` - data never changes at runtime
- Query key factory pattern maintains hierarchical structure: ['entity', id, 'i18n', lang] for easy invalidation
- Vite may need special handling for dynamic import paths with template literals - ensure glob patterns work
- Must handle case where id parameter is undefined from useParams (strict: false)
- Language change triggers i18n reload but data should remain cached
- Error states should differentiate between "data not found" vs "network/load error"
- Loading message should indicate which phase is loading (data vs translations)
