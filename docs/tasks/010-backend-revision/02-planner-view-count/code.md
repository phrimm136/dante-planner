# Code Changes: Planner View Count

## Summary

| Metric | Value |
|--------|-------|
| New Files | 6 |
| Modified Files | 5 |
| Lines Added | ~970 |
| Test Coverage | 29 tests |

---

## New Files

### Phase 1: Data Layer

**V008__add_planner_views.sql** - Flyway migration
- Creates `planner_views` table with composite PK (planner_id, viewer_hash, view_date)
- Adds index on view_date for cleanup queries

**PlannerViewId.java** - Composite key class
- Implements Serializable for JPA composite key
- Fields: plannerId (UUID), viewerHash (String), viewDate (LocalDate)

**PlannerView.java** - Entity
- Uses @IdClass(PlannerViewId.class) pattern
- Implements Persistable<PlannerViewId> for insert-only behavior
- Tracks userAgent for analytics

### Phase 2: Infrastructure

**ViewerHashUtil.java** - Hashing utility
- SHA-256 hash of (userId or IP) + plannerId + userAgent
- Truncates userAgent to 256 chars
- Authenticated users use HMAC with userId, anonymous use IP

**PlannerViewRepository.java** - Repository
- JpaRepository for PlannerView entity
- existsById() for deduplication check

### Phase 5: Tests

**ViewerHashUtilTest.java** - Unit tests (8 tests)
- Hash determinism, uniqueness, truncation, auth vs anon

---

## Modified Files

### SecurityConfig.java
- Added `/api/planner/md/*/view` to public endpoints

### PlannerController.java
- Added `POST /{id}/view` endpoint
- Extracts IP from X-Forwarded-For or getRemoteAddr()
- Returns 204 No Content on success

### PlannerService.java
- Added `recordView(plannerId, viewerHash, userAgent)` method
- Verifies planner is published (404 if not)
- Checks existence before insert (deduplication)
- Catches DataIntegrityViolationException for race conditions
- Calls `incrementViewCount()` on new view only

### PlannerServiceTest.java
- Added 12 tests for recordView() scenarios
- New view increments, duplicate no-ops, unpublished 404

### PlannerControllerTest.java
- Added 9 integration tests for view endpoint
- 204 response, 404 unpublished, IP extraction, auth user

---

## Architecture Decisions

1. **Composite PK over auto-increment**: Enables natural deduplication via unique constraint
2. **Daily granularity**: view_date (not timestamp) allows same-day dedup, next-day new view
3. **SHA-256 hashing**: Privacy-preserving viewer identification
4. **Optimistic insert**: Check exists → insert → catch duplicate (handles race conditions)
5. **Atomic increment**: Uses existing `incrementViewCount()` repository method

---

## File Locations

```
backend/src/main/
├── java/org/danteplanner/backend/
│   ├── config/SecurityConfig.java (modified)
│   ├── controller/PlannerController.java (modified)
│   ├── entity/
│   │   ├── PlannerView.java (new)
│   │   └── PlannerViewId.java (new)
│   ├── repository/PlannerViewRepository.java (new)
│   ├── service/PlannerService.java (modified)
│   └── util/ViewerHashUtil.java (new)
└── resources/db/migration/
    └── V008__add_planner_views.sql (new)

backend/src/test/java/org/danteplanner/backend/
├── controller/PlannerControllerTest.java (modified)
├── service/PlannerServiceTest.java (modified)
└── util/ViewerHashUtilTest.java (new)
```

---

Documented: 2026-01-02
