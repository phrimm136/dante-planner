# Task: User Authorization System with Role-Based Access Control

## Description

Implement a role-based authorization system with three hierarchical roles: NORMAL, MODERATOR, and ADMIN. The system provides:

### Role Hierarchy (ADMIN > MODERATOR > NORMAL)
- **NORMAL**: Default role for all new users. Can create, edit, and publish own planners.
- **MODERATOR**: Can unpublish any planner and timeout users (temporarily restrict their write permissions).
- **ADMIN**: All moderator powers plus ability to change user roles via REST API.

### Core Behaviors
1. **New User Default**: All newly created accounts start with NORMAL role
2. **Role Management**: Role changes via REST API (ADMIN only), not direct DB manipulation
3. **Immediate Token Invalidation**: When a user is demoted, their existing JWT tokens are immediately invalidated (critical for revoking troll moderator access)
4. **User Timeout**: Moderators can temporarily restrict users from creating/editing/publishing content

### Authorization Rules
- `/api/admin/**` endpoints require ADMIN role
- `/api/moderation/**` endpoints require MODERATOR+ role (due to hierarchy, ADMIN also has access)
- Existing authenticated endpoints remain unchanged

### Safeguards (Prevent Privilege Escalation)
- Cannot grant a role higher than your own
- Cannot demote the last administrator (prevents lock-out)
- Cannot modify role of users at equal or higher rank (unless self-demotion)
- Cannot timeout administrators
- Moderators cannot timeout fellow moderators (only ADMIN can)

### Token Handling
- Role embedded in JWT access token for fast authorization
- Refresh token does NOT contain role (forces DB lookup to get current role)
- Token blacklist extended to support invalidating all tokens for a specific user

## Research

### Existing Patterns to Follow
- `MDCategory.java` - Enum pattern with @JsonValue/@JsonCreator
- `VoteType.java` - Enum pattern reference
- `TokenBlacklistService.java` - Token invalidation mechanism
- `JwtAuthenticationFilter.java` - Authentication flow, authority setting
- `SecurityConfig.java` - Authorization rules structure

### Spring Security Concepts
- `RoleHierarchy` bean for hierarchical role support
- `SimpleGrantedAuthority` for converting roles to Spring authorities
- `@PreAuthorize` annotations (if needed for method-level security)

### Token Blacklist Enhancement
- Research user-level token invalidation (vs per-token)
- Options: userId→lastInvalidatedAt timestamp approach (recommended for this use case)

## Scope

### Files to READ for Context
- `backend/src/main/java/org/danteplanner/backend/entity/User.java`
- `backend/src/main/java/org/danteplanner/backend/entity/MDCategory.java` (enum pattern)
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenClaims.java`
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenGenerator.java`
- `backend/src/main/java/org/danteplanner/backend/service/token/JwtTokenService.java`
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenBlacklistService.java`
- `backend/src/main/java/org/danteplanner/backend/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/org/danteplanner/backend/config/SecurityConfig.java`
- `backend/src/main/java/org/danteplanner/backend/facade/AuthenticationFacade.java`
- `backend/src/main/java/org/danteplanner/backend/controller/AuthController.java`
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java`
- `backend/src/main/resources/db/migration/` (latest migration number)

## Target Code Area

### New Files
- `backend/src/main/java/org/danteplanner/backend/entity/UserRole.java` (enum)
- `backend/src/main/resources/db/migration/V013__add_user_authorization.sql`
- `backend/src/main/java/org/danteplanner/backend/controller/AdminController.java`
- `backend/src/main/java/org/danteplanner/backend/service/AdminService.java`
- `backend/src/main/java/org/danteplanner/backend/dto/admin/ChangeRoleRequest.java`
- `backend/src/main/java/org/danteplanner/backend/dto/admin/UserRoleResponse.java`
- `backend/src/main/java/org/danteplanner/backend/controller/ModerationController.java`
- `backend/src/main/java/org/danteplanner/backend/service/ModerationService.java`
- `backend/src/main/java/org/danteplanner/backend/dto/moderation/TimeoutRequest.java`
- `backend/src/main/java/org/danteplanner/backend/exception/UserTimedOutException.java`

