# Implementation Results: Planner Comment System

## What Was Done
- Implemented threaded comment CRUD with max depth 5 (arca.live style flattening)
- Added upvote-only voting with toggle behavior (soft-delete pattern)
- Created atomic increment/decrement counters to prevent race conditions
- Integrated user hard-delete flow (reassign comments to sentinel, soft-delete votes)
- Added moderator comment deletion endpoint
- Configured rate limiting (10 ops/min) for comment operations
- Fixed architecture review issues (reactivate signature, @Version, constants)

## Files Created
- `entity/CommentVoteType.java`
- `entity/PlannerCommentVoteId.java`
- `entity/PlannerComment.java`
- `entity/PlannerCommentVote.java`
- `repository/PlannerCommentRepository.java`
- `repository/PlannerCommentVoteRepository.java`
- `service/CommentService.java`
- `controller/CommentController.java`
- `dto/comment/CreateCommentRequest.java`
- `dto/comment/UpdateCommentRequest.java`
- `dto/comment/CommentResponse.java`
- `dto/comment/CommentVoteResponse.java`
- `exception/CommentNotFoundException.java`
- `exception/CommentForbiddenException.java`
- `util/CommentConstants.java`
- `db/migration/V015__add_planner_comments.sql`
- `db/migration/V016__add_comment_vote_version.sql`
- `test/.../CommentServiceTest.java`

## Files Modified
- `config/RateLimitConfig.java` - Added comment bucket
- `config/SecurityConfig.java` - Public GET comments endpoint
- `controller/ModerationController.java` - Moderator delete endpoint
- `exception/GlobalExceptionHandler.java` - New exception handlers
- `service/ModerationService.java` - Comment deletion method
- `service/UserAccountLifecycleService.java` - Comment/vote cleanup on user delete
- `resources/application.properties` - Rate limit config
- `test/.../ModerationServiceTest.java` - Added new dependency
- `test/.../UserAccountLifecycleServiceTest.java` - Added new dependencies

## Verification Results
- Checkpoint 5 (Migration): PASS
- Checkpoint 7 (Repos compile): PASS
- Checkpoint 14 (Service compiles): PASS
- Checkpoint 15 (Controller compiles): PASS
- Checkpoint 22 (Full compile): PASS
- Checkpoint 25 (Tests): PASS (CommentServiceTest, ModerationServiceTest, UserAccountLifecycleServiceTest)

## Issues & Resolutions
- User rejected Long comment ID → Kept Long (internal entity like User, not public like Planner)
- Missing rate limits on controller → Added Bucket4j rate limiting before completion
- Vote accumulation on sentinel user → Changed from reassign to soft-delete votes (avoids PK conflicts)
- Lambda variable not effectively final → Used requestedParentId for lambda capture
- reactivate() missing parameter → Added CommentVoteType param (consistent with PlannerVote)
- Missing @Version → Added optimistic locking + V016 migration
