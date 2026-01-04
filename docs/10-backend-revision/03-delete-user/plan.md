# Execution Plan: User Account Soft-Delete

## Planning Gaps

**NONE** — Research complete, all patterns identified.

---

## Execution Overview

Two-phase user deletion: soft-delete (immediate block) → hard-delete (30-day scheduler).
Bottom-up layering: Migration → Entity → Repository → Exception → Service → Security → Controller → Scheduler → Config → Tests

---

## Execution Order

### Phase 1: Data Layer

**Step 1: V009__add_user_soft_delete.sql** (NEW)
- Columns: `deleted_at`, `permanent_delete_scheduled_at` on users
- Sentinel user: id=0, email='[deleted]', provider='system'
- Indexes for query performance
- Depends on: none
- Enables: F1, F4, F5

**Step 2: User.java** (MODIFY)
- Add: `deletedAt`, `permanentDeleteScheduledAt` (Instant)
- Change: `createdAt`/`updatedAt` from LocalDateTime to Instant
- Add methods: `isDeleted()`, `softDelete()`, `reactivate()`
- Depends on: Step 1
- Enables: F1, F2, F3, F4

### Phase 2: Exception Layer

**Step 3: AccountDeletedException.java** (NEW)
- RuntimeException with userId field
- Depends on: none
- Enables: F2

**Step 4: GlobalExceptionHandler.java** (MODIFY)
- Handler for AccountDeletedException → 401
- Depends on: Step 3
- Enables: F2

### Phase 3: Repository Layer

**Step 5: UserRepository.java** (MODIFY)
- Add: `findByProviderAndProviderIdAndDeletedAtIsNull()`
- Add: `findByPermanentDeleteScheduledAtBefore(Instant)`
- Add: `findByIdAndDeletedAtIsNull(Long)`
- Depends on: Step 2
- Enables: F2, F3, F4

**Step 6: PlannerVoteRepository.java** (MODIFY)
- Add: @Modifying @Query for bulk UPDATE to sentinel
- Depends on: Step 1
- Enables: F5

### Phase 4: Service Layer

**Step 7: UserService.java** (MODIFY)
- Add: `deleteAccount()`, `reactivateAccount()`, `performHardDelete()`, `findActiveById()`
- Add: @Value for grace period config
- Add: SENTINEL_USER_ID constant
- Depends on: Steps 2, 5, 6
- Enables: F1, F3, F4, F5

### Phase 5: Security Layer

**Step 8: JwtAuthenticationFilter.java** (MODIFY)
- Check `isDeleted()` after token validation
- Guard sentinel user (id=0) from auth
- Depends on: Steps 3, 7
- Enables: F2, E2

**Step 9: AuthenticationFacade.java** (MODIFY)
- Detect soft-deleted user in OAuth flow → reactivate
- Add `reactivated` flag to AuthResult
- Check deletion in refreshTokens
- Depends on: Steps 7, 8
- Enables: F3

### Phase 6: Controller Layer

**Step 10: UserDeletionResponse.java** (NEW)
- Record: message, deletedAt, permanentDeleteAt, gracePeriodDays
- Depends on: none
- Enables: F1

**Step 11: UserController.java** (NEW)
- DELETE /api/user/me endpoint
- Depends on: Steps 7, 10
- Enables: F1, E1

### Phase 7: Scheduler

**Step 12: UserCleanupScheduler.java** (NEW)
- @Scheduled with configurable cron
- Find expired users, call performHardDelete
- Depends on: Step 7
- Enables: F4, F5

### Phase 8: Configuration

**Step 13: application.properties** (MODIFY)
- Add: `app.user.deletion.grace-period-days=30`
- Add: `app.user.cleanup.cron=0 0 3 * * *`
- Depends on: Steps 7, 12
- Enables: All features

### Phase 9: Tests

**Step 14: UserServiceTest.java** — Unit tests for deletion/reactivation logic
**Step 15: UserControllerTest.java** — Endpoint tests
**Step 16: UserCleanupSchedulerTest.java** — Scheduler tests
**Step 17: JwtAuthenticationFilterTest.java** — Auth rejection tests
**Step 18: AuthenticationFacadeTest.java** — Reactivation flow tests

---

## Verification Checkpoints

| After Step | Verify | Method |
|------------|--------|--------|
| Step 1 | Migration applies, sentinel exists | `SELECT * FROM users WHERE id = 0` |
| Step 7 | UserService unit tests pass | `mvn test -Dtest=UserServiceTest` |
| Step 11 | DELETE endpoint callable | Manual API test |
| Step 12 | Scheduler registered | Check startup logs |
| Step 18 | Full suite passes | `mvn test` |

---

## Rollback Strategy

| Failed Step | Action | Safe Stop |
|-------------|--------|-----------|
| Step 1 | Drop V009 migration | Before Step 2 |
| Steps 2-6 | Revert entity/repo | Before Step 7 |
| Steps 7-12 | Revert service/controller | Before Step 13 |

**Safe Stopping Points:**
- After Phase 1: DB ready, no behavior change
- After Phase 4: Service ready, security unchanged
- After Phase 8: Feature complete, tests pending

---

## Dependency Graph

```
Step 1 (Migration)
    └── Step 2 (Entity)
            └── Steps 5, 6 (Repositories)
                    └── Step 7 (Service)
                            ├── Step 8 (Filter) → Step 9 (Facade)
                            ├── Step 11 (Controller) ← Step 10 (DTO)
                            └── Step 12 (Scheduler) → Step 13 (Config)

Step 3 (Exception) → Step 4 (Handler) → Step 8 (Filter)
```
