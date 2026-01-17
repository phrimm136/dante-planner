# Code: Planner Card Reconstruction

## What Was Done

**Planned (Completed):**
- Added `selectedKeywords?: string[]` to PlannerSummary type
- Added `RECOMMENDED_THRESHOLD = 10` constant, updated MD_CATEGORY_STYLES colors
- Extracted keywords in `listPlanners()` for MD planners with type guard
- Added star indicator to published cards for upvotes >= 10
- Reconstructed PersonalPlannerCard with keywords inline, text-sm title

**Beyond Plan:**
- Extracted PersonalPlannerCard to separate file (`components/plannerList/PersonalPlannerCard.tsx`)
- Changed keywords from text chips to icons using `getKeywordIconPath()`
- Added "Published" indicator state to personal cards
- Added left-click navigation to community cards (left=navigate, right=context menu)
- Backend: Added `selectedKeywords` to UpsertPlannerRequest and UpdatePlannerRequest
- Backend: Updated PlannerService create/update/upsert/import to persist keywords
- Frontend: Updated usePlannerSyncAdapter to send keywords in sync request
- Added Clock icon to published card date display (consistency with personal)
- Removed bookmark from context menu (fork provides superset)
- Renamed PlannerCard → PublishedPlannerCard for clarity

## Files Changed

**Frontend:**
- `types/PlannerTypes.ts` - Added selectedKeywords to PlannerSummary, UpsertPlannerRequest
- `lib/constants.ts` - Added RECOMMENDED_THRESHOLD, updated MD_CATEGORY_STYLES
- `hooks/usePlannerStorage.ts` - Extract keywords in listPlanners()
- `hooks/usePlannerSyncAdapter.ts` - Send keywords in sync request
- `components/plannerList/PersonalPlannerCard.tsx` - NEW: Extracted component
- `components/plannerList/PublishedPlannerCard.tsx` - Renamed from PlannerCard, consolidated layout
- `components/plannerList/PlannerCardContextMenu.tsx` - Left-click nav, removed bookmark
- `routes/PlannerMDPage.tsx` - Import PersonalPlannerCard
- `routes/PlannerMDGesellschaftPage.tsx` - Import PublishedPlannerCard

**Backend:**
- `dto/planner/UpsertPlannerRequest.java` - Added selectedKeywords field
- `dto/planner/UpdatePlannerRequest.java` - Added selectedKeywords field
- `service/PlannerService.java` - Set keywords in create/update/upsert/import

## Verification Results

- TypeScript compilation: PASS
- Backend Maven compile: PASS
- Manual testing: PASS

## Issues & Resolutions

- Keywords not showing on published cards → Backend not persisting keywords; fixed by adding to request DTOs
- Inconsistent keyword position → Consolidated to top row inline with floor badge
- Confusing component name → Renamed PlannerCard to PublishedPlannerCard
- Missing clock icon on published cards → Added for consistency with personal cards
