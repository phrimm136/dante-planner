# View Count: Architecture, Review, and Testing Lessons

Lessons learned from implementing planner view counting with daily deduplication.

---

## Problem Context

Track unique views per planner with:
- Daily deduplication (same viewer counts once per day)
- Privacy preservation (no raw IP storage)
- Atomic increment (concurrent-safe)
- GDPR compliance (one-way hashing)

---

## Architecture Patterns

### 1. Insert-Only Composite Keys (No Soft Delete)

**Pattern**: Use composite PK for natural deduplication without soft delete.

| Component | Purpose |
|-----------|---------|
| `planner_id` | Which planner |
| `viewer_hash` | Who viewed (SHA-256) |
| `view_date` | When (daily granularity) |

**Why composite PK works**: INSERT fails on duplicate (planner_id, viewer_hash, view_date), enabling O(1) deduplication check via database constraint.

**Why no soft delete**: Views are append-only analytics. No business case for "un-viewing."

**Trade-off vs auto-increment**:
- Composite PK: Natural deduplication, no cleanup needed
- Auto-increment: Requires UNIQUE constraint + separate dedup logic

---

### 2. Persistable Interface for Insert-Only Entities

**Problem**: JPA's `save()` uses `merge()` when IDs are pre-set, which UPDATEs instead of INSERTs.

**Solution**: Implement `Persistable<T>` with `isNew()` always returning true for insert-only entities.

**Key insight**: Unlike soft-delete entities (PlannerVote) that need state transitions, view records never update. Simpler `isNew()` implementation:

| Entity Type | isNew() Logic |
|-------------|---------------|
| Soft-delete (PlannerVote) | Flag + @PostPersist/@PostLoad callbacks |
| Insert-only (PlannerView) | Always true (or transient field set in constructor) |

---

### 3. SHA-256 Hashing for Privacy

**Hash components**:
- Authenticated: `userId + ":" + plannerId`
- Anonymous: `ip + ":" + userAgent + ":" + plannerId`

**Why include plannerId**: Prevents cross-planner correlation. Same user viewing different planners produces different hashes.

**Truncation rule**: User-Agent truncated to 256 chars BEFORE hashing to prevent collision from identical truncated suffixes.

---

### 4. Race Condition Handling

**Problem**: Two concurrent requests for same viewer:
1. Both check `existsById()` → false
2. Both attempt INSERT
3. One fails with DataIntegrityViolationException

**Solution**: Catch-and-ignore pattern:
```
try {
    viewRepository.save(view);
    plannerRepository.incrementViewCount(id);
} catch (DataIntegrityViolationException e) {
    // Duplicate - no-op (another request already inserted)
}
```

**Why not check-then-lock**: Over-engineering for analytics. Occasional double-increment at 10k MAU is acceptable.

---

## Code Review Insights

### 1. Parallel Review Domains

Running 5 specialized reviewers in parallel catches domain-specific issues:

| Reviewer | Focus | Example Finding |
|----------|-------|-----------------|
| Security | OWASP, injection | None (SHA-256 is safe) |
| Architecture | SOLID, DRY | Correctly followed PlannerVote pattern |
| Performance | N+1, complexity | Data retention needed for table growth |
| Reliability | Edge cases, tests | 29/29 tests pass |
| Consistency | Naming, patterns | Matches existing conventions |

**Key learning**: Security + Performance reviewers rarely conflict. Architecture + Consistency often overlap.

---

### 2. Spec-Driven Compliance Checklist

| Check | Question |
|-------|----------|
| Spec-to-Code Mapping | Did implementation match research.md targets? |
| Pattern Enforcement | Did new files copy from specified patterns? |
| Technical Constraints | Were constraints (no DELETE, UTC dates) respected? |
| Execution Order | Did phases follow dependency graph? |

**Result**: 95% spec compliance. Only gap: race condition semantics (silent vs logged) not specified.

---

### 3. Backlog Generation from Review

