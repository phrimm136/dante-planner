# Research: Disable Query for Undefined ID

## Overview of Codebase

- useEntityDetailData hook accepts id parameter typed as string or undefined with empty string fallback pattern using id or empty string operator
- Data query executes even with empty string causing failed import attempts for paths like @static/data/identity/.json
- i18n query already uses enabled parameter checking dataQuery.isSuccess and !!id demonstrating correct pattern for conditional query execution
- All three detail pages use useParams with strict false option making TypeScript infer id as string or undefined for safety
- Routes defined with required dollar-id parameter not optional syntax indicating id should always exist for valid navigation
- All navigation to detail pages uses type-safe TanStack Router Links from Card components guaranteeing valid id strings
- Non-null assertions used throughout pages when passing id to child components indicating developers expect id to exist
- Undefined id appears to be defensive TypeScript safety measure rather than legitimate use case for missing route parameter
- Failed fetch attempts with empty string create error entries in console and waste resources attempting impossible imports
- Current behavior results in isError true state when id undefined masking true nature of problem as missing parameter rather than data fetch failure
- Pages check isError or missing data showing ErrorState component for both legitimate fetch failures and missing id scenarios
- Research document from previous task explicitly identifies undefined id handling as issue requiring enabled parameter addition to data query
- TanStack Query enabled false prevents automatic fetching making query pending with idle fetch status instead of attempting doomed request
- i18n query demonstrates dependent query pattern waiting for data success and valid id before executing establishing precedent for conditional execution
- Empty string fallback pattern creates cache entries for invalid empty id key polluting query cache with meaningless failed results

## Codebase Structure

- Target file at frontend/src/hooks/useEntityDetailData.ts contains generic hook with data and i18n query logic
- Three detail page components at frontend/src/routes/: IdentityDetailPage, EGODetailPage, EGOGiftDetailPage all calling useEntityDetailData hook
- Card components at frontend/src/components/ subdirectories use TanStack Router Link with type-safe params ensuring valid id always provided during navigation
- Route definitions in router.tsx specify required id parameter using dollar-id syntax not optional curly-dash-dollar-id syntax
- Static data files at static/data/ organized by entity type subdirectories with individual JSON files named by id
- i18n files at static/i18n/ with language subdirectories then entity type subdirectories containing translation files named by id
- Entity configuration in ENTITY_CONFIG constant maps types to dataPath and i18nPath used for dynamic import path construction
- createDataQueryOptions and createI18nQueryOptions factory functions build queryOptions objects with queryKey queryFn and staleTime
- Pages use early return pattern checking isPending first then isError or missing data showing LoadingState or ErrorState components
- Non-null assertions appear in JSX when passing id to child components like GiftImage id={id!} indicating expectation of defined value
- Documentation at docs/05-code-refactoring/01-refine-data-fetch/ contains research findings identifying undefined id as known issue

## Gotchas and Pitfalls

- useParams with strict false necessary for type flexibility but creates TypeScript typing making all params potentially undefined requiring defensive handling
- Empty string fallback satisfies TypeScript but causes runtime fetch attempts with invalid paths resulting in import errors rather than preventing query
- Data query lacks enabled parameter while i18n query has it creating inconsistency in how undefined id handled between related queries
- Adding enabled parameter changes query state from isError true to isPending true with idle fetch status potentially affecting components checking specific states
- Pages using id non-null assertion throughout assume id exists but lack explicit validation before hook call creating implicit dependency on routing correctness
- TanStack Router Link components guarantee valid id during normal navigation but direct URL entry or external links could provide undefined or malformed id
- Cache keys using empty string for undefined id create meaningless cache entries that persist storing failed query results
- Changing behavior from failed query to disabled query affects error boundary behavior and debugging experience showing different states for missing parameter
- Query key factory entityQueryKeys.detail uses provided id value meaning empty string keys persist in cache across component remounts
- Type narrowing after explicit id check in pages would eliminate need for non-null assertions but requires adding validation logic to all three pages
- TanStack Router typed from option could eliminate strict false need providing string type directly but requires refactoring useParams call sites
- Timing during route transitions might cause temporary undefined id state during navigation resolved when route params fully loaded
- Error messages shown to users currently generic for both fetch failures and missing id not distinguishing between data errors and routing errors
- Console errors from failed empty string imports pollute development logs making real errors harder to identify
- Future code expecting cached error state for empty id might break when query no longer executes with disabled enabled parameter
