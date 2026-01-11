# Research: Remove Downvote from Planner Voting

## Spec Ambiguities
**NONE DETECTED** - Spec is explicit and complete

## Spec-to-Code Mapping

### Backend Changes
- **VoteType.java** (lines 10-12): Remove `DOWN("DOWN")` enum value, update `fromValue()` to reject "DOWN"
- **Planner.java** (lines 92-93): Remove `private Integer downvotes = 0;` field
- **PlannerService.java** (line 462+): Remove `case DOWN` branch in castVote method
- **PlannerRepository.java** (lines 149-161): Remove `incrementDownvotes()` and `decrementDownvotes()` methods
- **PlannerRepository.java** (queries): Replace `(p.upvotes - p.downvotes)` with `p.upvotes` in all net vote calculations
- **VoteRequest.java** (javadoc): Update to reflect upvote-only system
- **V023__remove_downvotes.sql** (NEW): Delete all DOWN votes, drop downvotes column

### Frontend Changes
- **PlannerCardContextMenu.tsx** (lines 163-186, 288-300): Remove `handleDownvote()` and downvote DropdownMenuItem
- **PlannerListTypes.ts** (line 42): Change `VoteDirection` from `'UP' | 'DOWN'` to `'UP'` only
- **PublicPlanner interface**: Update userVote type to reflect UP-only voting
- **i18n files** (CN, EN, JP, KR): Remove `downvote`, `downvoted`, `messageDown` keys

## Pattern Enforcement

| File Modified | MUST Read First | Pattern to Apply |
|---------------|-----------------|------------------|
| V023__remove_downvotes.sql | V021__add_atomic_flags.sql, V018__make_votes_immutable.sql | Migration structure: header comments, table alterations |
| VoteType.java | Current VoteType.java | Enum with Jackson annotations, maintain immutability |
| PlannerRepository.java | Current atomic methods (incrementUpvotes line 139) | @Query with @Param, WHERE safety clauses |

## Pattern Copy Deep Analysis

### VoteType.java Enum Simplification
- Total lines: 35
- Dependencies: Jackson @JsonCreator, @JsonValue
- Modification: Remove DOWN("DOWN") constant, update fromValue() to throw for "DOWN"
- Contract: After change, only "UP" accepted in serialization/deserialization

### PlannerRepository.java Atomic Methods
- Keep: `incrementUpvotes()` (line 136-141)
- Remove: `incrementDownvotes()`, `decrementDownvotes()` (lines 142-161)
- Update: All queries using `(p.upvotes - p.downvotes)` → `p.upvotes`
- Query locations: `findPublished` (line 76), `findRecommended` (line 80)

### PlannerService.castVote() Switch Logic
- Location: Line 462+
- Keep: `case VoteType.UP:` branch
- Remove: `case VoteType.DOWN:` branch entirely
- Result: Simplified single-branch logic

## Existing Utilities Check

No new utilities needed - this is purely removal/simplification work.

## Gap Analysis

### Currently Missing
- Migration file V023__remove_downvotes.sql

### Needs Modification
- Backend: VoteType.java, Planner.java, PlannerRepository.java, PlannerService.java, VoteRequest.java
- Frontend: PlannerCardContextMenu.tsx, PlannerListTypes.ts
- i18n: All 4 language files (CN, EN, JP, KR)

### Can Reuse
- Vote immutability pattern (V018 migration + PlannerVote implementation)
- Upvote atomic increment method
- Vote response schema structure
- Vote notification logic (works for UP votes)

## Testing Requirements

### Manual UI Tests
1. Navigate to `/planner/md/gesellschaft`
2. Open planner card context menu
3. Verify only upvote button visible (no downvote)
4. Click upvote, verify count increments
5. Refresh page, verify vote persists
6. Attempt duplicate upvote, verify 409 error shown
7. Check all 4 languages render correctly

### Automated Tests - Backend

**Unit Tests to Update:**
- PlannerControllerTest.java: Remove all VoteType.DOWN test cases
- Remove: "Should return 200 when casting downvote" test
- Remove: All assertions checking `.downvotes` field
- Update: Net vote calculation tests to use upvotes only

**Integration Tests:**
- Verify DOWN votes deleted after migration
- Verify downvotes column removed from schema
- Verify castVote works with UP only
- Verify POST with DOWN enum value returns 400

### Automated Tests - Frontend

**Unit Tests:**
- usePlannerVote.ts: Verify mutation only accepts 'UP'
- Verify 409 Conflict handled gracefully

**Component Tests:**
- PlannerCardContextMenu.tsx: Verify downvote button not rendered
- Verify upvote button renders and works
- Verify disabled state when already voted

### Edge Cases
- Empty vote state: No button shown before voting
- Duplicate upvote: 409 error handled by hook
- POST with DOWN enum: 400 Bad Request (deserialization fails)
- Migration on empty database: Executes without error
- Migration with existing votes: All DOWN votes deleted

### Integration Points
- Vote endpoint accepts only UP (controller validation)
- SSE updates propagate (planner_updated events)
- Recommendation threshold recalculated (upvotes only)
- Notification system triggers on upvotes

## Technical Constraints

### Critical Constraints
- **Immutable voteType in PlannerVote**: Can't retroactively change existing votes (data deletion acceptable per spec)
- **Irreversible migration**: Downvotes column deleted permanently (backup recommended)
- **Enum deserialization**: Jackson rejects "DOWN" with IllegalArgumentException after change
- **Net vote queries**: Must update in ALL repository methods using find-replace: `(p.upvotes - p.downvotes)` → `p.upvotes`

### Deployment Requirements
- Frontend and backend must deploy together (coordinated release)
- Frontend must not send DOWN after deployment
- Database backup recommended before migration
- Monitor for IllegalArgumentException in logs (indicates client sent DOWN)

### Response Schema Compatibility
- PublicPlannerResponse still includes downvotes field (keep for now, set to 0)
- Allows gradual frontend migration if needed
- Can remove field in future version

## Domain Context

**Domains Affected:** Backend (services/repositories/entities) + Frontend (components/types/i18n) + Database (schema)

**Required Skills:**
- Backend: be-service (PlannerService), be-controller (VoteRequest)
- Frontend: fe-component (PlannerCardContextMenu), fe-data (types/schemas)
- Database: Flyway migration patterns

**Architecture Impact:**
- Recommendation algorithm simplified (net = upvotes only)
- Planner rankings magnitude increases (upvotes uncontested)
- Vote immutability unaffected (still permanent)
- Notification system complexity reduced (no DOWN notifications)

## Migration Script Structure

Follow V021 pattern:
1. Comment header with version and description
2. DELETE FROM planner_votes WHERE vote_type = 'DOWN'
3. ALTER TABLE planner DROP COLUMN downvotes
4. Verify no indexes reference downvotes column before dropping
