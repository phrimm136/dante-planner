# Research: Specific EGO Gift Detail Data

## Overview of Codebase

- useEntityDetailData currently loads egogift data from EGOGiftSpecList.json and extracts spec by id (lines 29-36)
- Identity and EGO load from individual files following pattern: import from @static/data/{type}/{id}.json (lines 24, 27)
- Individual egoGift files created in static/data/egoGift/ directory with only 2 files (0.json, 1.json)
- EGOGiftSpecList.json contains 3 entries (IDs 0, 1, 2) but only 2 individual files exist
- Individual egoGift files have incomplete structure: only category, tier, cost fields present
- EGOGiftSpec interface requires: category, keywords, themePack, cost, tier (missing keywords and themePack in individual files)
- SpecList entries include keywords array and themePack array fields not present in individual files
- useEGOGiftData hook for list page still depends on EGOGiftSpecList.json and will continue to need it
- Current staleTime for detail data is 7 days (604800000ms) as configured in createDataQueryOptions
- Global default staleTime is 1 minute (60000ms) configured in queryClient for non-detail queries
- Instructions mention setting longer staleTime since data changes rarely (maybe half year)
- i18n data for egogift already loads from individual files at @static/i18n/{language}/gift/{id}.json (lines 61-62)
- EGOGiftDetailPage already uses useEntityDetailData with 'egogift' type parameter
- Task requires making egogift data loading pattern identical to identity and ego patterns

## Codebase Structure

- Generic hook location: /frontend/src/hooks/useEntityDetailData.ts (lines 19-42 for data loading)
- Individual egoGift files: /static/data/egoGift/{id}.json (currently only 0.json and 1.json exist)
- Spec list location: /static/data/EGOGiftSpecList.json (still used by list page hook)
- List page hook: /frontend/src/hooks/useEGOGiftData.ts (depends on specList, not affected by this task)
- Type definitions: /frontend/src/types/EGOGiftTypes.ts (EGOGiftSpec interface at lines 4-10)
- Detail page using hook: /frontend/src/routes/EGOGiftDetailPage.tsx
- Query client config: /frontend/src/lib/queryClient.ts (global defaults at lines 12-18)
- Directory pattern matches identity and EGO: data/{entityType}/{id}.json

## Gotchas and Pitfalls

- Individual egoGift files missing required fields: keywords and themePack arrays not included
- Only 2 of 3 expected individual files exist (missing file for ID 2)
- Type mismatch between individual file structure and EGOGiftSpec interface will cause runtime errors
- SpecList structure includes cost field but individual files from git pull also have cost field creating confusion
- Changing egogift loading to individual files breaks nothing since list page uses separate hook (useEGOGiftData)
- Cannot remove EGOGiftSpecList.json as it's still required by useEGOGiftData for list page
- Individual files need migration or regeneration to include all required fields before implementation
- Instructions mention longer staleTime but don't specify exact value (half year mentioned = ~180 days)
- Build warning about dual imports of EGOGiftSpecList will disappear once detail loading switches to individual files
- No validation that individual files match EGOGiftSpec schema creating risk of silent type mismatches
- Empty string fallback in useEntityDetailData (line 80) will attempt to load nonexistent file when id undefined
- Task assumes individual files are complete and ready but they appear to be work in progress
