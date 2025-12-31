# Soft Delete with JPA Composite Keys

Lessons learned from implementing soft delete for the `planner_votes` table with composite primary key `(user_id, planner_id)`.

---

## Problem Context

Voting system needed soft delete to:
- Track vote history for analytics
- Enable vote reactivation without INSERT
- Maintain data integrity for audit trails

Challenge: JPA composite keys behave differently than auto-generated IDs.

---

## Key Patterns

### 1. Persistable Interface for Composite Keys

**Problem**: JPA's `save()` uses `merge()` when entity IDs are pre-set, which doesn't INSERT new entities correctly.

**Solution**: Implement `Persistable<T>` interface with manual `isNew` flag management.

**How it works**:
- `isNew = true` on construction → JPA uses `persist()` (INSERT)
- `@PostPersist` / `@PostLoad` sets `isNew = false` → subsequent saves use `merge()` (UPDATE)

**When to use**: Any entity with composite keys or manually-assigned IDs.

**Trade-off**: Adds lifecycle callback dependencies. If callbacks fail, persistence behavior becomes unpredictable.

---

### 2. Soft Delete Field Pattern

**Standard fields**:
- `deleted_at` (TIMESTAMP NULL) - When deleted, null = active
- `updated_at` (TIMESTAMP NULL) - Last modification time

**Helper methods**:
- `isDeleted()` - Check if soft deleted
- `softDelete()` - Set deleted_at to now
- `reactivate(newState)` - Clear deleted_at, set updated_at, apply new state

**Why not @SQLDelete**: Manual control allows state machine logic (reactivation with new vote type).

---

### 3. Repository Query Pattern

**Two query methods needed**:

| Method | Purpose |
|--------|---------|
| `findByUserIdAndPlannerId()` | Includes deleted (for reactivation check) |
| `findByUserIdAndPlannerIdAndDeletedAtIsNull()` | Active only (normal lookups) |

**Why both**: Reactivation requires finding deleted records; normal operations should ignore them.

---

### 4. Index Optimization for Soft Delete

**Wrong**: Index on `(planner_id, deleted_at)`
**Right**: Index on `(user_id, planner_id, deleted_at)`

**Reason**: B-tree indexes match left-to-right. Query `WHERE user_id = ? AND planner_id = ? AND deleted_at IS NULL` needs user_id as leftmost prefix.

**Rule**: Match index column order to WHERE clause predicate order.

---

### 5. Optimistic Locking for State Transitions

**Problem**: Concurrent vote removal and reactivation can race.

**Solution**: Add `@Version` field.

**How it works**: JPA adds `WHERE version = ?` to UPDATE statements. If another transaction modified the row, version mismatch throws `OptimisticLockException`.

**When to use**: Any entity with state machine transitions or high-concurrency updates.

---

### 6. State Machine Decomposition

**Vote states**:
1. No vote exists
2. Active vote (UP or DOWN)
3. Soft-deleted vote
4. Reactivated vote

**Service method structure**:
- `castVote()` - Public entry point, determines state
- `handleNewVote()` - No existing record
- `handleVoteRemoval()` - Soft delete active vote
- `handleVoteReactivation()` - Resurrect deleted vote
- `handleVoteTypeChange()` - Switch UP ↔ DOWN

**Benefit**: Each helper is testable independently, single responsibility.

---

## Migration Strategy

### Iterative vs Consolidated

**What happened**: V003 (columns) → V004 (index fix) → V005 (version column)

**Why problematic**: Three migrations for one feature increases deployment complexity.

**Better approach**: Design complete schema in planning phase:
1. Identify all columns needed (soft delete + audit + locking)
2. Design indexes based on actual query patterns
3. Single migration with everything

**When to consolidate**: Before production deployment, squash into single migration.

---

## Common Pitfalls

### 1. Forgetting Reactivation Path
Soft delete without reactivation logic means deleted records pile up. Plan UPDATE path, not just DELETE.

### 2. Index After-Thought
Designing indexes after queries exist leads to V004-style fixes. Query patterns first.

### 3. Missing Idempotency
Double-removal should be no-op, not error. Same-vote-type should be no-op. Test these explicitly.

### 4. Timestamp Manual Management
`@PrePersist` handles `created_at`, but soft delete needs manual `Instant.now()` for `deleted_at` and `updated_at`. No `@PreSoftDelete` exists.

### 5. Error Message Opacity
"Vote not found" vs "Vote already deleted" - both throw same exception. Consider stratifying for debugging.

---

## Transaction Pattern

**Method-level @Transactional** (industry standard):

| Method Type | Annotation |
|-------------|------------|
| Write (create, update, delete) | `@Transactional` |
| Read (get, list, search) | `@Transactional(readOnly = true)` |
| Private helpers | None (inherit from public caller) |

**Why not class-level**:
- Explicit intent per method
- Private methods don't accidentally get transactions
- Safer for refactoring

---

## Checklist for Future Soft Delete Features

- [ ] Add `deleted_at` and `updated_at` columns
- [ ] Add composite index covering query predicates
- [ ] Implement Persistable if composite/manual keys
- [ ] Add @Version for optimistic locking if concurrent updates expected
- [ ] Create both "include deleted" and "active only" repository methods
- [ ] Implement helper methods: isDeleted(), softDelete(), reactivate()
- [ ] Decompose service into state-specific handlers
- [ ] Test idempotency: double-removal, same-state operations
- [ ] Plan migration consolidation before production

---

## Files Reference

| File | Purpose |
|------|---------|
| `PlannerVote.java` | Entity with Persistable, @Version, soft delete fields |
| `PlannerVoteRepository.java` | Active and all-inclusive query methods |
| `PlannerService.castVote()` | State machine with helper decomposition |
| `V003-V005 migrations` | Schema evolution (consolidate before prod) |
