# Task: Planner List Viewer - Backend Infrastructure & Schema Migration

## Description

Create the backend infrastructure for a planner list viewer feature at `/planner/md`. This task focuses on database schema changes and backend API setup required before frontend implementation.

### Schema Changes

1. **Extract fields from content JSON to columns**:
   - `selectedKeywords`: New MySQL SET column with 17 fixed values (Combustion, Laceration, Vibration, Burst, Sinking, Breath, Charge, Slash, Penetrate, Hit, CRIMSON, SCARLET, AMBER, SHAMROCK, AZURE, INDIGO, VIOLET)
   - `title` and `category` already exist as columns - remove from content JSON to eliminate duplication

2. **Add publishing/voting fields to Planner entity**:
   - `published` (BOOLEAN, default false): Whether planner is publicly visible
   - `upvotes` (INT, default 0): Denormalized upvote count
   - `downvotes` (INT, default 0): Denormalized downvote count

3. **Create PlannerVote table** for tracking user votes:
   - `user_id`, `planner_id`, `vote_type` (UP/DOWN)
   - Unique constraint on (user_id, planner_id) - one vote per user per planner
   - Support vote changes (user can switch UP↔DOWN or remove vote)

### API Endpoints

1. **GET /api/planners/published**: Paginated public planner list
   - No authentication required (public)
   - Returns: id, title, category, selectedKeywords, authorName, upvotes, downvotes
   - Supports pagination (page, size)
   - Optional category filter

2. **GET /api/planners/recommended**: Recommended planners (net votes >= 10)
   - No authentication required (public)
   - Same response format as published
   - Ordered by net votes descending

3. **PUT /api/planners/{id}/publish**: Toggle published status
   - Authentication required, owner only
   - Guests cannot publish (must be logged in)

4. **POST /api/planners/{id}/vote**: Cast/change/remove vote
   - Authentication required (guests can view but not vote)
   - Body: `{ "voteType": "UP" | "DOWN" | null }`
   - null removes existing vote
   - Updates denormalized counts on Planner entity
   - Users can vote on their own planners

### Authorization Rules

| Endpoint | Guest | Logged In | Owner Only |
|----------|-------|-----------|------------|
| GET /published | Yes | Yes | - |
| GET /recommended | Yes | Yes | - |
| POST /{id}/vote | No | Yes | - |
| PUT /{id}/publish | - | - | Yes |

### Data Migration

Existing planners need migration:
1. Extract `selectedKeywords` from content JSON to new SET column
2. Remove `title`, `category`, `selectedKeywords` from content JSON (keep only in columns)
3. Set `published = false` for all existing planners

## Research

- Existing `Planner.java` entity structure and annotations
- Existing `PlannerService.java` patterns for CRUD operations
- `PlannerController.java` endpoint patterns
- `PlannerSummaryResponse.java` DTO structure
- Flyway migration file naming conventions in project
- MySQL SET type JPA mapping patterns
- Spring Data JPA custom query patterns for filtering

## Scope

Read for context:
- `backend/src/main/java/org/danteplanner/backend/entity/Planner.java`
- `backend/src/main/java/org/danteplanner/backend/entity/User.java`
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java`
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java`
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerRepository.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/*.java`
- `backend/src/main/resources/db/migration/` (existing migrations)
- `frontend/src/lib/constants.ts` (PLANNER_KEYWORDS definition)

## Target Code Area

### New Files
- `backend/src/main/resources/db/migration/V003__add_planner_publishing.sql`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerVote.java`
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerVoteRepository.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/PublicPlannerResponse.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/VoteRequest.java`

### Modified Files
- `backend/src/main/java/org/danteplanner/backend/entity/Planner.java` (add columns)
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` (add methods)
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java` (add endpoints)
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerRepository.java` (add queries)
- `backend/src/main/java/org/danteplanner/backend/dto/planner/PlannerSummaryResponse.java` (extend fields)

## Testing Guidelines

### Manual API Testing

1. Start the backend server
2. Create a test planner via existing POST /api/planners endpoint
3. Call PUT /api/planners/{id}/publish with owner authentication
4. Verify response indicates published=true
5. Call GET /api/planners/published without authentication
6. Verify the published planner appears in the list
7. Call POST /api/planners/{id}/vote with `{"voteType": "UP"}` as authenticated user
8. Verify response shows updated vote counts
9. Call GET /api/planners/published again
10. Verify upvotes count is incremented
11. Call POST /api/planners/{id}/vote with `{"voteType": "DOWN"}` (same user)
12. Verify vote changed from UP to DOWN, counts updated accordingly
13. Call POST /api/planners/{id}/vote with `{"voteType": null}`
14. Verify vote removed, counts decremented
15. Repeat vote attempt without authentication
16. Verify 401 Unauthorized response

### Automated Functional Verification

- [ ] Schema migration: New columns exist after running Flyway migration
- [ ] SET column: Accepts valid keyword combinations, rejects invalid
- [ ] Published endpoint: Returns only planners where published=true
- [ ] Recommended endpoint: Returns only planners where (upvotes - downvotes) >= 10
- [ ] Publish toggle: Only owner can change published status
- [ ] Vote uniqueness: Same user cannot have multiple votes on same planner
- [ ] Vote change: User can switch vote type or remove vote
- [ ] Denormalized counts: upvotes/downvotes on Planner match actual vote table counts
- [ ] Pagination: Published endpoint respects page/size parameters
- [ ] Category filter: Published endpoint filters by category when provided

### Edge Cases

- [ ] Unpublished access: Direct ID access to unpublished planner returns 404 for non-owners
- [ ] Self-vote: Owner can vote on their own planner
- [ ] Vote without auth: Returns 401, not 403
- [ ] Publish without auth: Returns 401
- [ ] Publish non-owner: Returns 403 Forbidden
- [ ] Vote on deleted planner: Returns 404
- [ ] Double vote same type: No-op, returns current state
- [ ] Negative net votes: Planner still shown in published list (not hidden)
- [ ] Empty published list: Returns empty page, not error
- [ ] Invalid vote type: Returns 400 Bad Request

### Integration Points

- [ ] Existing planner CRUD: Publishing/voting doesn't break existing create/update/delete
- [ ] Soft delete: Deleted planners excluded from published/recommended lists
- [ ] User deletion: CASCADE removes associated votes
- [ ] Planner deletion: CASCADE removes associated votes
- [ ] SSE notifications: Published/vote changes could trigger SSE events (future consideration)
