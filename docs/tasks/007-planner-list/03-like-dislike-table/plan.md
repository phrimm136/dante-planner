# Plan: Soft Delete for Planner Votes Table

## Planning Gaps

None identified. Research is comprehensive.

---

## Execution Overview

Backend-only change: 1 new file, 3 modified files, 2 test files. Follows established soft delete pattern from `Planner.java`. Strict dependency order: migration → entity → repository → service → tests.

**Key Constraints:**
- Single `@Transactional` boundary for `castVote()`
- Reactivate existing rows (UPDATE), never INSERT for re-votes
- Composite key `(user_id, planner_id)` is immutable
- Use `Instant.now()` for timestamps

---

## Execution Order

### Phase 1: Data Layer

1. **V003__add_vote_soft_delete.sql** (NEW)
   - Depends on: none
   - Enables: F1 (soft delete columns exist)
   - Actions:
     - ADD COLUMN `updated_at TIMESTAMP NULL`
     - ADD COLUMN `deleted_at TIMESTAMP NULL`
     - CREATE INDEX `idx_planner_active_votes(planner_id, deleted_at)`
   - Pattern: V002 structure

2. **PlannerVote.java** (MODIFY)
   - Depends on: Step 1
   - Enables: F2, F3 (entity + helper methods)
   - Actions:
     - Add `updatedAt` field with `@Column`
     - Add `deletedAt` field with `@Column`
     - Add `isDeleted()` (copy from Planner.java:106-108)
     - Add `softDelete()` (copy from Planner.java:113-115)
     - Add `reactivate(VoteType)` (sets deletedAt=null, updatedAt=now, voteType)

### Phase 2: Repository Layer

3. **PlannerVoteRepository.java** (MODIFY)
   - Depends on: Step 2
   - Enables: F4, F5 (queries)
   - Actions:
     - Add `findByUserIdAndPlannerIdAndDeletedAtIsNull()`
     - Keep `findByUserIdAndPlannerId()` (needed for reactivation)
     - Remove `deleteByUserIdAndPlannerId()` (replaced by soft delete)

### Phase 3: Service Layer

4. **PlannerService.castVote()** (MODIFY lines 336-403)
   - Depends on: Step 3
   - Enables: F4-F7 (all core features)
   - Actions:
     - Verify `@Transactional` present
     - Remove vote: call `softDelete()` + `save()` instead of `delete()`
     - Re-vote: call `reactivate(voteType)` + `save()` if soft-deleted row exists
     - Vote change: set `updatedAt = Instant.now()`
     - Double removal: no-op if already deleted

### Phase 4: Tests

5. **PlannerVoteRepositoryTest.java** (MODIFY)
   - Depends on: Steps 1-3
   - Tests:
     - `testFindActiveVote_ExcludesSoftDeleted()`
     - `testSoftDeletedVote_StillFindableByFindAll()`
     - `testReactivateVote_ClearsDeletedAt()`

6. **PlannerServiceTest.java** (MODIFY CastVoteTests)
   - Depends on: Steps 1-4
   - Tests to add:
     - `castVote_RemoveVote_SoftDeletes()`
     - `castVote_ReVoteAfterRemoval_ReactivatesRow()`
     - `castVote_DoubleRemoval_IsIdempotent()`
     - `castVote_VoteChange_SetsUpdatedAt()`
   - Tests to modify:
     - `castVote_RemoveUpvote_DecrementsCount()` - expect `save()` not `delete()`
     - `castVote_RemoveDownvote_DecrementsCount()` - expect `save()` not `delete()`

---

## Verification Checkpoints

| After Step | Verify | Method |
|------------|--------|--------|
| 1 | Migration applied | `./mvnw flyway:migrate` |
| 2 | Entity compiles | `./mvnw compile` |
| 3 | Repository compiles | `./mvnw compile` |
| 4 | Service logic works | `./mvnw test` (existing tests pass) |
| 5-6 | Full coverage | `./mvnw test` (all tests pass) |
| Final | UI behavior | Manual testing checklist |

---

## Rollback Strategy

**Safe stopping points:**
- After Step 1: Migration reversible with rollback script
- After Step 2: Entity backward compatible (nullable columns)
- After Step 3: Repository can coexist with old service

**Rollback procedure:**
1. Failure at Step 4+: Revert service, keep entity/repo (backward compatible)
2. Failure at Step 2-3: Revert entity/repo, create rollback migration
3. Failure at Step 1: Create `V003.1__rollback_soft_delete.sql`

**Critical:** All schema changes via Flyway only.
