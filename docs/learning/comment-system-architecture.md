# Comment System: Architecture, Issues, and Lessons

Lessons learned from implementing threaded comments with upvote voting for planners.

---

## Problem Context

Build a comment system for published planners with:
- Threaded replies (parent-child relationship)
- Max nesting depth of 5 (arca.live style flattening)
- Upvote-only voting (no downvotes, toggle behavior)
- Soft-delete pattern for comments and votes
- Deleted user handling via sentinel user (id=0)
- Rate limiting to prevent spam

---

## Architecture Patterns

### 1. Threaded Comments with Depth Flattening

**Pattern**: Store `parentCommentId` and `depth` on each comment. Flatten at max depth.

| Depth | Behavior |
|-------|----------|
| 0-4 | Reply creates child with depth+1 |
| 5 | Reply becomes sibling (uses parent's parentId, keeps depth=5) |

**Why depth flattening**: Prevents infinitely nested UI. At max depth, replies attach to grandparent, creating a "flat thread" visual pattern used by arca.live and similar forums.

**Service-layer logic required**: Schema only stores depth; flattening logic lives in CommentService.createComment() which adjusts `effectiveParentId` when parent.depth >= MAX_DEPTH.

---

### 2. Composite Key with Soft-Delete for Votes

**Pattern**: Composite PK (comment_id, user_id) with soft-delete via deletedAt column.

| State | deletedAt | Meaning |
|-------|-----------|---------|
| Active | null | User has upvoted |
| Removed | timestamp | User removed upvote (toggle off) |

**Why soft-delete over hard-delete**: Enables toggle behavior. User can upvote → remove → upvote again without creating duplicate rows. Reactivation clears deletedAt instead of INSERT.

**Persistable interface required**: Composite keys with pre-set IDs cause JPA to use merge() instead of INSERT. Implementing `Persistable<T>` with `isNew()` flag forces correct INSERT behavior.

---

### 3. Atomic Counters with Safety Guards

**Pattern**: Denormalized `upvoteCount` on comment with atomic increment/decrement queries.

**Increment query**: Standard atomic update
```sql
UPDATE planner_comments SET upvote_count = upvote_count + 1 WHERE id = :commentId
```

**Decrement query**: Guarded to prevent negative values
```sql
UPDATE planner_comments SET upvote_count = upvote_count - 1
WHERE id = :commentId AND upvote_count > 0
```

**Why guard on decrement**: Race conditions between soft-delete detection and counter update can cause mismatches. WHERE clause ensures count never goes negative even under concurrent load.

**Return value logging**: Check if `updated == 0` and log warning—indicates comment deleted mid-transaction or counter already at zero.

---

### 4. Sentinel User Pattern for Deleted Users

**Pattern**: When user hard-deletes account, reassign their comments to sentinel user (id=0).

| Entity | On User Delete | Reason |
|--------|----------------|--------|
| Comments | Reassign to sentinel | Preserve thread structure, anonymize author |
| Comment Votes | Soft-delete | Composite PK prevents reassignment conflicts |

**Why votes differ**: Composite key (comment_id, user_id) means sentinel user (id=0) might already have a vote record on that comment. Reassigning would cause PK violation. Soft-delete avoids this.

**Batch load optimization**: Filter sentinel user (id=0) from user batch loads. Handle null author in DTO mapping instead of wasting DB query for sentinel record.

---

## Issues Encountered

### Issue 1: Vote Reassignment PK Conflict

**Problem**: Plan was to reassign comment votes to sentinel user on hard-delete (like PlannerVote). But composite key (comment_id, user_id) breaks this—sentinel might already have voted.

**Discovery**: During implementation, realized uniqueness constraint on (comment_id, sentinel_id) would fail if sentinel already voted on that comment.

**Resolution**: Changed to soft-delete votes instead of reassignment. Vote record stays with original user_id but marked as deleted. Upholds counter integrity without PK conflicts.

---

### Issue 2: Missing @Version for Optimistic Locking

**Problem**: Architecture review flagged missing `@Version` on PlannerCommentVote. Concurrent toggle operations could cause lost updates.

**Discovery**: Post-implementation review compared to PlannerVote pattern which HAS @Version.

**Resolution**: Added V016 migration to add `version` column. Entity updated with @Version annotation. JPA now throws OptimisticLockException on concurrent modification.

---

### Issue 3: Missing Index on deleted_at

**Problem**: `findUpvotedCommentIds` query filters by `deletedAt IS NULL` but no index exists on that column.

**Discovery**: Performance review identified this as table-scan risk as vote table grows.

**Resolution**: Added V017 migration with `CREATE INDEX idx_comment_vote_deleted ON planner_comment_votes(deleted_at)`.

---

### Issue 4: Null Check Order in getComments

**Problem**: Code checked `!planner.getUser().getId().equals(currentUserId)` without first checking if `currentUserId` was null.

**Discovery**: Reliability review flagged potential NPE for unauthenticated users viewing unpublished planners.

**Resolution**: Changed to `currentUserId == null || !planner.getUser().getId().equals(currentUserId)` for explicit null-first check.

---

### Issue 5: Voting on Deleted Comments

**Problem**: Original design allowed upvoting deleted comments. Review questioned whether dead content should accumulate votes.

**Discovery**: Design assumption "deleted comments preserve structure" didn't address vote accumulation.

**Resolution**: Added check in toggleUpvote: `if (comment.isDeleted()) throw CommentForbiddenException`. Deleted comments are view-only for thread preservation, not engagement.

---

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Comment ID type | Long (auto-increment) | Internal entity like User, not public-facing like Planner (UUID) |
| Vote storage | Composite PK + soft-delete | Toggle behavior requires state preservation |
| Depth enforcement | Service layer | Schema stores depth; flattening is business logic |
| Rate limiting | 10 ops/min | Balance between spam prevention and usability |
| GET comments auth | Public for published | Match planner visibility; owner-only for unpublished |

---

## Migration Summary

| Migration | Purpose |
|-----------|---------|
| V015 | Create planner_comments and planner_comment_votes tables |
| V016 | Add version column for optimistic locking |
| V017 | Add deleted_at index for query performance |

---

## Files Created

**Entities**: CommentVoteType, PlannerComment, PlannerCommentVote, PlannerCommentVoteId
**Repositories**: PlannerCommentRepository, PlannerCommentVoteRepository
**Service**: CommentService
**Controller**: CommentController
**DTOs**: CreateCommentRequest, UpdateCommentRequest, CommentResponse, CommentVoteResponse
**Exceptions**: CommentNotFoundException, CommentForbiddenException
**Utility**: CommentConstants

---

## Lessons for Future Features

1. **Composite key entities always need @Version** - Add at creation, not retroactively
2. **Atomic decrements need WHERE guards** - Never assume service layer prevents negatives
3. **Soft-delete vs reassignment depends on PK structure** - Composite keys often require soft-delete
4. **Index columns used in WHERE filters** - Especially deletedAt for soft-delete queries
5. **Sentinel user handling spreads across layers** - Auth filter, batch loads, DTO mapping all need updates
