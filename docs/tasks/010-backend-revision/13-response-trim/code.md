# Implementation Results: API Response Data Leakage Fix

## What Was Done

- Removed `userId` field from `PlannerResponse.java` (eliminated sequential ID exposure)
- Changed `UserDto.id` from `Long` to `UUID`, mapping to `User.publicId`
- Updated `UserService.toDto()` to use `getPublicId()` instead of `getId()`
- Deleted dead `CommentResponse.java` and removed unused imports from 3 files
- Removed `userId` from frontend `ServerPlannerResponseSchema` and `ServerPlannerResponse`
- Removed `userId` from `PlannerMetadata` types, schema, and sync adapter
- Fixed 15+ tests: added `publicId` to mock users, corrected response field assertions

## Files Changed

**Backend (Source):**
- `backend/src/main/java/org/danteplanner/backend/dto/planner/PlannerResponse.java`
- `backend/src/main/java/org/danteplanner/backend/dto/UserDto.java`
- `backend/src/main/java/org/danteplanner/backend/service/UserService.java`
- `backend/src/main/java/org/danteplanner/backend/controller/CommentController.java`
- `backend/src/main/java/org/danteplanner/backend/service/CommentService.java`
- `backend/src/main/java/org/danteplanner/backend/dto/comment/CommentResponse.java` (deleted)

**Backend (Tests):**
- `backend/src/test/java/org/danteplanner/backend/controller/AuthControllerTest.java`
- `backend/src/test/java/org/danteplanner/backend/controller/PlannerControllerTest.java`
- `backend/src/test/java/org/danteplanner/backend/controller/CommentControllerTest.java`
- `backend/src/test/java/org/danteplanner/backend/service/NotificationServiceTest.java`
- `backend/src/test/java/org/danteplanner/backend/service/UserSettingsServiceTest.java`
- `backend/src/test/java/org/danteplanner/backend/service/CommentServiceTest.java`

**Frontend:**
- `frontend/src/schemas/PlannerSchemas.ts`
- `frontend/src/types/PlannerTypes.ts`
- `frontend/src/hooks/usePlannerSyncAdapter.ts`
- `frontend/src/hooks/usePlannerSave.ts`

## Verification Results

- Backend compile: PASS
- Frontend typecheck: PASS (pre-existing unused var warnings remain)
- Backend tests: 707/717 pass (10 pre-existing failures unrelated to task)
- Code review: ACCEPTABLE after fixes

## Issues & Resolutions

- Test expected `$.userId` in response → Removed assertion (field no longer exists)
- Mock users missing `publicId` → Added `UUID.randomUUID()` to mock User builders
- VoteResponse field mismatch (`upvotes`/`userVote`) → Fixed to `upvoteCount`/`hasUpvoted`
- MAX_DEPTH flattening tests → Updated to match `MAX_DEPTH=MAX_VALUE` behavior
- `saveAndFlush` vs `save` mock mismatch → Corrected mock method names
