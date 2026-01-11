# Implementation Results: Remove Downvote from Planner Voting

## What Was Done
- Removed VoteType.DOWN enum value, making voting upvote-only
- Deleted downvotes column from planners table via V023 migration
- Removed all downvote-related methods from PlannerRepository (incrementDownvotes, decrementDownvotes)
- Simplified PlannerService.castVote() to single UP branch, removed toggle logic
- Updated all repository queries from net votes (upvotes - downvotes) to upvotes-only
- Changed VoteDirection frontend type from 'UP' | 'DOWN' to literal 'UP'
- Removed downvote UI button and handler from PlannerCardContextMenu component
- Cleaned i18n files across all 4 languages (EN, CN, JP, KR) with proper localizations
- Updated 6 test files removing downvote test cases and assertions
- Fixed VoteResponse field naming to match frontend expectations (upvoteCount, vote)

## Files Changed

### Backend (9 files)
- backend/src/main/resources/db/migration/V023__remove_downvotes.sql
- backend/src/main/java/org/danteplanner/backend/entity/VoteType.java
- backend/src/main/java/org/danteplanner/backend/entity/Planner.java
- backend/src/main/java/org/danteplanner/backend/repository/PlannerRepository.java
- backend/src/main/java/org/danteplanner/backend/service/PlannerService.java
- backend/src/main/java/org/danteplanner/backend/dto/planner/VoteResponse.java
- backend/src/main/java/org/danteplanner/backend/dto/planner/VoteRequest.java
- backend/src/main/java/org/danteplanner/backend/dto/planner/PublicPlannerResponse.java
- backend/src/main/java/org/danteplanner/backend/dto/planner/PlannerResponse.java

### Frontend (8 files)
- frontend/src/types/PlannerListTypes.ts
- frontend/src/schemas/PlannerListSchemas.ts
- frontend/src/hooks/usePlannerVote.ts
- frontend/src/components/plannerList/PlannerCardContextMenu.tsx
- static/i18n/EN/planner.json
- static/i18n/CN/planner.json
- static/i18n/JP/planner.json
- static/i18n/KR/planner.json

### Tests (6 files)
- backend/src/test/java/org/danteplanner/backend/service/PlannerServiceTest.java
- backend/src/test/java/org/danteplanner/backend/repository/PlannerRepositoryTest.java
- backend/src/test/java/org/danteplanner/backend/repository/PlannerVoteRepositoryTest.java
- backend/src/test/java/org/danteplanner/backend/controller/PlannerControllerTest.java
- backend/src/test/java/org/danteplanner/backend/integration/VoteNotificationFlowTest.java
- backend/src/test/java/org/danteplanner/backend/dto/planner/PublicPlannerResponseTest.java

## Verification Results
- Backend compilation: ✅ PASS (BUILD SUCCESS)
- Frontend compilation: ✅ PASS (Done in 0.14s)
- PlannerServiceTest: ✅ PASS (62 tests passing)
- Code review: ✅ ACCEPTABLE (after fixes)
- Migration safety: ✅ Added IF EXISTS for index drop

## Issues & Resolutions
- Issue: Frontend VoteDirectionSchema still validated 'DOWN' after backend removal
  → Resolution: Changed from z.enum(['UP', 'DOWN']) to z.literal('UP')
- Issue: VoteResponse field mismatch (backend: upvotes/userVote vs frontend: upvoteCount/vote)
  → Resolution: Aligned backend field names to match frontend expectations
- Issue: trySetRecommendedNotified() query failed with CURRENT_TIMESTAMP type mismatch (Timestamp vs Instant)
  → Resolution: Changed to native SQL query for MySQL compatibility
- Issue: PlannerServiceTest referenced netBefore/netAfter variables that no longer existed
  → Resolution: Renamed all occurrences to upvotesBefore/upvotesAfter for clarity
- Issue: Migration index drop would fail if index doesn't exist
  → Resolution: Added IF EXISTS clause to DROP INDEX statement
- Issue: i18n files contained leftover messageDown keys
  → Resolution: Removed from all 4 language files (EN, CN, JP, KR)
