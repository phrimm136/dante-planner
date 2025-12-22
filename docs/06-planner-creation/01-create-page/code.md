# Code Documentation: Create Planner Page

## What Was Done

- Created new page component at /planner/md/new with category dropdown, keyword multi-selector, and title input
- Added MD_CATEGORIES and PLANNER_KEYWORDS constants to constants.ts
- Added getPlannerKeywordIconPath() function to handle both sin and status effect icons
- Registered route in router.tsx with TanStack Router createRoute()
- Added i18n translation keys to all 4 language files (EN, JP, KR, CN)
- Created custom KeywordSelector component with click-to-expand panel showing icons
- Fixed pre-existing type errors in EGOGiftKeywordFilter and egoGiftSort (Keywordless -> None)

## Files Changed

- frontend/src/routes/PlannerMDNewPage.tsx (new)
- frontend/src/lib/constants.ts
- frontend/src/lib/assetPaths.ts
- frontend/src/lib/router.tsx
- frontend/src/components/egoGift/EGOGiftKeywordFilter.tsx
- frontend/src/lib/egoGiftSort.ts
- static/i18n/EN/common.json
- static/i18n/JP/common.json
- static/i18n/KR/common.json
- static/i18n/CN/common.json

## What Was Skipped

- Actual translations for JP/KR/CN (keys added with empty values)
- Icon verification for new keywords (Burst, Breath, Penetration, Hit)

## Testing Results

- Build: PASSED (yarn build succeeded)
- TypeScript: No errors after fixing Keywordless -> None references

## Issues & Resolutions

- KEYWORD_ORDER changed 'Keywordless' to 'None' - updated EGOGiftKeywordFilter and egoGiftSort
- Import for SINS added at top of assetPaths.ts to avoid duplicate import error
- KeywordSelector designed as inline component showing selected icons with expandable panel
- Category selector uses DropdownMenu component per user request
