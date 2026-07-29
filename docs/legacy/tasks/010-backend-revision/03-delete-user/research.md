# Research: User Account Soft-Delete

## Spec Ambiguities

**NONE FOUND** — Specification is complete and unambiguous.

**One consistency note:** User.java currently uses `LocalDateTime`; must change to `Instant` to match Planner/PlannerVote pattern.

---

## Spec-to-Code Mapping

| Requirement | Target File | Action |
|-------------|-------------|--------|
| Soft-delete columns | `User.java` | Add `deletedAt`, `permanentDeleteScheduledAt` (Instant) |
| Sentinel user + indexes | `V009__add_user_soft_delete.sql` | NEW: migration file |
| DELETE endpoint | `UserController.java` | NEW: REST controller |
| Response DTO | `UserDeletionResponse.java` | NEW: record class |
| Background scheduler | `UserCleanupScheduler.java` | NEW: @Scheduled service |
| Auth reactivation | `AuthenticationFacade.java` | MODIFY: detect + reactivate soft-deleted |
| Block deleted users | `JwtAuthenticationFilter.java` | MODIFY: reject if isDeleted() |
| Custom exception | `AccountDeletedException.java` | NEW: RuntimeException |
| Handle exception | `GlobalExceptionHandler.java` | MODIFY: add handler → 401 |
| Expired user query | `UserRepository.java` | MODIFY: add finder method |
| Vote reassignment | `PlannerVoteRepository.java` | MODIFY: add bulk UPDATE query |
| Deletion orchestration | `UserService.java` | MODIFY: add 4 methods |
| Config properties | `application.properties` | MODIFY: add 2 properties |

---

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `UserController.java` | `AuthController.java` | REST structure, ResponseEntity, @AuthenticationPrincipal |
| `UserDeletionResponse.java` | DTO records in `dto/planner/` | Record-based DTO pattern |
| `AccountDeletedException.java` | `UserNotFoundException.java` | Exception hierarchy, message construction |
| `UserCleanupScheduler.java` | `TokenBlacklistService.java` | @Scheduled, @Slf4j, @Transactional |
| `V009__add_user_soft_delete.sql` | `V008__add_planner_views.sql` | Flyway migration format |

---

## Existing Utilities to Reuse

| Category | Location | What to Use |
|----------|----------|-------------|
| Soft-delete pattern | `Planner.java:71-72` | `isDeleted()`, `softDelete()` methods |
| Reactivation pattern | `PlannerVote.java:158-162` | `reactivate()` method structure |
| Timestamp handling | `Planner.java`, `PlannerVote.java` | `Instant.now()` in @PrePersist |
| Query pattern | `PlannerRepository.java:29,36,53` | `...AndDeletedAtIsNull()` suffix |
| Error responses | `GlobalExceptionHandler.java` | `ErrorResponse` record pattern |
| Config injection | `PlannerService.java:64-65` | `@Value("${...}")` pattern |
| Auth result | `AuthenticationFacade.java:57-83` | `AuthResult` record (extend for reactivated) |

---

## Gap Analysis

### Must Create
- `User.java`: 2 fields + 2 methods
- `UserController.java`: New controller
- `UserDeletionResponse.java`: New DTO
- `AccountDeletedException.java`: New exception
- `UserCleanupScheduler.java`: New scheduler
- `V009__add_user_soft_delete.sql`: New migration

### Must Modify
- `UserRepository.java`: Add 2 query methods
- `PlannerVoteRepository.java`: Add bulk UPDATE @Query
- `UserService.java`: Add 4 methods (deleteAccount, reactivate, hardDelete, reassignVotes)
- `AuthenticationFacade.java`: Reactivation logic + flag
- `JwtAuthenticationFilter.java`: Deletion check
- `GlobalExceptionHandler.java`: Exception handler

### Can Reuse Directly
- Soft-delete pattern from Planner
- Reactivation pattern from PlannerVote
- Exception hierarchy from UserNotFoundException
- Scheduler pattern from TokenBlacklistService

---

## Testing Requirements

### Manual API Tests
- DELETE /api/user/me → verify 200 with timestamps
- Auth with deleted user → verify 401 rejection
- Refresh with deleted user → verify 401 rejection
- OAuth re-login during grace period → verify reactivation
- OAuth re-login after hard-delete → verify fresh account

### Automated Tests

**Unit:**
- UserService.deleteAccount() sets timestamps correctly
- UserService.reactivateAccount() clears timestamps
- UserService.performHardDelete() reassigns votes then deletes
- UserCleanupScheduler finds expired users

**Integration:**
- Full deletion flow end-to-end
- Reactivation flow end-to-end
- Vote count preservation after hard-delete

---

## Technical Constraints

| Constraint | Resolution |
|------------|------------|
| User uses LocalDateTime | Change to Instant (match Planner) |
| Sentinel user ID=0 | Create in migration; guard in auth |
| Composite key votes | Use native @Query for bulk UPDATE |
| Multi-server scheduler | Accept single-instance for now (low risk) |
| Rate limiting | Consider adding to DELETE endpoint |

---

## Implementation Order

1. **Migration** — V009 (schema + sentinel)
2. **Entity** — User.java (fields + methods)
3. **Repository** — UserRepository, PlannerVoteRepository queries
4. **Exception** — AccountDeletedException + handler
5. **Service** — UserService methods
6. **Security** — JwtAuthenticationFilter check
7. **Facade** — AuthenticationFacade reactivation
8. **Controller** — UserController endpoint
9. **Scheduler** — UserCleanupScheduler
10. **Config** — application.properties
