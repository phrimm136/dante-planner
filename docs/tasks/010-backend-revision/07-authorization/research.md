# Authorization System Research

## Spec Ambiguities
- **None found** - Spec is explicit and comprehensive

---

## Spec-to-Code Mapping

### New Files
- `UserRole.java` - Enum with NORMAL, MODERATOR, ADMIN
- `V013__add_user_authorization.sql` - Database migration (V012 is latest)
- `AdminController.java` - PUT /api/admin/user/{id}/role
- `AdminService.java` - Role change logic with safeguards
- `ModerationController.java` - Timeout and unpublish endpoints
- `ModerationService.java` - Moderation business logic
- `ChangeRoleRequest.java`, `UserRoleResponse.java` - Admin DTOs
- `TimeoutRequest.java` - Moderation DTO
- `UserTimedOutException.java` - Timeout exception

### Modified Files
- `User.java` - Add role (non-null, DEFAULT 'NORMAL') + timeoutUntil (nullable)
- `TokenClaims.java` - Add role field (nullable for backward compat)
- `JwtTokenService.java` - Embed role in access token only
- `AuthenticationFacade.java` - Fresh role on token refresh
- `TokenBlacklistService.java` - Add blacklistUserTokens(userId)
- `JwtAuthenticationFilter.java` - Convert role to SimpleGrantedAuthority
- `SecurityConfig.java` - Add RoleHierarchy bean + authorization rules
- `PlannerService.java` - Add timeout check in write operations
- `UserRepository.java` - Add countByRole(UserRole)
- `GlobalExceptionHandler.java` - Add UserTimedOutException handler

---

## Pattern Enforcement (MANDATORY)

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `UserRole.java` | `MDCategory.java` | @JsonValue/@JsonCreator, fromValue(), isValid() |
| `AdminService.java` | `PlannerService.java` | Constructor injection, @Transactional |
| `ModerationService.java` | `UserService.java` | @Transactional, validation |
| `ChangeRoleRequest.java` | `OAuthCallbackRequest.java` | Record DTO with @NotNull |
| `UserTimedOutException.java` | `PlannerNotFoundException.java` | RuntimeException pattern |
| `AdminController.java` | `PlannerController.java` | @RestController, @Valid |
| `ModerationController.java` | `AuthController.java` | Request/response handling |

---

## Existing Utilities (Reuse)

| Category | Existing | Reuse For |
|----------|----------|-----------|
| Enums | MDCategory, VoteType | UserRole pattern |
| Exceptions | PlannerNotFoundException | UserTimedOutException pattern |
| Token handling | TokenBlacklistService | User-level blacklist |
| Services | PlannerService | Safeguard pattern |

---

## Gap Analysis

### Currently Missing
- User role enum and entity fields
- Role in JWT token claims
- User-level token blacklist method
- Admin/Moderation controllers and services
- Timeout enforcement in PlannerService

### Can Reuse
- Enum pattern from MDCategory
- Service layer pattern from PlannerService
- Exception handling from GlobalExceptionHandler
- Token flow from AuthenticationFacade

---

## Testing Requirements

### Unit Tests
- AdminService: privilege escalation safeguards, last admin protection
- ModerationService: timeout logic, cannot timeout admins
- PlannerService: timeout check blocks writes
- UserRole: serialization, isValid(), fromValue()

### Integration Tests
- /api/admin/** blocked for non-admin (403)
- /api/moderation/** blocked for normal users (403)
- Role hierarchy: admin can access moderation endpoints
- Token invalidation on demotion
- Timeout blocks planner writes

---

## Technical Constraints

- Spring Boot 4.0.0 / Spring Security 6.x
- RoleHierarchy bean for hierarchical authorization
- Access token: includes role claim
- Refresh token: NO role claim (forces DB lookup)
- Backward compat: old tokens without role = NORMAL
- Migration V013: additive with DEFAULT (non-breaking)

---

## Implementation Sequence

1. Database migration (V013)
2. UserRole enum
3. User entity fields
4. Token updates (TokenClaims, JwtTokenService)
5. Auth updates (filter, facade, security config)
6. Services (AdminService, ModerationService)
7. Controllers (AdminController, ModerationController)
8. PlannerService timeout check
9. Exception handling
10. Tests