Review should produce actionable backlog items, not just "fix later":

| Item | Priority | Rationale |
|------|----------|-----------|
| Data retention job | Medium | Table grows ~1M rows/year |
| Rate limiting | Low | Prevent bot view inflation |
| Index optimization | Low | Monitor before optimizing |

**Anti-pattern**: "Needs improvement" without specific action items.

---

## Testing Challenges

### 1. Manual Verification Blocked by Missing Data

**Problem**: API tests require valid published planner in database. Test environment had malformed UUID.

**Symptoms**:
- GET /published returned data
- POST /{id}/view returned 404 for same ID
- UUID parsing failed silently

**Root cause**: Test data created with invalid UUID format that passed loose validation but failed strict parsing.

**Lesson**: Integration test setup must validate data before running functional tests.

---

### 2. Timezone Boundary Testing

**Problem**: Daily deduplication depends on UTC date boundaries. Local timezone can mask bugs.

**Test cases needed**:

| Scenario | Expected |
|----------|----------|
| View at 23:59 UTC | Counts for today |
| View at 00:01 UTC next day | New record, counts again |
| Same viewer, same UTC day | No increment |

**Lesson**: Include explicit timezone boundary tests in manual verification checklist.

---

### 3. Authentication Context in Tests

**Problem**: Anonymous vs authenticated requests produce different hashes.

**Test isolation required**:

| Test Type | Mock |
|-----------|------|
| Anonymous viewer | No auth context |
| Authenticated viewer | SecurityContext with userId |
| Same viewer twice | Consistent mock setup |

**Pitfall**: Forgetting to clear SecurityContext between tests causes hash inconsistency.

---

### 4. @Transactional Rollback Behavior

**Benefit**: Each test runs in isolation, rollback prevents data pollution.

**Gotcha**: Tests don't verify actual COMMIT behavior. Race condition tests may pass in @Transactional but fail in production.

**When to skip @Transactional**: Tests explicitly verifying concurrent behavior need real commits.

---

## Process Improvements

### 1. Spec Clarity for Operational Semantics

Specs should distinguish:

| Category | Example |
|----------|---------|
| Functional | "Same viewer counts once per day" |
| Operational | "Duplicate INSERT should be silent (no logging)" |

**Gap found**: Spec said "best-effort" but didn't specify silent vs logged for race conditions.

---

### 2. Test Data Validation Script

Before manual API testing:
```
1. List published planners
2. Validate each UUID is parseable
3. Verify at least one test candidate exists
4. Report blocking issues before test execution
```

---

### 3. Skeleton-First for Complex Patterns

When using @IdClass + Persistable pattern:
1. Create empty entity + ID class first
2. Verify compilation
3. Add repository
4. Verify Spring context loads
5. Then add business logic

**Why**: Persistable field-name matching is error-prone. Early verification prevents cascade failures.

---

## Checklist for Future View-Like Features

- [ ] Design composite PK for natural deduplication
- [ ] Implement Persistable with appropriate isNew() logic
- [ ] Create hashing utility with documented field ordering
- [ ] Handle race conditions (check → insert → catch duplicate)
- [ ] Add atomic increment for counter fields
- [ ] Include timezone boundary in test cases
- [ ] Validate test data before manual verification
- [ ] Plan data retention strategy for analytics tables
- [ ] Run parallel code reviews (security, architecture, performance, reliability, consistency)
- [ ] Generate actionable backlog from review findings

---

## Files Reference

| File | Pattern Demonstrated |
|------|---------------------|
| `PlannerView.java` | Insert-only Persistable entity |
| `PlannerViewId.java` | Composite key with LocalDate |
| `ViewerHashUtil.java` | Privacy-preserving hash utility |
| `PlannerService.recordView()` | Race-condition handling |
| `V008__add_planner_views.sql` | Composite PK migration |
| `ViewerHashUtilTest.java` | Determinism and uniqueness tests |
