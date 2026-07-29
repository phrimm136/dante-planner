# Execution Progress: Backend Integration Tests

Last Updated: 2026-01-11
Current Step: 0/10
Current Phase: Not Started

## Milestones

- [ ] M1: Phase 1-2 Complete (Foundations + Critical Controllers)
- [ ] M2: Phase 3-4 Complete (Infrastructure + Documentation)
- [ ] M3: Phase 5 Complete (Repositories + Edge Cases)
- [ ] M4: All 74+ Tests Pass (<60 seconds)
- [ ] M5: No Flaky Tests (3 consecutive runs green)

## Step Log

- Step 1: ⏳ pending - Create TestDataFactory.java
- Step 2: ⏳ pending - Create JwtTestUtil.java
- Step 3: ⏳ pending - Create CommentControllerTest.java (20+ scenarios)
- Step 4: ⏳ pending - Create AuthControllerTest.java (15+ scenarios)
- Step 5: ⏳ pending - Create PlannerSseServiceTest.java (15+ scenarios)
- Step 6: ⏳ pending - Create NotificationControllerTest.java (10+ scenarios)
- Step 7: ⏳ pending - Create backend/TESTING.md
- Step 8: ⏳ pending - Create PlannerCommentRepositoryTest.java (6+ scenarios)
- Step 9: ⏳ pending - Create NotificationRepositoryTest.java (3+ scenarios)
- Step 10: ⏳ pending - Create AdminModerationControllerTest.java (5+ scenarios)

---

## Feature Status

### Core Features
- [ ] F1: Comment CRUD operations validated via HTTP layer - Verify: Run CommentControllerTest (20+ tests pass)
- [ ] F2: OAuth flow creates users with collision retry - Verify: Run AuthControllerTest (15+ tests pass)
- [ ] F3: SSE emitters cleaned up on timeout/error - Verify: Run PlannerSseServiceTest (emitter map empty after tests)
- [ ] F4: Notification inbox excludes soft-deleted - Verify: Run NotificationControllerTest (deleted not in response)

### Edge Cases
- [ ] E1: Orphaned replies visible when parent deleted - Verify: CommentControllerTest "List with deleted parent" returns replies
- [ ] E2: Username generation retries max 5 times - Verify: AuthControllerTest "Collision exhaustion" returns 500
- [ ] E3: SSE duplicate deviceId completes old emitter - Verify: PlannerSseServiceTest "Subscribe twice" → map size 1

### Integration
- [ ] I1: Comment creation triggers COMMENT_RECEIVED notification - Verify: CommentControllerTest integration with NotificationService
- [ ] I2: Vote immutability enforced (409 on duplicate) - Verify: CommentControllerTest "Upvote duplicate" returns 409
- [ ] I3: Token blacklist prevents use after logout - Verify: AuthControllerTest "Use after logout" returns 401

### Dependency Verification
- [ ] D1: GlobalExceptionHandler maps CommentNotFoundException → 404 - Verify after Step 3
- [ ] D2: JwtAuthenticationFilter rejects blacklisted tokens - Verify after Step 4
- [ ] D3: TestDataFactory eliminates duplication - Verify: No duplicated user/planner builders in tests

---

## Testing Checklist

### Test Utilities (Steps 1-2)
- [ ] UT1: TestDataFactory.createTestUser() - returns User with all required fields
- [ ] UT2: TestDataFactory.createTestPlanner() - returns published Planner
- [ ] UT3: TestDataFactory.createTestComment() - returns PlannerComment with depth
- [ ] UT4: JwtTestUtil.generateTokenForUser() - returns valid JWT
- [ ] UT5: JwtTestUtil.generateExpiredToken() - returns expired JWT
- [ ] UT6: JwtTestUtil.generateAdminToken() - returns JWT with ROLE_ADMIN

