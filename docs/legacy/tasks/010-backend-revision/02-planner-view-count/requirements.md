# Task: Implement Planner View Count with Deduplication

## Description

Add view counting functionality to the planner feature with proper deduplication to prevent artificial inflation. The system tracks unique views per planner with a daily deduplication window.

### Requirements

**View Recording:**
- Record a view when a user (authenticated or anonymous) views a published planner
- Deduplicate views using a hashed identifier:
  - Authenticated users: `SHA-256(user_id + planner_id)`
  - Anonymous users: `SHA-256(IP + User-Agent + planner_id)`
- Same viewer can count again the next day (daily deduplication)
- Only published planners should have their views counted

**Privacy & Security:**
- Never store raw IP addresses (GDPR compliance)
- Use one-way hash to prevent reverse engineering of viewer identity
- Hash includes planner_id to prevent cross-planner correlation

**View Count Display:**
- The `view_count` column on `planners` table already exists
- Increment atomically when a new unique view is recorded
- Display in `PublicPlannerResponse` (already included)

**No Cleanup Required:**
- Date-based primary key eliminates need for scheduled cleanup
- Compatible with INFRA-002 (app_user has no DELETE permission)
- See TODO.md UX-002 for future cleanup strategy if table grows large

### Future Expansion: Multi-Planner-Type Support

**Terminology:**

| Term | Scope | Examples |
|------|-------|----------|
| **Planner Type** | Top-level game mode (NOT YET IMPLEMENTED) | MD (Mirror Dungeon), RR (Refraction Railway) |
| **Category** | Secondary grouping within a type | MD floors: 5F, 10F, 15F |

**Current State:**
- The `planners` table has NO `planner_type` column yet
- The `category` column uses `MDCategory` enum (MD floor levels)
- All current planners are implicitly MD type

**UUID Uniqueness:**
UUIDs are globally unique (2^122 combinations). Different planner types (MD, RR) will have different UUIDs with no collision risk. The `planner_views` table needs only `planner_id` - no `planner_type` column required.

**When adding new planner types (separate task):**
1. Add `planner_type` column to `planners` table
2. Make `category` type-specific or polymorphic
3. **View tracking requires NO changes** - works via `planner_id` FK

## Research

Before implementation, investigate:

1. **Existing Patterns:**
   - `PlannerVote.java` / `PlannerVoteId.java` - composite key pattern
   - `PlannerBookmark.java` - similar tracking entity
   - `PlannerRepository.incrementViewCount()` - already exists

2. **Current Schema:**
   - `V006__add_view_count_and_bookmarks.sql` - view_count column exists
   - `Planner.java` entity - has `viewCount` field

3. **Request Context:**
   - Extract IP: `X-Forwarded-For` header (proxy) or `request.getRemoteAddr()`
   - Extract User-Agent: `request.getHeader("User-Agent")`

## Scope

Files to READ for context:
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerVote.java`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerVoteId.java`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerBookmark.java`
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerRepository.java`
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java`
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java`
- `backend/src/main/resources/db/migration/V006__add_view_count_and_bookmarks.sql`

## Target Code Area

**New Files:**
- `backend/src/main/resources/db/migration/V008__add_planner_views.sql`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerView.java`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerViewId.java`
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerViewRepository.java`

**Modified Files:**
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` - add `recordView()` method
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java` - add `POST /{id}/view` endpoint

**Frontend (separate task):**
- `frontend/src/lib/plannerApi.ts` - add `recordView()` API call
- Planner detail page - call on mount

## Technical Specification

### Database Schema (V008)

```sql
CREATE TABLE planner_views (
    planner_id CHAR(36) NOT NULL,
    viewer_hash VARCHAR(64) NOT NULL,
    view_date DATE NOT NULL,

    PRIMARY KEY (planner_id, viewer_hash, view_date),
    CONSTRAINT fk_view_planner FOREIGN KEY (planner_id)
        REFERENCES planners(id) ON DELETE CASCADE,
    INDEX idx_planner_views_date (view_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Design Rationale:**
- Date-based PK allows same viewer to count once per day
- No cleanup needed - INSERT IGNORE handles deduplication automatically
- Index on view_date for future cleanup queries if needed

### Hashing Strategy

```java
// For authenticated users
String hash = SHA256(userId + ":" + plannerId);

// For anonymous users
String hash = SHA256(ipAddress + ":" + userAgent + ":" + plannerId);
```

### API Endpoint

```
POST /api/planner/md/{id}/view
- No request body
- No authentication required (public)
- Returns: 204 No Content (success or duplicate - idempotent)
- Returns: 404 Not Found (planner not found or not published)
```

### Deduplication Logic

```
1. Verify planner exists and is published
2. Extract viewer identifier (user_id or IP+UA)
3. Compute SHA-256 hash with planner_id
4. Get current date (server timezone)
5. Attempt INSERT IGNORE into planner_views with (planner_id, hash, date)
6. If insert succeeded (new row) → atomically increment view_count
7. If insert failed (duplicate for today) → no-op
```

## Testing Guidelines

### Manual API Testing

1. Create and publish a planner
2. Record first view:
   ```bash
   curl -X POST http://localhost:8080/api/planner/md/{id}/view \
     -H "X-Forwarded-For: 192.168.1.100" \
     -H "User-Agent: TestBrowser/1.0"
   ```
3. Verify 204 response, view_count = 1
4. Repeat same request (same day)
5. Verify 204 response, view_count still = 1 (deduplicated)
6. Change IP header, repeat
7. Verify view_count = 2 (different viewer)
8. Test unpublished planner → expect 404

### Automated Functional Verification

- [ ] New view: Creates record and increments count
- [ ] Duplicate view: Same hash on same day does not increment
- [ ] Different viewer: Different hash increments count
- [ ] Authenticated user: Uses user_id in hash (not IP)
- [ ] Anonymous user: Uses IP + User-Agent in hash
- [ ] Unpublished planner: Returns 404
- [ ] Deleted planner: Returns 404
- [ ] Concurrent views: Atomic increment handles races

### Edge Cases

- [ ] Missing User-Agent: Use empty string in hash
- [ ] Missing X-Forwarded-For: Use request.getRemoteAddr()
- [ ] Long User-Agent (>256 chars): Truncate before hashing
- [ ] Invalid UUID: Return 400
- [ ] Null planner ID: Return 400

### Date Boundary Testing

- [ ] View at 23:59 counts for today
- [ ] View at 00:01 next day creates new record
- [ ] Same viewer on consecutive days → both count

### Integration Points

- [ ] GET /published shows correct view_count
- [ ] GET /recommended shows correct view_count
- [ ] Sort by "popular" uses viewCount correctly
- [ ] Forked planner has viewCount = 0

## Out of Scope

1. **Planner Type Support** - Adding MD/RR distinction (separate task)
2. **Frontend Integration** - Calling view endpoint from UI (separate task)
3. **Short ID URLs** - See TODO.md UX-001 (separate task)
4. **Scheduled Cleanup** - See TODO.md UX-002 (future consideration)
5. **Rate Limiting** - Not needed at 10k MAU scale

## Notes

- Daily deduplication (vs 24h rolling) chosen for simpler implementation
- Date-based PK eliminates DELETE operations (compatible with INFRA-002)
- Hash approach for GDPR compliance (no raw IP storage)
- `incrementViewCount()` repository method already exists
- Table growth estimate: ~1M rows/year at 10k MAU (acceptable)