### Modified Files
- `backend/src/main/java/org/danteplanner/backend/entity/User.java` (+role, +timeoutUntil fields)
- `backend/src/main/java/org/danteplanner/backend/repository/UserRepository.java` (+countByRole)
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenClaims.java` (+role field)
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenGenerator.java` (+role param)
- `backend/src/main/java/org/danteplanner/backend/service/token/JwtTokenService.java` (role in claims)
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenBlacklistService.java` (+blacklistUserTokens)
- `backend/src/main/java/org/danteplanner/backend/security/JwtAuthenticationFilter.java` (role→authority)
- `backend/src/main/java/org/danteplanner/backend/config/SecurityConfig.java` (+RoleHierarchy, +rules)
- `backend/src/main/java/org/danteplanner/backend/facade/AuthenticationFacade.java` (pass role)
- `backend/src/main/java/org/danteplanner/backend/controller/AuthController.java` (refresh: fetch fresh role)
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` (+timeout check)
- `backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java` (+timeout handler)

## System Context (Senior Thinking)

- **Feature domain**: Authentication / User Management / Security
- **Core files in this domain**:
  - Authentication: `AuthController.java`, `JwtService.java`, `JwtAuthenticationFilter.java`
  - User Management: `UserService.java`, `UserController.java`, `User.java`
  - Security Config: `SecurityConfig.java`
- **Cross-cutting concerns touched**:
  - JWT token generation/validation (affects all authenticated requests)
  - Security filter chain (affects all requests)
  - Planner write operations (timeout enforcement)

## Impact Analysis

### Files Being Modified (Impact Level)

| File | Impact | Notes |
|------|--------|-------|
| `SecurityConfig.java` | **HIGH** | All authenticated requests affected |
| `JwtAuthenticationFilter.java` | **HIGH** | Critical auth path |
| `JwtTokenService.java` | **HIGH** | All token operations |
| `TokenBlacklistService.java` | **HIGH** | Token invalidation |
| `User.java` | MEDIUM | Entity change requires migration |
| `TokenClaims.java` | MEDIUM | JWT payload structure |
| `PlannerService.java` | MEDIUM | Write operations affected |
| `AuthenticationFacade.java` | LOW | Token generation |
| `AuthController.java` | LOW | Refresh endpoint |

### What Depends on These Files
- `JwtAuthenticationFilter.java` → All authenticated endpoints
- `SecurityConfig.java` → All API security rules
- `TokenBlacklistService.java` → Token revocation flows
- `User.java` → All user-related services

### Potential Ripple Effects
- Token structure change requires all active sessions to re-authenticate
- Security rule changes affect all protected endpoints immediately
- Migration adds non-null column (safe: has DEFAULT value)

### High-Impact Files to Watch
- `SecurityConfig.java` - Test all endpoint access after changes
- `JwtAuthenticationFilter.java` - Test authentication flow thoroughly

## Risk Assessment

### Edge Cases Not Yet Defined
- What happens to timed-out user's published planners? (remain published)
- Can users see their own role? (via /api/user/me endpoint if exists)
- Should role change emit SSE event? (not in initial scope)