### CommentController (Step 3)
- [ ] CT1: Create top-level comment - 200, depth=0
- [ ] CT2: Create reply - 200, depth=parent+1
- [ ] CT3: Depth=5 flattening - depth=5, parent's parentId
- [ ] CT4: Unauthorized creation - 401
- [ ] CT5: Update own comment - 200, content updated
- [ ] CT6: Update other's comment - 403
- [ ] CT7: Delete own comment - 204, soft-deleted
- [ ] CT8: Delete other's comment - 403
- [ ] CT9: List comments public - 200, excludes deleted
- [ ] CT10: List unpublished - 404
- [ ] CT11: Upvote first - 200, upvoteCount++
- [ ] CT12: Upvote duplicate - 409
- [ ] CT13: Rate limit - 429 on 11th request
- [ ] CT14: Validation - 400 for empty content

### AuthController (Step 4)
- [ ] AT1: OAuth new user - 200, user created, cookies set
- [ ] AT2: OAuth existing user - 200, lastLoginAt updated
- [ ] AT3: Username collision - 200, retries with different suffix
- [ ] AT4: Deleted user reactivation - 200, deletedAt=null
- [ ] AT5: Invalid auth code - 401
- [ ] AT6: Cookie security - HttpOnly, Secure, SameSite=Lax
- [ ] AT7: Token refresh - 200, new tokens
- [ ] AT8: Refresh expired - 401
- [ ] AT9: Refresh blacklisted - 401
- [ ] AT10: Logout - 204, tokens blacklisted, cookies cleared
- [ ] AT11: Use after logout - 401

### PlannerSseService (Step 5)
- [ ] ST1: Subscribe creates emitter - returned, stored in map
- [ ] ST2: Subscribe replaces old - old completed first
- [ ] ST3: Multiple devices - 3 emitters for same user
- [ ] ST4: Broadcast excludes source - all except source receive
- [ ] ST5: Broadcast no subscribers - no errors
- [ ] ST6: Broken emitter cleanup - IOException → removed
- [ ] ST7: Unsubscribe removes - emitter removed, completed
- [ ] ST8: Unsubscribe idempotent - no error
- [ ] ST9: Timeout cleanup - onTimeout → removed
- [ ] ST10: Completion cleanup - onCompletion → removed
- [ ] ST11: Zombie cleanup - scheduler removes unresponsive

### NotificationController (Step 6)
- [ ] NT1: Inbox listing - 200, paginated
- [ ] NT2: Inbox excludes deleted - deletedAt != null not returned
- [ ] NT3: Inbox authorization - user sees only own
- [ ] NT4: Unread count - 200, count where read=false
- [ ] NT5: Mark read - 200, read=true
- [ ] NT6: Delete notification - 204, deletedAt set
- [ ] NT7: Unauthorized access - 401

### PlannerCommentRepository (Step 8)
- [ ] RT1: Atomic increment - incrementUpvoteCount by 1
- [ ] RT2: Concurrent increments - 10 threads → upvoteCount=10
- [ ] RT3: Decrement safety - upvoteCount=0 remains 0
- [ ] RT4: Soft-delete filtering - findByPlannerId excludes deletedAt
- [ ] RT5: Top-level only - findByPlannerIdAndParentIsNull depth=0
- [ ] RT6: Batch reassignment - reassignCommentsToSentinel authorId=0

### NotificationRepository (Step 9)
- [ ] NR1: UNIQUE constraint - duplicate throws exception
- [ ] NR2: Soft-delete filtering - excludes deletedAt
- [ ] NR3: Count unread - count where read=false

### AdminModerationController (Step 10)
- [ ] MT1: Hide planner admin - 200, hiddenFromRecommended=true
- [ ] MT2: Hide planner non-admin - 403
- [ ] MT3: Unhide planner admin - 200, hiddenFromRecommended=false
- [ ] MT4: Unhide planner non-admin - 403
- [ ] MT5: Hide reason required - 400 if <10 chars

### Manual Verification
- [ ] MV1: Full test suite runs in <60 seconds - Run `mvn test` and measure time
- [ ] MV2: No flaky tests - Run full suite 3 consecutive times, all green
- [ ] MV3: Controller coverage 25% → 80%+ - Check coverage report
- [ ] MV4: Repository coverage 33% → 70%+ - Check coverage report

---

## Summary

Steps: 0/10 complete
Features: 0/4 verified
Tests: 0/74 passed
Overall: 0%
