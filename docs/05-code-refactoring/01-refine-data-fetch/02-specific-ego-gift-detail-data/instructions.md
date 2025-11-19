# Task: Specific EGO Gift Detail Data

## Description
- Refactor the `useEntityDetailData` to fetch `/static/data/egoGift/{id}/json` instead of extracting the data from the fetched `/static/data/EGOGiftSpecList.json`.

## Research
- Look at the pattern of detailed spec data fetch of id/ego.
- Since we changed the logic to fetch the detail data for the EGO gifts, and those data will be changed rarely (maybe half year?), we can set the stale value longer and create an invalidation trigger for refresh

## Scope
- `/static/data/egoGift/{id}.json`
- `/frontend/src/hooks/useEntityDetailData.ts`

## Target Code Area
- `/frontend/src/hooks/useEntityDetailData.ts`

## Testing Guidelines
- The fetch logic for EGO gifts have to be the same as that of EGO/identity.