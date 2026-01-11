# Task: Remove Downvote from Planner Voting System

## Description
Completely remove downvote capability from the planner voting system. Users can only upvote planners. The system should:
- Remove DOWN from VoteType enum (only UP remains)
- Delete all existing downvote records via database migration
- Remove downvotes column from planner table
- Simplify net vote calculation to use only upvotes
- Remove downvote UI buttons from frontend
- Clean up all downvote-related i18n strings

This is a breaking change that completely eliminates downvoting, not just disabling it.

## Research
- VoteType enum usage across codebase (find all references)
- Planner entity downvotes field references
- PlannerVote entity and repository queries using VoteType.DOWN
- Vote count calculation logic in PlannerService
- Frontend vote UI and state management

## Scope
**Backend files to READ for context:**
- `backend/src/main/java/org/danteplanner/backend/entity/VoteType.java`
- `backend/src/main/java/org/danteplanner/backend/entity/Planner.java`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerVote.java`
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java`
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerVoteRepository.java`
- `backend/src/main/resources/db/migration/` (latest migration version)

**Frontend files to READ for context:**
- `frontend/src/components/plannerList/PlannerCardContextMenu.tsx`
- `frontend/src/hooks/usePlannerVote.ts`
- `frontend/src/types/PlannerListTypes.ts`

## Target Code Area
**Backend files to MODIFY:**
- `backend/src/main/java/org/danteplanner/backend/entity/VoteType.java` (remove DOWN enum value)
- `backend/src/main/java/org/danteplanner/backend/entity/Planner.java` (remove downvotes field)
- `backend/src/main/java/org/danteplanner/backend/dto/planner/VoteRequest.java` (update javadoc)
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` (simplify vote logic)
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerRepository.java` (remove downvote-related methods if any)

**Backend files to CREATE:**
- `backend/src/main/resources/db/migration/V0XX__remove_downvotes.sql` (migration to delete downvote data and column)

**Frontend files to MODIFY:**
- `frontend/src/components/plannerList/PlannerCardContextMenu.tsx` (remove downvote button)
- `frontend/src/types/PlannerListTypes.ts` (update VoteDirection type if needed)
- `static/i18n/CN/planner.json` (remove downvote keys)
- `static/i18n/EN/planner.json` (remove downvote keys)
- `static/i18n/JP/planner.json` (remove downvote keys)
- `static/i18n/KR/planner.json` (remove downvote keys)

## System Context (Senior Thinking)
- **Feature domain**: Planner Publishing & Voting
- **Core files**: PlannerController, PlannerService, PlannerVote entity
- **Cross-cutting concerns**: Vote immutability, recommendation threshold calculation

## Impact Analysis
**Files being modified:**
- VoteType.java (Low impact - enum simplification)
- Planner.java (Medium impact - entity used across planner features)
- PlannerService.java (High impact - core business logic)
- Database schema (High impact - irreversible data deletion)

**What depends on these:**
- Vote count displays in UI
- Recommendation algorithm (threshold calculation simplified)
- Historical vote data (will be deleted)

**Ripple effects:**
- All planners lose their downvote counts
- Net vote calculation becomes just upvote count
- Recommendation rankings will change

## Risk Assessment
**Edge cases:**
- Existing DOWN votes in database will be deleted (irreversible)
- Users who previously downvoted will have those votes erased
- Planners with many downvotes will rank higher after migration

**Backward compatibility:**
- **BREAKING**: Historical vote data deleted
- **BREAKING**: API clients expecting DOWN enum value will fail deserialization
- **BREAKING**: Database schema change requires migration

**Deployment:**
- Backend must deploy with migration (downtime or careful rollout)
- Frontend should deploy simultaneously to avoid DOWN enum errors
- Cannot rollback easily - vote data deletion is permanent

## Testing Guidelines

### Manual UI Testing
1. Navigate to `/planner/md/gesellschaft`
2. Open planner card context menu
3. Verify only upvote button appears
4. Vote on a planner
5. Verify vote count updates correctly
6. Refresh and verify vote persists

### Automated Functional Verification - Backend
- [ ] VoteType enum has only UP value
- [ ] Planner entity has no downvotes field
- [ ] Vote creation works with UP only
- [ ] Net vote calculation uses upvotes only
- [ ] Migration deletes all DOWN votes from planner_votes table
- [ ] Migration drops downvotes column from planner table

### Automated Functional Verification - Frontend
- [ ] Downvote button removed from UI
- [ ] Upvote flow works correctly
- [ ] No references to VoteDirection.DOWN

### Edge Cases
- [ ] Empty vote state handled correctly
- [ ] 409 conflict on duplicate upvote works

### Integration Points
- [ ] Vote endpoint accepts only UP votes
- [ ] SSE updates propagate correctly
- [ ] Recommendation threshold recalculated after migration
