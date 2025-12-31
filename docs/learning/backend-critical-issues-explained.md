# Backend Critical Issues - Detailed Explanation

This document explains the changes made to fix 5 critical backend issues related to security and reliability.

---

## Table of Contents

1. [Issue 1: Race Condition in Vote Counting](#issue-1-race-condition-in-vote-counting)
2. [Issue 2: PII Exposure in Public API](#issue-2-pii-exposure-in-public-api)
3. [Issue 3: Hardcoded Validation Limits](#issue-3-hardcoded-validation-limits)
4. [Issue 4: Generic Validation Error Codes](#issue-4-generic-validation-error-codes)
5. [Issue 5: Noisy SSE Cleanup Logs](#issue-5-noisy-sse-cleanup-logs)

---

## Issue 1: Race Condition in Vote Counting

### The Problem

When multiple users vote on the same planner simultaneously, votes could be lost. This happened because the old code used a "read-modify-write" pattern:

```
Thread A: Read planner (upvotes = 5)
Thread B: Read planner (upvotes = 5)
Thread A: Increment to 6, save
Thread B: Increment to 6, save  ← Thread A's vote is lost!
```

Both threads read the same value (5), both increment to 6, and the final result is 6 instead of 7.

### The Solution: Atomic Database Operations

Instead of reading, modifying, and writing in the application layer, we now use atomic SQL UPDATE queries that increment directly in the database:

**File: `PlannerRepository.java`**

```java
@Modifying(clearAutomatically = true)
@Query("UPDATE Planner p SET p.upvotes = p.upvotes + 1 WHERE p.id = :id")
int incrementUpvotes(@Param("id") UUID id);

@Modifying(clearAutomatically = true)
@Query("UPDATE Planner p SET p.upvotes = p.upvotes - 1 WHERE p.id = :id AND p.upvotes > 0")
int decrementUpvotes(@Param("id") UUID id);
```

**Key Points:**
- `@Modifying` tells Spring this query changes data
- `clearAutomatically = true` ensures the persistence context is cleared after the update
- The decrement query has `AND p.upvotes > 0` to prevent negative counts
- Returns `int` (number of rows affected) for verification

**File: `PlannerService.java`**

The `castVote()` method was refactored to use these atomic operations:

```java
// Old (race condition prone):
planner.setUpvotes(planner.getUpvotes() + 1);
plannerRepository.save(planner);

// New (atomic):
plannerRepository.incrementUpvotes(plannerId);
```

After the atomic operation, we re-fetch the planner to get the updated counts for the response.

### Why This Works

The database handles the increment as a single atomic operation. Even with concurrent requests, each UPDATE sees the current value and increments it correctly:

```
Thread A: UPDATE ... SET upvotes = upvotes + 1 (5 → 6)
Thread B: UPDATE ... SET upvotes = upvotes + 1 (6 → 7)  ← Correct!
```

---

## Issue 2: PII Exposure in Public API

### The Problem

The public planner list endpoint (`GET /api/planner/md/published`) was exposing user email addresses to anyone browsing the list. Email is personally identifiable information (PII) that should never be exposed without user consent.

### The Solution: Always Return "Anonymous"

**File: `PublicPlannerResponse.java`**

```java
public static PublicPlannerResponse fromEntity(Planner planner) {
    return new PublicPlannerResponse(
        planner.getId(),
        planner.getTitle(),
        "Anonymous",  // Never expose user email
        planner.getCategory(),
        planner.getSelectedKeywords(),
        planner.getUpvotes(),
        planner.getDownvotes(),
        planner.getCreatedAt()
    );
}
```

**Key Points:**
- The `authorName` field is always `"Anonymous"` - no conditional logic
- We never even access `planner.getUser().getEmail()`
- This is a deliberate privacy-by-design decision

### Why Not Use a Display Name?

We could have added a `displayName` field to the User entity, but:
1. That would require database schema changes
2. Users would need a way to set their display name (UI work)
3. "Anonymous" is the safest default for privacy

If display names are needed later, the field is already there - just change what `fromEntity()` returns.

---

## Issue 3: Hardcoded Validation Limits

### The Problem

The `PlannerContentValidator` had hardcoded values for maximum content size and note size:

```java
// Old code
private static final int MAX_CONTENT_SIZE = 51200;  // Hardcoded
private static final int MAX_NOTE_SIZE = 1024;      // Hardcoded
```

This makes it impossible to adjust limits without redeploying the application.

### The Solution: Configuration via Properties

**File: `application.properties`**

```properties
# Planner validation limits
planner.content.max-size-bytes=51200
planner.note.max-size-bytes=1024
```

**File: `PlannerContentValidator.java`**

```java
@Value("${planner.content.max-size-bytes:51200}")
private int maxContentSizeBytes;

@Value("${planner.note.max-size-bytes:1024}")
private int maxNoteSizeBytes;
```

**Key Points:**
- `@Value` injects the property value at startup
- `:51200` is the default value if the property isn't set
- Constructor injection was used to allow testing with different values

**File: `application-test.properties`**

```properties
# Smaller limits for testing
planner.content.max-size-bytes=51200
planner.note.max-size-bytes=1024
```

### Benefits

- Adjust limits per environment (dev vs. production)
- No code changes needed to modify limits
- Tests can use different values via constructor injection

---

## Issue 4: Generic Validation Error Codes

### The Problem

All validation errors returned the same generic error:

```json
{
  "code": "INVALID_JSON",
  "message": "Invalid planner content"
}
```

The frontend couldn't tell if the content was too large, empty, or had invalid structure.

### The Solution: Granular Error Codes with Hybrid Exposure

We implemented 8 different error codes in the validator:

| Error Code | Meaning |
|------------|---------|
| `EMPTY_CONTENT` | Content is null/empty/blank |
| `SIZE_EXCEEDED` | Content exceeds max size |
| `MALFORMED_JSON` | Invalid JSON syntax |
| `MISSING_REQUIRED_FIELD` | Required field missing |
| `UNKNOWN_FIELD` | Unrecognized field in JSON |
| `INVALID_CATEGORY` | Category not 5F/10F/15F |
| `INVALID_FIELD_TYPE` | Wrong data type for field |
| `INVALID_ID_REFERENCE` | Reference to non-existent ID |

**File: `PlannerContentValidator.java`**

```java
private PlannerValidationException emptyContentError() {
    return new PlannerValidationException("EMPTY_CONTENT", "Content cannot be empty");
}

private PlannerValidationException sizeExceededError(String context, int actual, int limit) {
    return new PlannerValidationException("SIZE_EXCEEDED",
        String.format("%s size %d exceeds limit %d", context, actual, limit));
}
```

### The Hybrid Approach

Not all error codes should be exposed to clients. Some reveal internal API structure:

**File: `GlobalExceptionHandler.java`**

```java
private static final Set<String> USER_FACING_ERROR_CODES = Set.of(
    "EMPTY_CONTENT",      // User can provide content
    "SIZE_EXCEEDED",      // User can reduce size
    "MALFORMED_JSON"      // User can fix JSON syntax
);

@ExceptionHandler(PlannerValidationException.class)
public ResponseEntity<ErrorResponse> handlePlannerValidation(PlannerValidationException ex) {
    if (USER_FACING_ERROR_CODES.contains(ex.getErrorCode())) {
        // Expose specific error to help user fix the issue
        return ResponseEntity.badRequest()
            .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    // Hide structural errors to prevent schema probing
    return ResponseEntity.badRequest()
        .body(new ErrorResponse("VALIDATION_ERROR", "Invalid planner content structure"));
}
```

### Why Hide Structural Errors?

Exposing errors like `MISSING_REQUIRED_FIELD` or `UNKNOWN_FIELD` helps attackers understand your API schema:

- They can probe to find all valid field names
- They can discover which fields are required
- They can enumerate valid category values

By returning a generic `VALIDATION_ERROR` for structural issues, we prevent this information disclosure while still helping users with fixable issues.

---

## Issue 5: Noisy SSE Cleanup Logs

### The Problem

Server-Sent Events (SSE) connections regularly disconnect and get cleaned up. Each cleanup logged an INFO message:

```
INFO  PlannerSseService: Completed cleanup for SSE emitter
```

In production with many users, this created thousands of log lines per hour, making it hard to find important logs.

### The Solution: Change to DEBUG Level

**File: `PlannerSseService.java`**

```java
// Old
log.info("Completed cleanup for SSE emitter");

// New
log.debug("Completed cleanup for SSE emitter");
```

**Key Points:**
- DEBUG level is typically disabled in production
- Developers can enable DEBUG when debugging SSE issues
- INFO level remains clean for important events

---

## Test Coverage

### PlannerRepositoryTest (10 tests)

Tests the atomic operations directly against an H2 database:

- `incrementUpvotes_IncreasesCount`: Basic increment works
- `incrementUpvotes_ConcurrentCalls_AllSucceed`: Multiple increments don't lose updates
- `decrementUpvotes_DecreasesCount`: Basic decrement works
- `decrementUpvotes_AtZero_StaysAtZero`: Can't go negative
- `incrementDownvotes_IncreasesCount`: Basic increment works
- `decrementDownvotes_DecreasesCount`: Basic decrement works
- `decrementDownvotes_AtZero_StaysAtZero`: Can't go negative
- (Similar tests for downvotes)

### PlannerServiceTest - CastVoteTests (8 tests)

Tests the vote service logic with mocked repository:

- `castVote_NewUpvote_IncrementsCount`: First upvote increments correctly
- `castVote_NewDownvote_IncrementsCount`: First downvote increments correctly
- `castVote_ChangeUpToDown_AdjustsCounts`: Switching vote type adjusts both counts
- `castVote_ChangeDownToUp_AdjustsCounts`: Switching the other direction
- `castVote_RemoveUpvote_DecrementsCount`: Removing vote decrements
- `castVote_RemoveDownvote_DecrementsCount`: Removing vote decrements
- `castVote_SameVoteType_NoChange`: Voting same type is idempotent
- `castVote_PlannerNotFound_ThrowsException`: Error handling

### PublicPlannerResponseTest (7 tests)

Tests the DTO mapping and anonymization:

- `fromEntity_NullEmail_ReturnsAnonymous`: Null email returns Anonymous
- `fromEntity_EmptyEmail_ReturnsAnonymous`: Empty email returns Anonymous
- `fromEntity_ValidEmail_ReturnsAnonymous`: Valid email still returns Anonymous
- `fromEntity_MapsAllFieldsCorrectly`: All other fields map correctly

### PlannerContentValidatorTest (59+ tests)

Comprehensive tests for all validation rules:

- Valid content tests (with all required/optional fields)
- Missing required field tests (each field)
- Invalid category tests
- Wrong field type tests
- Unknown field tests
- Content size limit tests
- Note size limit tests
- Sinner index validation tests
- Start buff validation tests
- Start gift validation tests
- Floor selection gift tests
- Edge cases

---

## Summary Table

| Issue | Problem | Solution | Files Changed |
|-------|---------|----------|---------------|
| Race Condition | Lost concurrent votes | Atomic SQL UPDATE queries | Repository, Service |
| PII Exposure | Email visible in public API | Always return "Anonymous" | PublicPlannerResponse |
| Hardcoded Limits | Can't adjust without deploy | @Value injection from properties | Validator, application.properties |
| Generic Errors | Client can't distinguish errors | Granular codes with hybrid exposure | Validator, GlobalExceptionHandler |
| Noisy Logs | SSE cleanup floods logs | Change to DEBUG level | PlannerSseService |

---

## Key Takeaways

1. **Atomic operations are essential for concurrent updates** - Never use read-modify-write for counters
2. **Privacy by design** - Default to not exposing data, add exposure only when needed
3. **Configuration over code** - Use properties for values that might need adjustment
4. **Error codes should balance UX and security** - Help users fix issues without revealing internals
5. **Log levels matter** - Routine operations should be DEBUG, not INFO
