# Authorization System - Architecture and Implementation

This document explains the design decisions, architecture, and implementation of the role-based authorization system.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Role Hierarchy Design](#role-hierarchy-design)
3. [Token Architecture](#token-architecture)
4. [Security Layer Integration](#security-layer-integration)
5. [Service Layer Design](#service-layer-design)
6. [Database Schema](#database-schema)
7. [Request Flow](#request-flow)
8. [Safeguards Implementation](#safeguards-implementation)

---

## System Overview

### Goals

- **Role-based access control**: Three-tier hierarchy (NORMAL < MODERATOR < ADMIN)
- **Fast authorization**: Role in JWT avoids DB lookup on every request
- **Immediate revocation**: Demotion instantly invalidates existing tokens
- **Privilege escalation prevention**: Multiple safeguards against unauthorized access

### Components

```
┌─────────────────────────────────────────────────────────────────┐
│                        Request Flow                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Client ──► JwtAuthFilter ──► SecurityConfig ──► Controller    │
│                   │                  │                           │
│                   ▼                  ▼                           │
│            TokenClaims         RoleHierarchy                     │
│            (role claim)        (ADMIN > MOD > NORMAL)            │
│                   │                                              │
│                   ▼                                              │
│          TokenBlacklistService                                   │
│          (user-level invalidation)                               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Role Hierarchy Design

### Why Three Tiers?

| Role | Purpose | Capabilities |
|------|---------|--------------|
| NORMAL | Default for all users | CRUD own planners, publish |
| MODERATOR | Community management | Timeout users, unpublish content |
| ADMIN | System administration | All above + change roles |

### Spring Security RoleHierarchy

**File: `SecurityConfig.java`**

```java
@Bean
public RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.withDefaultRolePrefix()
        .role("ADMIN").implies("MODERATOR")
        .role("MODERATOR").implies("NORMAL")
        .build();
}
```

### How Hierarchy Works

When ADMIN accesses `/api/moderation/**`:
1. SecurityConfig requires `ROLE_MODERATOR`
2. RoleHierarchy knows `ADMIN implies MODERATOR`
3. Access granted without explicit ADMIN rule

This avoids duplicating rules:
```java
// WITHOUT hierarchy (verbose, error-prone)
.requestMatchers("/api/moderation/**").hasAnyRole("MODERATOR", "ADMIN")

// WITH hierarchy (clean)
.requestMatchers("/api/moderation/**").hasRole("MODERATOR")
```

### Explicit Rank for Comparisons

RoleHierarchy handles URL authorization, but business logic needs comparisons:

```java
public enum UserRole {
    NORMAL("NORMAL", 1),
    MODERATOR("MODERATOR", 2),
    ADMIN("ADMIN", 3);

    private final int rank;

    public boolean outranks(UserRole other) {
        return this.rank > other.rank;
    }

    public boolean hasRankAtLeast(UserRole other) {
        return this.rank >= other.rank;
    }
}
```

**Use cases:**
- `actor.outranks(target)` → Can modify target's role?
- `newRole.outranks(actorRole)` → Trying to grant higher than self?

---

## Token Architecture

### Design Decision: Role in Access Token Only

```
┌─────────────────────────────────────────────────────────────┐
│                    Token Strategy                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│   ACCESS TOKEN (15 min)          REFRESH TOKEN (7 days)     │
│   ├── userId                     ├── userId                 │
│   ├── email                      ├── email                  │
│   ├── role ◄── INCLUDED          ├── (no role) ◄── EXCLUDED │
│   ├── issuedAt                   ├── issuedAt               │
│   └── expiration                 └── expiration             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Why This Design?

**Role in access token:**
- Fast authorization (no DB lookup per request)
- Short-lived (15 min), so role staleness is limited

**No role in refresh token:**
- Forces DB lookup on refresh
- Gets current role, not stale cached role
- Refresh = "re-validate my permissions"

### Token Generation

**File: `TokenGenerator.java`**

```java
public interface TokenGenerator {
    String generateAccessToken(Long userId, String email, UserRole role);
    String generateRefreshToken(Long userId, String email); // No role!
}
```

**File: `JwtTokenService.java`**

```java
@Override
public String generateAccessToken(Long userId, String email, UserRole role) {
    Map<String, Object> claims = new HashMap<>();
    claims.put(CLAIM_USER_ID, userId);
    claims.put(CLAIM_EMAIL, email);
    claims.put(CLAIM_TYPE, TokenClaims.TYPE_ACCESS);
    claims.put(CLAIM_ROLE, role.getValue()); // Role embedded
    return buildToken(claims, accessTokenExpiry);
}

@Override
public String generateRefreshToken(Long userId, String email) {
    Map<String, Object> claims = new HashMap<>();
    claims.put(CLAIM_USER_ID, userId);
    claims.put(CLAIM_EMAIL, email);
    claims.put(CLAIM_TYPE, TokenClaims.TYPE_REFRESH);
    // No role claim!
    return buildToken(claims, refreshTokenExpiry);
}
```

### Backward Compatibility

Old tokens (pre-authorization) don't have role claim:

**File: `TokenClaims.java`**

```java
public record TokenClaims(
    Long userId,
    String email,
    String type,
    UserRole role,  // Nullable for old tokens
    Date issuedAt,
    Date expiration
) {
    public UserRole getEffectiveRole() {
        return role != null ? role : UserRole.NORMAL;
    }
}
```

---

## Security Layer Integration

### Filter Chain Position

```
Request ──► CorsFilter ──► JwtAuthFilter ──► SecurityConfig ──► Controller
                               │
                               ▼
                    ┌──────────────────────┐
                    │ 1. Extract token     │
                    │ 2. Validate JWT      │
                    │ 3. Check blacklist   │
                    │ 4. Check user-level  │
                    │ 5. Set authorities   │
                    └──────────────────────┘
```

### JwtAuthenticationFilter

**File: `JwtAuthenticationFilter.java`**

```java
@Override
protected void doFilterInternal(...) {
    String token = cookieUtils.getCookieValue(request, ACCESS_TOKEN);

    if (token != null) {
        // 1. Validate and parse JWT
        TokenClaims claims = tokenValidator.validateToken(token);

        // 2. Check token-level blacklist (logout)
        if (tokenBlacklistService.isBlacklisted(token)) {
            throw new TokenRevokedException(TokenClaims.TYPE_ACCESS);
        }

        // 3. Check user-level blacklist (demotion)
        if (tokenBlacklistService.isUserTokenInvalidated(
                claims.userId(), claims.issuedAt().getTime())) {
            throw new TokenRevokedException(TokenClaims.TYPE_ACCESS);
        }

        // 4. Convert role to Spring authority
        UserRole role = claims.getEffectiveRole();
        List<SimpleGrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + role.getValue())
        );

        // 5. Set authentication context
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(
                claims.userId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    filterChain.doFilter(request, response);
}
```

### SecurityConfig Authorization Rules

**File: `SecurityConfig.java`**

```java
.authorizeHttpRequests(auth -> auth
    // Public endpoints
    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers("/api/public/**").permitAll()

    // Role-restricted endpoints
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .requestMatchers("/api/moderation/**").hasRole("MODERATOR")

    // All other authenticated endpoints
    .anyRequest().authenticated()
)
```

---

## Service Layer Design

### AdminService: Role Management

**Responsibilities:**
- Change user roles with safeguards
- Invalidate tokens on demotion

**File: `AdminService.java`**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional
    public User changeRole(Long actorId, Long targetId, UserRole newRole) {
        // Pessimistic lock prevents race conditions
        User actor = userRepository.findWithLockByIdAndDeletedAtIsNull(actorId)
                .orElseThrow(() -> new UserNotFoundException(actorId));
        User target = userRepository.findWithLockByIdAndDeletedAtIsNull(targetId)
                .orElseThrow(() -> new UserNotFoundException(targetId));

        UserRole actorRole = actor.getRole();
        UserRole targetCurrentRole = target.getRole();

        // Safeguard checks (see Safeguards section)
        validateRoleChange(actor, target, actorRole, targetCurrentRole, newRole);

        // Apply change
        UserRole oldRole = target.getRole();
        target.setRole(newRole);
        User saved = userRepository.save(target);

        // Invalidate tokens on demotion
        if (oldRole.outranks(newRole)) {
            tokenBlacklistService.invalidateUserTokens(targetId);
            log.info("User {} demoted. Tokens invalidated.", targetId);
        }

        return saved;
    }
}
```

### ModerationService: Content & User Moderation

**Responsibilities:**
- Timeout users (temporary write restriction)
- Remove timeouts
- Unpublish planners

**File: `ModerationService.java`**

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationService {

    @Transactional
    public User timeoutUser(Long actorId, Long targetId, int durationMinutes) {
        User actor = userRepository.findByIdAndDeletedAtIsNull(actorId)
                .orElseThrow(() -> new UserNotFoundException(actorId));
        User target = userRepository.findByIdAndDeletedAtIsNull(targetId)
                .orElseThrow(() -> new UserNotFoundException(targetId));

        // Cannot timeout admins
        if (target.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("Cannot timeout administrators");
        }

        // Moderators can only timeout NORMAL users
        if (actor.getRole() == UserRole.MODERATOR &&
            target.getRole() == UserRole.MODERATOR) {
            throw new IllegalArgumentException("Moderators cannot timeout other moderators");
        }

        Instant timeoutUntil = Instant.now().plus(durationMinutes, ChronoUnit.MINUTES);
        target.setTimeoutUntil(timeoutUntil);
        return userRepository.save(target);
    }

    @Transactional
    public Planner unpublishPlanner(Long actorId, UUID plannerId) {
        Planner planner = plannerRepository.findById(plannerId)
                .filter(p -> p.getDeletedAt() == null)
                .orElseThrow(() -> new PlannerNotFoundException(plannerId));

        planner.setPublished(false);
        return plannerRepository.save(planner);
    }
}
```

### PlannerService: Timeout Enforcement

**File: `PlannerService.java`**

```java
private User getUserAndCheckNotTimedOut(Long userId) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
    if (user.isTimedOut()) {
        throw new UserTimedOutException(userId, user.getTimeoutUntil());
    }
    return user;
}

public PlannerResponse createPlanner(Long userId, CreatePlannerRequest request) {
    User user = getUserAndCheckNotTimedOut(userId); // Blocks if timed out
    // ... create planner ...
}

public PlannerResponse updatePlanner(Long userId, UUID plannerId, ...) {
    getUserAndCheckNotTimedOut(userId); // Blocks if timed out
    // ... update planner ...
}
```

---

## Database Schema

### V013 Migration: Add Authorization Fields

**File: `V013__add_user_authorization.sql`**

```sql
-- Add role column with default for existing users
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'NORMAL';

-- Add timeout column (nullable - most users not timed out)
ALTER TABLE users ADD COLUMN timeout_until TIMESTAMP WITH TIME ZONE;

-- Index for role-based queries
CREATE INDEX idx_users_role ON users(role);
```

### V014 Migration: Timeout Index

**File: `V014__add_timeout_index.sql`**

```sql
-- Partial index for timeout lookups
-- Only indexes rows where timeout_until is set (small subset)
CREATE INDEX idx_users_timeout_until ON users(timeout_until)
WHERE timeout_until IS NOT NULL;
```

### User Entity

**File: `User.java`**

```java
@Entity
@Table(name = "users")
public class User {
    // ... existing fields ...

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserRole role = UserRole.NORMAL;

    @Column(name = "timeout_until")
    private Instant timeoutUntil;

    public boolean isTimedOut() {
        return timeoutUntil != null && Instant.now().isBefore(timeoutUntil);
    }
}
```

---

## Request Flow

### Scenario: Admin Changes User Role

```
1. Admin sends PUT /api/admin/user/123/role
   Body: {"role": "MODERATOR"}

2. JwtAuthFilter:
   - Extracts access token from cookie
   - Validates JWT signature
   - Checks blacklist (not blacklisted)
   - Extracts role = ADMIN
   - Sets authority = ROLE_ADMIN

3. SecurityConfig:
   - /api/admin/** requires hasRole("ADMIN")
   - ROLE_ADMIN matches → access granted

4. AdminController:
   - @AuthenticationPrincipal extracts userId (admin's ID)
   - @PathVariable extracts targetId (123)
   - @RequestBody extracts role (MODERATOR)
   - Calls adminService.changeRole(adminId, 123, MODERATOR)

5. AdminService:
   - Acquires pessimistic locks on both users
   - Validates safeguards (all pass)
   - Updates target.role = MODERATOR
   - Saves to database
   - (Not a demotion, so no token invalidation)

6. Response: 200 OK with updated user info
```

### Scenario: Demoted User's Token Rejected

```
1. User has token with role = MODERATOR
2. Admin demotes user to NORMAL
   - AdminService calls tokenBlacklistService.invalidateUserTokens(userId)
   - Records: userInvalidationTimes[userId] = currentTime

3. User's next request (using old token):
   - JwtAuthFilter validates JWT (valid signature)
   - JwtAuthFilter checks user-level blacklist:
     - tokenBlacklistService.isUserTokenInvalidated(userId, issuedAt)
     - issuedAt (before demotion) < invalidationTime (demotion time)
     - Returns TRUE → token is invalidated

4. Filter throws TokenRevokedException
5. Response: 401 Unauthorized

6. User must re-authenticate to get new token with role = NORMAL
```

---

## Safeguards Implementation

### Five Privilege Escalation Guards

**File: `AdminService.java`**

```java
private void validateRoleChange(User actor, User target,
        UserRole actorRole, UserRole targetCurrentRole, UserRole newRole) {

    // 1. Cannot grant role higher than your own
    if (newRole.outranks(actorRole)) {
        throw new IllegalArgumentException(
            "Cannot grant a role higher than your own");
    }

    // 2. Cannot modify users at equal or higher rank (unless self)
    if (!actor.getId().equals(target.getId()) &&
        targetCurrentRole.hasRankAtLeast(actorRole)) {
        throw new IllegalArgumentException(
            "Cannot modify role of users at equal or higher rank");
    }

    // 3. Cannot demote the last admin
    if (targetCurrentRole == UserRole.ADMIN && newRole != UserRole.ADMIN) {
        long adminCount = userRepository.countByRole(UserRole.ADMIN);
        if (adminCount <= 1) {
            throw new IllegalArgumentException(
                "Cannot demote the last administrator");
        }
    }
}
```

### ModerationService Safeguards

```java
// 4. Cannot timeout administrators
if (target.getRole() == UserRole.ADMIN) {
    throw new IllegalArgumentException("Cannot timeout administrators");
}

// 5. Moderators cannot timeout other moderators
if (actor.getRole() == UserRole.MODERATOR &&
    target.getRole() == UserRole.MODERATOR) {
    throw new IllegalArgumentException(
        "Moderators cannot timeout other moderators");
}
```

### Safeguard Matrix

| Actor | Target | Action | Result |
|-------|--------|--------|--------|
| ADMIN | NORMAL | Promote to MODERATOR | ✅ Allowed |
| ADMIN | MODERATOR | Promote to ADMIN | ✅ Allowed |
| ADMIN | ADMIN | Demote to MODERATOR | ✅ If not last admin |
| MODERATOR | NORMAL | Promote to MODERATOR | ❌ Cannot grant equal |
| MODERATOR | NORMAL | Timeout | ✅ Allowed |
| MODERATOR | MODERATOR | Timeout | ❌ Cannot timeout equal |
| MODERATOR | ADMIN | Any | ❌ Cannot modify higher |
| Any | ADMIN | Timeout | ❌ Cannot timeout admin |

---

## Token Invalidation Strategy

### Two-Level Blacklist

```
┌─────────────────────────────────────────────────────────────┐
│              TokenBlacklistService                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│   Token-Level Blacklist          User-Level Blacklist       │
│   ┌─────────────────┐            ┌────────────────────┐     │
│   │ tokenHash → exp │            │ userId → timestamp │     │
│   └─────────────────┘            └────────────────────┘     │
│                                                              │
│   Used for: LOGOUT               Used for: DEMOTION         │
│   (invalidate specific token)    (invalidate all tokens)    │
│                                                              │
│   Check: Is this token           Check: Was token issued    │
│          in blacklist?                  before invalidation?│
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Why Two Levels?

**Token-level** (logout):
- User logs out on one device
- Only that device's token invalidated
- Other sessions continue working

**User-level** (demotion):
- User demoted from MODERATOR to NORMAL
- ALL their tokens must be invalidated immediately
- Prevents using old token with elevated privileges

### Cleanup Strategy

```java
// Token blacklist: cleaned hourly
@Scheduled(fixedRate = 3600000)
public void cleanupExpired() {
    blacklist.entrySet().removeIf(e -> e.getValue() < now);
}

// User blacklist: cleaned every 6 hours
@Scheduled(fixedRate = 21600000)
public void cleanupUserInvalidations() {
    long ttl = refreshTokenExpiry + 3600000; // 7 days + 1 hour
    long cutoff = System.currentTimeMillis() - ttl;
    userInvalidationTimes.entrySet().removeIf(e -> e.getValue() < cutoff);
}
```

---

## Summary

| Aspect | Decision | Rationale |
|--------|----------|-----------|
| Role storage | JWT access token | Fast authorization, no DB per request |
| Refresh token | No role | Forces DB lookup for current role |
| Role comparison | Explicit rank field | Avoids ordinal() fragility |
| URL authorization | RoleHierarchy bean | Clean rules, implicit inheritance |
| Race prevention | Pessimistic locking | Correctness over throughput |
| Token revocation | Two-level blacklist | Logout vs demotion scenarios |
| Timeout check | Service layer | Consistent enforcement point |
| Cleanup | Scheduled tasks | Prevents memory leaks |
