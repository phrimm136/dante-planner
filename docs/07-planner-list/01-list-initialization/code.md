# Code Documentation: Planner List Backend Infrastructure

## What Was Done
- Migrated API path from `/api/planners` to `/api/planner/md` for all planner endpoints
- Created V002 Flyway migration adding published, upvotes, downvotes, selected_keywords columns
- Implemented PlannerVote entity with composite key (@IdClass pattern)
- Added KeywordSetConverter for MySQL SET column type conversion
- Created public endpoints: GET /published, GET /recommended (permitAll in SecurityConfig)
- Added owner-only publish toggle: PUT /{id}/publish with PlannerForbiddenException
- Implemented voting system: POST /{id}/vote with rate limiting

## Files Changed

### New Files
- `backend/src/main/resources/db/migration/V002__add_planner_publishing.sql`
- `backend/src/main/java/org/danteplanner/backend/converter/KeywordSetConverter.java`
- `backend/src/main/java/org/danteplanner/backend/entity/VoteType.java`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerVoteId.java`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerVote.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/VoteRequest.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/VoteResponse.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/PublicPlannerResponse.java`
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerVoteRepository.java`
- `backend/src/main/java/org/danteplanner/backend/exception/PlannerForbiddenException.java`
- `backend/src/test/java/org/danteplanner/backend/repository/PlannerVoteRepositoryTest.java`

### Modified Files
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java`
- `backend/src/main/java/org/danteplanner/backend/entity/Planner.java`
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerRepository.java`
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java`
- `backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/org/danteplanner/backend/config/SecurityConfig.java`
- `backend/src/test/java/org/danteplanner/backend/service/PlannerServiceTest.java`
- `backend/src/test/java/org/danteplanner/backend/controller/PlannerControllerTest.java`

## Verification Results
- Build: PASS (mvnw compile)
- Tests: PASS (204 tests, 0 failures)
- Code Review: ACCEPTABLE (code-architecture-reviewer)

## Issues & Resolutions
- Missing rate limiting on vote/publish → Added checkCrudLimit() calls
- SEC-001: Author name extraction may leak email → Tracked in docs/TODO.md (pending displayName spec)
- Race condition in vote counts → Deferred to high-scale optimization (@Version)
- Manual curl testing blocked → DB credentials issue; verified via unit tests instead