### Security Considerations
- **Privilege escalation**: Safeguards implemented (can't grant higher than own, can't demote last admin)
- **Token staleness**: Addressed via user-level blacklist on demotion
- **Self-demotion**: Allowed but protected against last-admin scenario
- **Error message leakage**: Use generic "Insufficient permissions" (don't reveal role structure)

### Backward Compatibility
- Existing tokens without role claim: Handle gracefully (treat as NORMAL)
- Migration is additive with DEFAULT value (non-breaking)

### Performance Concerns
- Token blacklist check per request (already exists, adding user-level check)
- RoleHierarchy evaluation (negligible, cached by Spring)

## Testing Guidelines

### Manual API Testing

#### Role Management (ADMIN endpoints)
1. Create test user with NORMAL role (default)
2. As ADMIN, call `PUT /api/admin/user/{id}/role` with `{"role":"MODERATOR"}`
3. Verify 200 OK response
4. Verify user can now access `/api/moderation/**` endpoints
5. As ADMIN, demote user back to NORMAL
6. Verify user's existing token is immediately invalidated (next request returns 401)
7. Have user re-login, verify they're now NORMAL
8. Attempt to access `/api/moderation/**` as NORMAL user
9. Verify 403 Forbidden

#### Safeguard Testing
1. As MODERATOR, attempt `PUT /api/admin/user/{id}/role` with `{"role":"ADMIN"}`
2. Verify 403 - cannot grant higher than own
3. As sole ADMIN, attempt to demote self to MODERATOR
4. Verify 403 - cannot demote last administrator
5. As MODERATOR, attempt to change another MODERATOR's role
6. Verify 403 - cannot modify equal rank
7. As ADMIN, successfully demote a MODERATOR to NORMAL
8. Verify success and token invalidation

#### Moderation Endpoints
1. As MODERATOR, call `POST /api/moderation/user/{id}/timeout` with `{"durationMinutes":60}`
2. Verify 200 OK
3. As timed-out user, attempt to create planner
4. Verify 403 with "USER_TIMED_OUT" error
5. As MODERATOR, call `PUT /api/moderation/planner/{id}/unpublish`
6. Verify planner is no longer published
7. As MODERATOR, attempt to timeout an ADMIN
8. Verify 403 - cannot timeout administrators

#### Token Refresh Flow
1. User logs in, receives access token with role=NORMAL
2. Admin promotes user to MODERATOR (but user keeps old token)
3. User's old token still works but has old role
4. User refreshes token via `/api/auth/refresh`
5. Verify new access token has role=MODERATOR

### Automated Functional Verification

#### Role Assignment
- [ ] New users created with NORMAL role by default
- [ ] UserRole enum serializes to/from JSON correctly
- [ ] Role field persisted to database

#### Authorization Rules
- [ ] `/api/admin/**` returns 403 for MODERATOR users
- [ ] `/api/admin/**` returns 200 for ADMIN users
- [ ] `/api/moderation/**` returns 403 for NORMAL users
- [ ] `/api/moderation/**` returns 200 for MODERATOR users
- [ ] `/api/moderation/**` returns 200 for ADMIN users (hierarchy)

#### Token Handling
- [ ] Access token contains role claim
- [ ] Refresh token does NOT contain role claim
- [ ] Token refresh fetches current role from DB
- [ ] Demotion triggers immediate token blacklist

#### Safeguards
- [ ] Cannot grant role higher than own: returns 403
- [ ] Cannot demote last admin: returns 403
- [ ] Cannot modify equal/higher rank: returns 403
- [ ] Self-demotion allowed (if not last admin)

#### Timeout
- [ ] Timed-out users blocked from creating planners
- [ ] Timed-out users blocked from updating planners
- [ ] Timed-out users blocked from publishing planners
- [ ] Timeout removal re-enables write access
- [ ] Cannot timeout administrators

### Edge Cases

- [ ] **Token without role claim**: Existing tokens (before migration) treated as NORMAL
- [ ] **Invalid role value in request**: Returns 400 with validation error
- [ ] **Timeout already expired**: User can write normally (no error)
- [ ] **Unpublish already unpublished planner**: Returns error (idempotency decision)
- [ ] **Promote already promoted user**: No-op, returns 200
- [ ] **Concurrent role changes**: Last write wins (optimistic)
- [ ] **Deleted user**: Cannot change role of deleted user

### Integration Points

- [ ] **Planner creation**: Timeout check integrated before save
- [ ] **Planner update**: Timeout check integrated before save
- [ ] **Planner publish**: Timeout check integrated before publish
- [ ] **Token refresh**: Role fetched from DB, not from old token
- [ ] **SSE connections**: Consider if role change should disconnect (future enhancement)
