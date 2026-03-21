---
paths:
  - "backend/src/**/*.java"
---

# Comment Standards

## Good Comments

A comment is justified only if it serves one of these purposes:

| Category | Purpose | Example |
|----------|---------|---------|
| Why | Intent, decision, or constraint | `// Pessimistic lock to prevent TOCTOU race` |
| Warning | Dangerous or surprising behavior | `// NOT thread-safe` |
| Workaround | Intentional hack that shouldn't be "fixed" away | `// Workaround: Spring ignores @Valid on nested lists` |
| Cross-ref | Spec, RFC, OWASP, or related code | `// @see OWASP XSS Prevention Cheat Sheet` |
| TODO | Actionable item with tracker link | `// TODO: #142 — add rate limiting` |
| Clarification | Non-obvious nullability, threading, or invariant | `// Nullable: null means unrestricted` |

## Forbidden Comments

| Pattern | Example | Why |
|---------|---------|-----|
| Restates code | `// Set published to false` above `setPublished(false)` | Code is self-documenting |
| Change history | `(BUG FIX: was missing)`, `(moved from X)`, `(added for Y)` | Git tracks changes |
| Tombstone | `// Old FooTests removed (lines 100-200 deleted)` | Git tracks deletions |
| Commented-out code | `// @PostMapping("/report") ...` | Delete it; git has it |
| Redundant negation | `(NEVER use X)` when the positive choice implies it | Positive choice is sufficient |
| Parenthetical restatement | `getUsers() // (fetches all active users)` | LLM hedging noise; the name says it |

## Javadoc

### When to write

| Target | Required | Skip |
|--------|----------|------|
| Public/protected class | Yes | Simple DTOs with self-explanatory fields |
| Public/protected method with business logic | Yes | Getters, setters, delegating one-liners |
| Private method with non-obvious algorithm | Yes | Trivial helpers |
| Class-level file header | Yes, for services/controllers | Entities, configs with no domain logic |

### Format

**Class header** — what the service does and its responsibilities:

```java
/**
 * Service for comment operations.
 * Handles CRUD, threading, and upvote toggle with atomic counters.
 */
```

**Method** — description, `@param`, `@return`, `@throws`:

```java
/**
 * Get user and check if restricted (timed out or banned).
 * Returns the user to avoid duplicate DB queries.
 *
 * @param userId the user ID
 * @return the User entity
 * @throws UserNotFoundException if user not found
 * @throws UserTimedOutException if user is currently timed out
 * @throws UserBannedException   if user is currently banned
 */
```

### Rules

- Description: what it does and why
- `@param` / `@return`: only when type signature alone is ambiguous
- `@throws`: document all checked and business exceptions
- No `@author`, `@version`, `@since` — git tracks these
- No empty tag descriptions (`@param x` with no text)
- Third-person voice: "Gets the label", not "Get the label"

## LLM Anti-Pattern: Compulsive Parentheticals

LLMs add parenthetical micro-explanations to every noun:

```java
// BAD — parenthetical restates what the code/name already says
planner.setPublished(false); // Also unpublish (sets publish flag to false)
userService.deleteAccount(userId); // Delete user (removes from database)

// BAD — parenthetical adds changelog
checkRestrictions(user); // (BUG FIX: was missing)

// GOOD — no parenthetical needed, or the comment explains WHY
planner.setPublished(false); // Unpublishing cascades to all subscribers
```

If you catch yourself writing `noun (explanation)` in a comment, delete the parenthetical. If the explanation is important enough to keep, promote it to a standalone comment.
