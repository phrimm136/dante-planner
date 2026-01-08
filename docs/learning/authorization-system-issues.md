# Authorization System - Issues and Resolutions

This document explains the issues discovered during the role-based authorization system implementation, why each was problematic, and how it was resolved.

---

## Table of Contents

1. [CRITICAL-1: TOCTOU Race Condition](#critical-1-toctou-race-condition)
2. [CRITICAL-2: Enum Ordinal Fragility](#critical-2-enum-ordinal-fragility)
3. [CRITICAL-3: Token Invalidation Persistence](#critical-3-token-invalidation-persistence)
4. [HIGH-1: Missing Database Index](#high-1-missing-database-index)
5. [HIGH-4: Redundant Database Query](#high-4-redundant-database-query)
6. [H1: Unbounded Memory Growth](#h1-unbounded-memory-growth)
7. [H2: Underutilized Partial Index](#h2-underutilized-partial-index)

---

## CRITICAL-1: TOCTOU Race Condition

### The Problem

TOCTOU (Time-Of-Check-Time-Of-Use) race condition in `AdminService.changeRole()`. When two admins attempt to modify the same user's role simultaneously:

```
Admin A: Read user (role = MODERATOR, admin count = 2)
Admin B: Read user (role = MODERATOR, admin count = 2)
Admin A: Promote to ADMIN, save
Admin B: Demote to NORMAL ← Uses stale "admin count = 2" check!
```

Admin B's check passed because it saw 2 admins, but after Admin A's promotion there were 3 admins. Worse, if both were demoting the last admin, both checks would pass and leave zero admins.

### Why This Matters

- **Security bypass**: Privilege escalation safeguards could be circumvented
- **System lockout**: Could end up with zero administrators
- **Data corruption**: Role state becomes inconsistent

### The Solution: Pessimistic Locking

**File: `UserRepository.java`**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<User> findWithLockByIdAndDeletedAtIsNull(Long id);
```

**File: `AdminService.java`**

```java
@Transactional
public User changeRole(Long actorId, Long targetId, UserRole newRole) {
    // Both users fetched with row-level lock
    User actor = userRepository.findWithLockByIdAndDeletedAtIsNull(actorId)
            .orElseThrow(...);
    User target = userRepository.findWithLockByIdAndDeletedAtIsNull(targetId)
            .orElseThrow(...);

    // Now safeguard checks are atomic with the update
    // ...
}
```

### Why This Works

`PESSIMISTIC_WRITE` acquires a database row-level lock. The second transaction must wait until the first completes:

```
Admin A: Lock user row, read, check, update, release lock
Admin B: [BLOCKED waiting for lock]
Admin B: Lock user row, read (sees updated state), check, proceed
```

### Trade-off

Pessimistic locking reduces throughput, but role changes are infrequent admin operations where correctness trumps performance.

---

## CRITICAL-2: Enum Ordinal Fragility

### The Problem

Initial implementation used `enum.ordinal()` for rank comparison:

```java
public enum UserRole {
    NORMAL,    // ordinal = 0
    MODERATOR, // ordinal = 1
    ADMIN      // ordinal = 2
}

// Comparison used ordinal position
if (newRole.ordinal() > actorRole.ordinal()) {
    throw new IllegalArgumentException("Cannot grant higher role");
}
```

### Why This Matters

If someone later adds a role or reorders the enum:

```java
public enum UserRole {
    GUEST,     // ordinal = 0 ← NEW
    NORMAL,    // ordinal = 1 ← Was 0!
    MODERATOR, // ordinal = 2 ← Was 1!
    ADMIN      // ordinal = 3 ← Was 2!
}
```

All rank comparisons silently break. A NORMAL user (ordinal 1) would now outrank what was previously MODERATOR (ordinal 1).

### The Solution: Explicit Rank Field

**File: `UserRole.java`**

```java
public enum UserRole {
    NORMAL("NORMAL", 1),
    MODERATOR("MODERATOR", 2),
    ADMIN("ADMIN", 3);

    private final String value;
    private final int rank;

    UserRole(String value, int rank) {
        this.value = value;
        this.rank = rank;
    }

    public boolean hasRankAtLeast(UserRole other) {
        return this.rank >= other.rank;
    }

    public boolean outranks(UserRole other) {
        return this.rank > other.rank;
    }
}
```

### Why This Works

- Rank values are explicit and immutable
- Adding new roles requires explicit rank assignment
- Reordering enum constants doesn't affect comparisons
- Self-documenting: `ADMIN.outranks(MODERATOR)` reads clearly

---

## CRITICAL-3: Token Invalidation Persistence

### The Problem

When a user is demoted, their tokens are invalidated:

```java
public void invalidateUserTokens(Long userId) {
    userInvalidationTimes.put(userId, System.currentTimeMillis());
}
```

But if the demoted user logs in again (re-authenticates), the invalidation entry was never cleared. Their new tokens would still be rejected because `issuedAt < invalidationTime`.

### Why This Matters

- Demoted users could never successfully use the system again
- Re-authentication didn't restore access
- Support tickets for "I can't log in after being demoted and re-promoted"

### The Solution: Clear on Re-authentication

**File: `AuthenticationFacade.java`**

```java
public TokenPair authenticateOAuthUser(String provider, String idToken) {
    // ... validate OAuth ...

    // Clear any previous token invalidation (they're re-authenticating)
    tokenBlacklistService.clearUserInvalidation(user.getId());

    // Generate new tokens
    String accessToken = tokenGenerator.generateAccessToken(
        user.getId(), user.getEmail(), user.getRole());
    // ...
}
```

### Why This Works

When a user re-authenticates:
1. Their invalidation entry is cleared
2. New tokens are issued with current timestamp
3. The new `issuedAt` is after the (now-cleared) invalidation time
4. User can access the system with their current role

---

## HIGH-1: Missing Database Index

### The Problem

V013 migration added `timeout_until` column but no index:

```sql
ALTER TABLE users ADD COLUMN timeout_until TIMESTAMP WITH TIME ZONE;
```

Queries filtering by timeout would do full table scans.

### Why This Matters

- O(n) scan for every timeout check in moderation dashboard
- Performance degrades as user count grows
- Admin operations become slow

### The Solution: Partial Index

**File: `V014__add_timeout_index.sql`**

```sql
CREATE INDEX idx_users_timeout_until ON users(timeout_until)
WHERE timeout_until IS NOT NULL;
```

### Why Partial Index?

- Most users (99%+) have `timeout_until = NULL`
- Only timed-out users need fast lookup
- Partial index is smaller and faster than full index
- Only indexes rows where condition is true

---

## HIGH-4: Redundant Database Query

### The Problem

`PlannerService` checked timeout and then re-fetched the user:

```java
private void checkUserNotTimedOut(Long userId) {
    User user = userRepository.findById(userId).orElseThrow(...);
    if (user.isTimedOut()) {
        throw new UserTimedOutException(...);
    }
    // User object discarded!
}

public PlannerResponse createPlanner(Long userId, ...) {
    checkUserNotTimedOut(userId);
    User user = userRepository.findById(userId).orElseThrow(...); // Second query!
    // ...
}
```

### Why This Matters

- 2 queries where 1 suffices
- Database round-trip overhead
- Unnecessary load on connection pool

### The Solution: Return User from Check

```java
private User getUserAndCheckNotTimedOut(Long userId) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
    if (user.isTimedOut()) {
        throw new UserTimedOutException(userId, user.getTimeoutUntil());
    }
    return user; // Reuse this!
}

public PlannerResponse createPlanner(Long userId, ...) {
    User user = getUserAndCheckNotTimedOut(userId); // Single query
    // Use 'user' directly
}
```

---

## H1: Unbounded Memory Growth

### The Problem

`TokenBlacklistService.userInvalidationTimes` map had no cleanup:

```java
private final ConcurrentHashMap<Long, Long> userInvalidationTimes = new ConcurrentHashMap<>();

public void invalidateUserTokens(Long userId) {
    userInvalidationTimes.put(userId, System.currentTimeMillis());
    // Entry lives forever if user never logs in again!
}
```

### Why This Matters

- Each demoted user adds an entry that never expires
- Memory usage grows unbounded over time
- Long-running servers eventually OOM

### The Solution: Scheduled TTL-based Cleanup

```java
@Value("${jwt.refresh-token-expiry:604800000}")
private long refreshTokenExpiry;

@Scheduled(fixedRate = 21600000) // Every 6 hours
public void cleanupUserInvalidations() {
    // TTL = refresh token expiry + 1 hour buffer
    long ttl = refreshTokenExpiry + 3600000;
    long cutoff = System.currentTimeMillis() - ttl;
    userInvalidationTimes.entrySet().removeIf(entry -> entry.getValue() < cutoff);
}
```

### Why This TTL Value?

- Refresh token is the longest-lived token (7 days default)
- After refresh expiry, user must re-authenticate anyway
- Adding 1 hour buffer for clock skew safety
- Entries older than TTL can safely be removed

---

## H2: Underutilized Partial Index

### The Problem

V014 created a partial index, but no queries used it:

```sql
-- Index exists but nothing queries it!
CREATE INDEX idx_users_timeout_until ON users(timeout_until)
WHERE timeout_until IS NOT NULL;
```

### Why This Matters

- Index consumes disk space and slows writes
- Provides no benefit if queries don't exploit it
- Technical debt with no return

### The Solution: Add Query Method and Endpoint

**File: `UserRepository.java`**

```java
List<User> findByTimeoutUntilAfterAndDeletedAtIsNull(Instant now);
```

**File: `ModerationService.java`**

```java
@Transactional(readOnly = true)
public List<User> getTimedOutUsers() {
    return userRepository.findByTimeoutUntilAfterAndDeletedAtIsNull(Instant.now());
}
```

**File: `ModerationController.java`**

```java
@GetMapping("/users/timed-out")
public ResponseEntity<List<TimeoutResponse>> getTimedOutUsers() {
    List<User> timedOutUsers = moderationService.getTimedOutUsers();
    // ...
}
```

### Why This Works

The generated query becomes:

```sql
SELECT * FROM users
WHERE timeout_until > ? AND deleted_at IS NULL
```

The partial index on `timeout_until WHERE timeout_until IS NOT NULL` accelerates this query because:
- Only scans indexed rows (timed-out users)
- Provides ordered access for range comparison (`> ?`)

---

## Key Takeaways

| Pattern | Lesson |
|---------|--------|
| Pessimistic locking | Use for low-frequency, correctness-critical operations |
| Explicit rank fields | Never use ordinal() for enum comparisons |
| Clear on re-auth | Stateful exceptions to stateless auth need cleanup paths |
| Partial indexes | Only create if queries will exploit the condition |
| Return values | Return fetched objects from validation methods to avoid re-fetch |
| Scheduled cleanup | In-memory state needs TTL-based eviction from day one |
