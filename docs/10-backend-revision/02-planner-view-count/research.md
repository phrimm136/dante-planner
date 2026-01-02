# Research: Planner View Count with Deduplication

## Clarifications Resolved

| Ambiguity | Decision | Rationale |
|-----------|----------|-----------|
| IP extraction precedence | X-Forwarded-For → getRemoteAddr() | Standard proxy-aware approach |
| Daily cutoff timezone | UTC | Consistent across servers |
| Failed view attempts | 404 + silent (no logging) | Reduce log noise |
| INSERT + increment race | Accept best-effort | Rare edge case at 10k MAU |
| User-Agent truncation | 256 chars max | Per spec, use empty string if null |

---

## Spec-to-Code Mapping

| Requirement | Target File | Action |
|-------------|-------------|--------|
| Database schema | `V008__add_planner_views.sql` | CREATE TABLE with 3-column composite PK |
| View entity | `PlannerView.java` | NEW: @IdClass pattern, Persistable<T> |
| Composite key | `PlannerViewId.java` | NEW: Serializable, equals/hashCode |
| Repository | `PlannerViewRepository.java` | NEW: Custom query for upsert |
| Service method | `PlannerService.java` | ADD: recordView() with hash logic |
| REST endpoint | `PlannerController.java` | ADD: POST /{id}/view (public, 204) |
| Hashing util | `ViewerHashUtil.java` | NEW: SHA-256 for authenticated vs anonymous |

---

## Pattern Enforcement (MANDATORY)

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `PlannerView.java` | `PlannerVote.java`, `PlannerBookmark.java` | @IdClass, Persistable<T>, @PrePersist |
| `PlannerViewId.java` | `PlannerVoteId.java` | Serializable, equals/hashCode, getters |
| `PlannerViewRepository.java` | `PlannerVoteRepository.java` | @Query, @Modifying, @Param binding |

---

## Existing Utilities

| Category | Location | Status |
|----------|----------|--------|
| SHA-256 hashing | None exists | CREATE new ViewerHashUtil |
| IP extraction | RateLimitConfig (takes IP, doesn't extract) | CREATE extraction logic in controller |
| Atomic increment | PlannerRepository.incrementViewCount() | REUSE existing method |
| Composite key persistence | PlannerVote pattern | REUSE same @IdClass approach |

---

## Gap Analysis

**Currently Missing:**
- SHA-256 hashing utility for viewer deduplication
- PlannerView entity with LocalDate-based PK
- View recording service method
- View recording REST endpoint

**Needs Modification:**
- PlannerService: Add recordView() method
- PlannerController: Add POST /{id}/view endpoint

**Can Reuse:**
- PlannerRepository.incrementViewCount() - works atomically
- PlannerVote/PlannerVoteId pattern - directly adaptable
- Exception handling via GlobalExceptionHandler

---

## Testing Requirements

### Manual API Tests

| Test | Request | Expected |
|------|---------|----------|
| First view | POST /{id}/view with IP-A | 204, view_count = 1 |
| Duplicate same day | Same IP-A again | 204, view_count = 1 |
| Different viewer | POST with IP-B | 204, view_count = 2 |
| Unpublished planner | POST /{unpublished}/view | 404 |
| Missing User-Agent | POST without UA header | 204, uses empty string |
| Authenticated user | POST while logged in | 204, uses userId in hash |

### Unit Tests

- ViewerHashUtil: Hash determinism (same inputs → same hash)
- ViewerHashUtil: Different IPs produce different hashes
- ViewerHashUtil: Authenticated vs anonymous logic
- IP extraction: X-Forwarded-For parsing (single, multiple, malformed)
- User-Agent: Truncation at 256 chars, null handling

### Integration Tests

- recordView() new viewer → INSERT + increment
- recordView() duplicate → INSERT IGNORE, no increment
- recordView() unpublished → 404, no DB changes
- GET /published includes correct view_count
- Sort by "popular" uses viewCount

---

## Technical Constraints

| Constraint | Solution |
|------------|----------|
| No DELETE permission (INFRA-002) | Date-based PK, no cleanup needed |
| Composite key persistence | Use Persistable<T> interface like PlannerVote |
| Timezone consistency | Use LocalDate.now(ZoneOffset.UTC) |
| Race condition (INSERT + increment) | Best-effort acceptable at 10k MAU |

---

## Implementation Order

1. V008 migration (schema)
2. PlannerViewId (composite key)
3. PlannerView entity
4. PlannerViewRepository
5. ViewerHashUtil
6. PlannerService.recordView()
7. PlannerController POST endpoint
8. Unit tests
9. Integration tests
