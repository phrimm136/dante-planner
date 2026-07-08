# Research: Soft Delete for Planner Votes Table

## Spec Ambiguities

None found. Spec is comprehensive.

---

## Spec-to-Code Mapping

| Requirement | File | Change |
|-------------|------|--------|
| Add columns | `V003__add_vote_soft_delete.sql` (NEW) | Migration with `updated_at`, `deleted_at`, index |
| Soft delete fields | `PlannerVote.java` | Add `updatedAt`, `deletedAt` with `@Column` |
| Helper methods | `PlannerVote.java` | Add `isDeleted()`, `softDelete()`, `reactivate(VoteType)` |
| Active vote query | `PlannerVoteRepository.java` | Add `findByUserIdAndPlannerIdAndDeletedAtIsNull()` |
| Remove hard delete | `PlannerVoteRepository.java` | Remove `deleteByUserIdAndPlannerId()` |
| Soft delete + reactivation | `PlannerService.castVote()` | Refactor lines 336-403 |

---

## Spec-to-Pattern Mapping

| Requirement | Pattern Source | Location |
|-------------|----------------|----------|
| Soft delete impl | `Planner.java` | Lines 106-115: `isDeleted()`, `softDelete()` |
| Timestamp fields | `Instant` type | `Planner.java` lines 62-72, `PlannerVote.java` line 30 |
| @PrePersist hook | Auto-set timestamp | `PlannerVote.java` lines 41-44 |
| Atomic counters | Repository methods | `PlannerRepository.java` lines 115-156 |

---

## Pattern Enforcement (MANDATORY)

| New/Modified File | MUST Read First | Pattern to Copy |
|-------------------|-----------------|-----------------|
| `PlannerVote.java` | `Planner.java` | `isDeleted()`, `softDelete()`, `@Column`, `Instant` type |
| `V003__*.sql` | `V002__add_planner_publishing.sql` | Migration structure, ALTER TABLE, INDEX naming |
| `PlannerVoteRepository.java` | Same file (existing methods) | Query method signature pattern |
| `PlannerService.castVote()` | `PlannerService.deletePlanner()` | Soft delete: call `softDelete()`, then `save()` |

---

## Existing Utilities

| Category | Location | Functions Found |
|----------|----------|-----------------|
| Soft delete | `Planner.java` | `isDeleted()`, `softDelete()` |
| Vote queries | `PlannerVoteRepository.java` | `findByUserIdAndPlannerId()`, `deleteByUserIdAndPlannerId()` |
| Atomic counters | `PlannerRepository.java` | `incrementUpvotes()`, `decrementUpvotes()`, `incrementDownvotes()`, `decrementDownvotes()` |

---

## Gap Analysis

**Missing:**
- `@Transactional` on `castVote()` method (line 336)
- `updatedAt` field in `PlannerVote`
- `deletedAt` field in `PlannerVote`
- `findByUserIdAndPlannerIdAndDeletedAtIsNull()` repository method
- Migration file `V003__add_vote_soft_delete.sql`
- Helper methods in `PlannerVote`

**Needs Modification:**
- `PlannerService.castVote()`: Reactivation logic + set `updatedAt` on change
- Tests: Add soft delete scenarios

**Can Reuse:**
- `Instant` type and `@PrePersist` pattern
- Atomic counter methods from `PlannerRepository`
- Soft delete pattern from `Planner.java`
- Migration structure from `V002`

---

## Testing Requirements

### Manual Tests
1. Navigate to published planner (authenticated)
2. Click upvote → count +1
3. Click downvote → UP -1, DOWN +1
4. Click downvote again → DOWN -1 (soft delete)
5. Click upvote → UP +1 (reactivation)
6. Refresh → state persists

### Automated Tests

**Repository Tests:**
- Soft delete sets `deletedAt`, query returns empty
- Reactivation clears `deletedAt`, sets `updatedAt`
- `findByUserIdAndPlannerIdAndDeletedAtIsNull()` excludes soft-deleted

**Service Tests:**
- Re-vote after removal reactivates row (no new INSERT)
- Same vote type is no-op
- Vote change updates `updatedAt` and `vote_type`
- Double removal is idempotent
- Counter accuracy maintained

---

## Technical Constraints

| Constraint | Details |
|------------|---------|
| Composite Key | `(user_id, planner_id)` immutable |
| Transaction | `@Transactional` MUST wrap `castVote()` |
| Atomic Ops | Use `increment/decrementUpvotes/Downvotes` |
| Index | `idx_planner_active_votes (planner_id, deleted_at)` required |
| Timestamps | `Instant.now()` for `deletedAt`/`updatedAt` |
| Reactivation | UPDATE existing row, not INSERT new |
| @PrePersist | Only fires on INSERT; `updatedAt` set manually |

---

## Key Code Locations

| File | Lines | Changes |
|------|-------|---------|
| `PlannerVote.java` | 1-79 | Add 2 fields + 3 methods |
| `PlannerVoteRepository.java` | 15-34 | Remove hard delete, add soft query |
| `PlannerService.castVote()` | 336-403 | Refactor + add `@Transactional` |
| `V002__*.sql` | 18-29 | Template for V003 |
| `Planner.java` | 106-115 | Soft delete pattern reference |

---

## Implementation Sequence

1. Migration → V003 (columns + index)
2. Entity → PlannerVote (fields + methods)
3. Repository → Add new query, remove delete
4. Service → Refactor castVote()
5. Tests → Update with soft delete scenarios
