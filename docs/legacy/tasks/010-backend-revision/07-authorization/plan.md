# Authorization System - Execution Plan

## Planning Gaps
**None** - Research is comprehensive. Spec covers all edge cases.

## Execution Overview

Implement role-based authorization in 7 phases:
1. Data Layer (migration, enum, entity)
2. Token Layer (claims, generator, validator)
3. Security Layer (filter, config)
4. Service Layer (admin, moderation, timeout)
5. Controller Layer (endpoints)
6. Integration (wiring)
7. Tests

Each phase builds on the previous, ensuring compile-time safety throughout.

---

## Dependency Analysis

### Files Being Modified

| File | Impact | Depends On | Used By |
|------|--------|------------|---------|
| `V013__add_user_authorization.sql` | LOW | V012 | User entity |
| `UserRole.java` (NEW) | LOW | None | User, TokenClaims, Services |
| `User.java` | MEDIUM | Migration, UserRole | All user services |
| `UserRepository.java` | LOW | UserRole | AdminService |
| `TokenClaims.java` | MEDIUM | UserRole | JwtTokenService, JwtAuthFilter |
| `TokenGenerator.java` | HIGH | UserRole | JwtTokenService, AuthFacade |
| `JwtTokenService.java` | HIGH | TokenClaims, Generator | All token operations |
| `TokenBlacklistService.java` | HIGH | None | AdminService, JwtAuthFilter |
| `JwtAuthenticationFilter.java` | HIGH | TokenClaims | All authenticated requests |
| `SecurityConfig.java` | HIGH | JwtAuthFilter | All API security |
| `PlannerService.java` | MEDIUM | User entity | Planner operations |

### Ripple Effect Map
- TokenGenerator interface change → JwtTokenService + AuthenticationFacade must update
- TokenClaims adds role → JwtTokenService + JwtAuthFilter must handle
- User adds role/timeout → Migration must be applied first
- SecurityConfig adds rules → Test all endpoints after change

### High-Risk Modifications
- `JwtAuthenticationFilter.java` - Breaks all auth if incorrect (mitigate: backward compat)
- `SecurityConfig.java` - Blocks requests if wrong (mitigate: test all endpoints)
- `TokenGenerator.java` - Interface change (mitigate: update impl immediately)

---

## Execution Order

### Phase 1: Data Layer (Steps 1-4)

1. **V013__add_user_authorization.sql** - Create migration
   - Depends on: none
   - Enables: User entity fields

2. **UserRole.java** - Create enum (pattern: MDCategory.java)
   - Depends on: none
   - Enables: User entity, TokenClaims, all services

3. **User.java** - Add role and timeoutUntil fields
   - Depends on: Step 1, Step 2
   - Enables: Admin/Moderation services

4. **UserRepository.java** - Add countByRole query
   - Depends on: Step 2
   - Enables: AdminService safeguard

### Phase 2: Token Layer (Steps 5-8)

5. **TokenClaims.java** - Add nullable role field
   - Depends on: Step 2
   - Enables: JwtTokenService, JwtAuthFilter

6. **TokenGenerator.java** - Update interface with role param
   - Depends on: Step 2
   - Enables: JwtTokenService implementation

7. **JwtTokenService.java** - Embed role in access token
   - Depends on: Step 5, Step 6
   - Enables: Auth filter, auth facade

8. **TokenBlacklistService.java** - Add blacklistUserTokens method
   - Depends on: none
   - Enables: AdminService (invalidate on demotion)

### Phase 3: Security Layer (Steps 9-10)

9. **JwtAuthenticationFilter.java** - Convert role to SimpleGrantedAuthority
   - Depends on: Step 5, Step 7
   - Enables: SecurityConfig rules

10. **SecurityConfig.java** - Add RoleHierarchy + authorization rules
    - Depends on: Step 9
    - Enables: Admin/Moderation endpoints

### Phase 4: Service Layer (Steps 11-15)

11. **UserTimedOutException.java** - Create exception
    - Depends on: none
    - Enables: PlannerService, GlobalExceptionHandler

12. **AdminService.java** - Role change with safeguards
    - Depends on: Step 3, Step 4, Step 8
    - Enables: AdminController

13. **ModerationService.java** - Timeout and unpublish logic
    - Depends on: Step 3
    - Enables: ModerationController

14. **PlannerService.java** - Add timeout check in writes
    - Depends on: Step 3, Step 11
    - Enables: Timeout enforcement

15. **AuthenticationFacade.java** - Pass role to token generation
    - Depends on: Step 3, Step 7
    - Enables: Correct role in tokens

### Phase 5: Controller Layer (Steps 16-19)

16. **ChangeRoleRequest.java, UserRoleResponse.java** - Admin DTOs
    - Depends on: Step 2
    - Enables: AdminController

17. **TimeoutRequest.java** - Moderation DTO
    - Depends on: none
    - Enables: ModerationController

18. **AdminController.java** - Role management endpoint
    - Depends on: Step 10, Step 12, Step 16
    - Enables: F7

19. **ModerationController.java** - Timeout/unpublish endpoints
    - Depends on: Step 10, Step 13, Step 17
    - Enables: F8, F9

### Phase 6: Integration (Steps 20-21)

20. **AuthController.java** - Refresh fetches fresh role
    - Depends on: Step 15
    - Enables: Token refresh correctness

21. **GlobalExceptionHandler.java** - Add timeout handler
    - Depends on: Step 11
    - Enables: Proper 403 response

### Phase 7: Tests (Steps 22-24)

22. **AdminServiceTest.java** - Unit tests for safeguards
    - Depends on: Step 12
    - Verifies: UT2, UT3

23. **ModerationServiceTest.java** - Unit tests for timeout
    - Depends on: Step 13
    - Verifies: UT4

24. **Integration tests** - Full endpoint tests
    - Depends on: All previous
    - Verifies: IT1-IT7

---

## Verification Checkpoints

| After Step | Checkpoint |
|------------|------------|
| 4 | `./mvnw compile` passes, migration applies |
| 8 | Token tests: access has role, refresh doesn't |
| 10 | All existing endpoints still accessible |
| 14 | Timeout blocks writes (manual test) |
| 19 | 403 for unauthorized users on admin/mod endpoints |
| 21 | Full build passes |

---

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| Token backward compat | 5-7 | Null role = NORMAL in filter |
| Interface breaking | 6 | Update JwtTokenService immediately |
| Security blocks all | 10 | Test all endpoints after |
| Last admin lockout | 12 | Test safeguard before deploy |
| Migration breaks prod | 1 | DEFAULT 'NORMAL' for role |

---

## Rollback Strategy

- **Data Layer**: V014__revert_authorization.sql
- **Token Layer**: Null role treated as NORMAL (built-in)
- **Security Layer**: Remove new rules only
- **Services/Controllers**: Delete new files (no cascading)
- **Complete rollback**: Revert code + apply V014
