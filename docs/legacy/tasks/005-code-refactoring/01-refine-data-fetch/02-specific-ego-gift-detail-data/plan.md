# Implementation Plan: Specific EGO Gift Detail Data

## Clarifications Received

- EGOGiftData interface matches current individual files structure with fields: category, tier, cost only
- EGOGiftSpec no longer uses cost field - specList structure differs from individual files
- Individual files intentionally minimal - keywords and themePack not needed for detail page, only for list filtering
- StaleTime set to one month (30 days = 2592000000ms) instead of half year
- EGO gift data already migrated to individual files - only need to implement loading mechanism for existing files

## Task Overview

Refactor useEntityDetailData hook to load EGO gift data from individual files at /static/data/egoGift/{id}.json instead of extracting from EGOGiftSpecList.json. Create new EGOGiftData interface to type individual file structure. Update staleTime to longer value since data changes rarely. This aligns egogift loading pattern with identity and ego patterns while eliminating build warning about dual imports of specList.

## Steps to Implementation

1. **Create EGOGiftData interface**: Define new TypeScript interface in EGOGiftTypes.ts with category, tier, and cost fields matching current individual file structure
2. **Modify createDataQueryOptions**: Replace egogift branch to import from individual file path at @static/data/egoGift/{id}.json following identity/ego pattern instead of loading entire specList
3. **Update type assertions**: Change type assertion from EGOGiftSpec to EGOGiftData in egogift data loading branch
4. **Configure one-month staleTime**: Set staleTime to 2592000000ms (30 days) for egogift entity type specifically instead of 7-day default
5. **Update useEntityDetailData generics**: Modify generic type constraint to accept EGOGiftData in addition to existing IdentityData and EGOData types for proper type inference
6. **Update EGOGiftDetailPage**: Change type parameter from EGOGiftSpec to EGOGiftData in useEntityDetailData hook call
7. **Test data loading**: Verify individual files load correctly for all migrated gifts, error handling works properly, and build warning about dual imports is resolved
8. **Verify list page unaffected**: Confirm useEGOGiftData and EGOGiftPage still function correctly using EGOGiftSpecList without cost field

## Success Criteria

- useEntityDetailData loads egogift data from individual files at /static/data/egoGift/{id}.json instead of EGOGiftSpecList.json
- EGOGiftData interface created with category, tier, and cost fields matching individual file structure
- StaleTime for egogift set to one month (2592000000ms / 30 days) reflecting monthly data update cycle
- Loading pattern for egogift identical to identity and ego patterns using dynamic import of individual files
- Build warning about dual import of EGOGiftSpecList.json eliminated from detail page loading
- List page (useEGOGiftData) continues functioning without changes using EGOGiftSpecList without cost field
- All migrated gift IDs load successfully from individual files with correct data
- TypeScript compilation succeeds with proper type inference using EGOGiftData type and no type errors

## Assumptions Made

- EGOGiftData has minimal structure (category, tier, cost) since keywords and themePack only needed for list filtering not detail display
- EGOGiftSpec used by list page no longer includes cost field - structural divergence between list and detail data is intentional
- One-month staleTime (30 days) appropriate for monthly data update cycle balancing freshness with performance
- Keeping EGOGiftSpecList.json for list page is correct approach since list page uses separate hook with different data needs
- Individual file naming follows numeric ID pattern consistent with identity and EGO (0.json, 1.json, etc.)
- Error handling for missing individual files follows same pattern as identity and ego (throw error caught by QueryCache with toast notification)
