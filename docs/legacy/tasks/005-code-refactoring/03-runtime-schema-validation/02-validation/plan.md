# Implementation Plan: Runtime Validation Integration

## Clarifications Resolved

- Validation errors display detailed field-level messages to users providing specific information about what failed and where
- Partial validation failures in list data fail the entire query atomically while identifying the specific element number that caused the failure
- Validation error verbosity differs between development and production environments with more detailed technical information in development
- Performance monitoring follows time-proved standard methods for measuring validation overhead on large datasets
- Validation always required in all environments with no configurable skip option ensuring data integrity in development and production

## Task Overview

Integrate zod schema validation into useEntityDetailData and useEntityListData hooks to validate all JSON data loaded via dynamic imports before caching. Replace seven unsafe type assertions with runtime validation that throws descriptive errors on structural mismatches. Successfully validated data becomes type-safe without assertions through zod type inference. Validation failures trigger existing error handling flow with toast notifications requiring no component or error system changes.

## Steps to Implementation

1. **Create validation helper module**: Build lib/validation.ts with schema mapping functions to route entity types and data kinds to correct zod schemas plus error formatting utilities to convert zod validation errors into user-friendly messages
2. **Validate detail spec data**: Update createDataQueryOptions in useEntityDetailData to call schema validation on imported JSON before returning data removing type assertion and relying on zod inferred types
3. **Validate detail i18n data**: Update createI18nQueryOptions in useEntityDetailData to validate i18n JSON with appropriate i18n schemas matching entity type removing i18n type assertion
4. **Validate spec lists**: Update createSpecListQueryOptions in useEntityListData to validate spec list JSON using Record schema patterns that expect Record<string, EntityData> structure removing Record<string, any> assertion
5. **Validate name lists**: Update createI18nNameListQueryOptions in useEntityListData to validate i18n name list JSON using name list schemas removing unsafe type assertions
6. **Remove type assertions**: Systematically remove all seven type assertions from both hooks verifying TypeScript infers correct types from validated data returned by schemas
7. **Test validation success**: Verify valid JSON data passes validation and flows correctly to components with proper typing and no runtime errors
8. **Test validation failures**: Inject malformed JSON to verify validation catches structural errors displays formatted error messages in toast notifications and prevents bad data from reaching components
9. **Verify build integrity**: Run TypeScript compilation and production build ensuring no type errors after removing assertions and validation integration complete
10. **Document validation flow**: Update hook documentation explaining validation occurs in query functions error handling through existing system and type safety guarantees from runtime validation

## Success Criteria

- All seven type assertions removed from useEntityDetailData and useEntityListData hooks with no remaining unsafe casts
- Validation integrated into all four queryFn functions executing before data enters React Query cache
- Malformed JSON with missing fields incorrect types or additional fields triggers validation errors preventing cache pollution
- Valid JSON matching schema definitions passes validation and components receive fully type-safe data through zod inference
- Validation error messages formatted and displayed to users via toast notifications through existing QueryClient error handler
- TypeScript build succeeds with no type errors after removing assertions demonstrating zod type inference provides complete type safety
- Components unchanged requiring no modifications and continue functioning correctly with validated data
- Performance impact measured as negligible with validation completing under one millisecond per entity for normal loads

## Assumptions Made

- Validation errors include detailed field-level information with element numbers for list failures enabling precise debugging and issue identification
- Error formatting utilities check environment to provide verbose technical details in development and concise messages in production
- Schema strict mode constraints match actual JSON structure in static data files requiring no schema relaxation or data fixes
- Existing QueryClient global error handler with toast notifications sufficient for displaying validation errors without additional error UI
- Zod type inference from validated schemas produces types exactly matching TypeScript interface definitions enabling safe assertion removal
