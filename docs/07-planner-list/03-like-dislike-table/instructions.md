# Task: Soft Delete for Planner Votes Table

## Description

Implement soft delete mechanism for the `planner_votes` table to replace hard DELETE operations with UPDATE-based soft deletes. This improves performance and enables future analytics capabilities.

### Schema Changes

Add two columns to `planner_votes` table:
- `updated_at TIMESTAMP NULL` - tracks when vote was last modified (vote type change)
- `deleted_at TIMESTAMP NULL` - marks soft-deleted votes (NULL = active)

Add index `idx_planner_active_votes (planner_id, deleted_at)` for efficient active vote queries.

### Final Schema

```sql
CREATE TABLE planner_votes (
    user_id      BIGINT              NOT NULL,
    planner_id   CHAR(36)            NOT NULL,
    vote_type    ENUM('UP', 'DOWN')  NOT NULL,
    created_at   TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP           NULL,
    deleted_at   TIMESTAMP           NULL,

    PRIMARY KEY (user_id, planner_id),
    INDEX idx_planner_active_votes (planner_id, deleted_at),

    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (planner_id) REFERENCES planners(id)
);
```

### Vote Operations Behavior

| Action | Current Behavior | New Behavior |
|--------|------------------|--------------|
| **New vote** | INSERT | INSERT (same) |
| **Change vote (UP↔DOWN)** | UPDATE vote_type | UPDATE vote_type + updated_at |
| **Remove vote** | DELETE row | UPDATE deleted_at = NOW() |
| **Re-vote after removal** | INSERT new row | UPDATE deleted_at = NULL, vote_type = X (reactivate) |

### State Machine

```
┌─────────────┐
│  No Vote    │ (row doesn't exist OR deleted_at IS NOT NULL)
└──────┬──────┘
       │ castVote(UP)
       ▼
┌─────────────┐  castVote(DOWN)   ┌─────────────┐
│  UP Active  │ ◄───────────────► │ DOWN Active │
└──────┬──────┘   (updates both   └──────┬──────┘
       │          vote_type and          │
       │          updated_at)            │
       │ castVote(null)                  │ castVote(null)
       ▼                                 ▼
┌─────────────────────────────────────────────────┐
│  Soft Deleted  (deleted_at = NOW())             │
└─────────────────────────────────────────────────┘
       │
       │ castVote(UP or DOWN) — reactivate
       ▼
┌─────────────┐
│  Reactivated│ (deleted_at = NULL, vote_type = X, updated_at = NOW())
└─────────────┘
```

### Design Decisions (Confirmed)

1. **Soft delete over hard delete**: Preserves audit trail, avoids DELETE overhead
2. **Reactivate existing row**: On re-vote after removal, UPDATE the soft-deleted row instead of INSERT
3. **Keep denormalized counters**: Maintain `upvotes`/`downvotes` on Planner entity for O(1) reads
4. **No audit fields**: No `deleted_by` or `deletion_reason` - user-only control
5. **Update in place**: Vote type changes (UP→DOWN) UPDATE the row, no history tracking

## Research

- Current `PlannerVote.java` entity structure
- Current `castVote()` method in `PlannerService.java`
- Existing migration file naming pattern (V001, V002, etc.)
- `Planner.java` soft delete pattern (`deletedAt` field implementation)
- Atomic counter operations in `PlannerRepository.java`

## Scope

Read for context:
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerVote.java`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerVoteId.java`
- `backend/src/main/java/org/danteplanner/backend/entity/Planner.java` (soft delete pattern)
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerVoteRepository.java`
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` (castVote method)
- `backend/src/main/resources/db/migration/` (existing migrations)

## Target Code Area

### New Files
- `backend/src/main/resources/db/migration/V003__add_vote_soft_delete.sql`

### Modified Files
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerVote.java`
  - Add `updatedAt` field
  - Add `deletedAt` field
  - Add helper methods: `isDeleted()`, `softDelete()`, `reactivate(VoteType)`

- `backend/src/main/java/org/danteplanner/backend/repository/PlannerVoteRepository.java`
  - Add `findByUserIdAndPlannerIdAndDeletedAtIsNull()` method
  - Remove `deleteByUserIdAndPlannerId()` method (no longer needed)

- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java`
  - Refactor `castVote()` to handle soft delete and reactivation logic
  - Update query to find ANY row (active or deleted)
  - Implement reactivation for soft-deleted votes

### Test Files
- `backend/src/test/java/org/danteplanner/backend/service/PlannerServiceTest.java`
- `backend/src/test/java/org/danteplanner/backend/repository/PlannerVoteRepositoryTest.java` (if exists)

## Testing Guidelines

### Manual UI Testing

1. Navigate to a published planner page
2. Ensure you are logged in
3. Click the upvote button
4. Verify vote count increases by 1
5. Click the downvote button
6. Verify upvote count decreases by 1 AND downvote count increases by 1
7. Click the downvote button again (to remove vote)
8. Verify downvote count decreases by 1
9. Click the upvote button again (re-vote after removal)
10. Verify upvote count increases by 1
11. Refresh the page
12. Verify vote state persists correctly

### Automated Functional Verification

- [ ] New vote: Creates new row with `deleted_at = NULL`, `updated_at = NULL`
- [ ] Vote change: Updates `vote_type` and sets `updated_at = NOW()`
- [ ] Vote removal: Sets `deleted_at = NOW()`, does NOT delete row
- [ ] Re-vote after removal: Sets `deleted_at = NULL`, updates `vote_type`, sets `updated_at = NOW()`
- [ ] Same vote type: No-op, no database changes
- [ ] Counter accuracy: Upvote/downvote counts on Planner match active votes
- [ ] Query filtering: `findByUserIdAndPlannerIdAndDeletedAtIsNull()` excludes soft-deleted votes

### Edge Cases

- [ ] Double removal: Calling remove on already-removed vote is no-op
- [ ] Concurrent votes: Atomic counter operations prevent race conditions
- [ ] Null voteType: Treated as "remove vote" request
- [ ] Non-existent planner: Returns appropriate error (existing behavior)
- [ ] Unauthenticated user: Returns 401 (existing behavior)
- [ ] Vote on own planner: Should work (or be blocked - verify current behavior)

### Integration Points

- [ ] SSE notifications: Vote changes trigger real-time updates (if applicable)
- [ ] Rate limiting: Vote endpoint respects rate limits (existing behavior)
- [ ] Published planner list: Vote counts display correctly
- [ ] Recommended planners: Net vote calculation uses correct counts

## Implementation Sequence

1. **Migration** - `V003__add_vote_soft_delete.sql`
2. **Entity** - `PlannerVote.java` (add fields and methods)
3. **Repository** - `PlannerVoteRepository.java` (add new query, remove old delete)
4. **Service** - `PlannerService.castVote()` (refactor logic)
5. **Tests** - Update mocks and assertions
6. **Cleanup** - Remove deprecated methods

## Notes

- Keep all changes within single `@Transactional` boundary
- Use `Instant` type for timestamp fields (consistent with codebase)
- Follow existing `Planner.deletedAt` pattern for soft delete implementation
- Verify index usage with EXPLAIN on test data after migration
