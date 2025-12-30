# Code Documentation: Handle Backend Critical Issues

## Summary

This task addressed 5 critical backend security and reliability issues:
1. Race condition in vote counting (concurrent updates losing votes)
2. PII exposure in public API (email addresses visible)
3. Hardcoded validation limits (not configurable)
4. Generic validation error codes (client can't distinguish error types)
5. Noisy SSE cleanup logs (INFO level for routine cleanup)

## Files Changed

### Phase 1-5: Implementation

| File | Changes |
|------|---------|
| `PlannerRepository.java` | Added 4 atomic operations: `incrementUpvotes`, `decrementUpvotes`, `incrementDownvotes`, `decrementDownvotes` using `@Modifying @Query` with atomic SQL |
| `PlannerService.java` | Refactored `castVote()` to use atomic operations instead of read-modify-write pattern |
| `PublicPlannerResponse.java` | Changed `fromEntity()` to always return `"Anonymous"` for `authorName` |
| `PlannerContentValidator.java` | Refactored to use `@Value` injection for size limits; added granular error codes |
| `PlannerSseService.java` | Changed `log.info()` to `log.debug()` for SSE cleanup messages |
| `application.properties` | Added `planner.content.max-size-bytes=51200` and `planner.note.max-size-bytes=1024` |
| `application-test.properties` | Added test-specific size limits |
| `GlobalExceptionHandler.java` | Implemented hybrid error handling: user-fixable codes exposed, structural codes hidden |

### Phase 6: Tests

| File | Test Count | Coverage |
|------|------------|----------|
| `PlannerRepositoryTest.java` | 10 tests | Atomic increment/decrement operations |
| `PlannerServiceTest.java` | 8 tests | castVote with atomic operations |
| `PublicPlannerResponseTest.java` | 7 tests | Anonymous author mapping |
| `PlannerContentValidatorTest.java` | 59+ tests | All validation error codes |

## Key Implementation Details

### 1. Atomic Vote Operations

```java
// PlannerRepository.java - Atomic UPDATE prevents race conditions
@Modifying(clearAutomatically = true)
@Query("UPDATE Planner p SET p.upvotes = p.upvotes + 1 WHERE p.id = :id")
void incrementUpvotes(@Param("id") UUID id);
```

**Why atomic?** Read-modify-write pattern loses updates under concurrent load. Atomic UPDATE increments in a single database operation.

### 2. PII Protection

```java
// PublicPlannerResponse.java - No email ever exposed
public static PublicPlannerResponse fromEntity(Planner planner) {
    return new PublicPlannerResponse(
        planner.getId(),
        planner.getTitle(),
        "Anonymous",  // Always Anonymous - no user.getEmail()
        ...
    );
}
```

**Why Anonymous?** Public planner list should not reveal author identity. Email is PII that should never be exposed.

### 3. Configurable Size Limits

```java
// PlannerContentValidator.java - @Value injection
@Value("${planner.content.max-size-bytes:51200}")
private int maxContentSizeBytes;

@Value("${planner.note.max-size-bytes:1024}")
private int maxNoteSizeBytes;
```

**Why configurable?** Allows adjustment without code changes. Different limits for production vs. test.

### 4. Hybrid Error Handling

```java
// GlobalExceptionHandler.java
private static final Set<String> USER_FACING_ERROR_CODES = Set.of(
    "EMPTY_CONTENT",      // User can provide content
    "SIZE_EXCEEDED",      // User can reduce content size
    "MALFORMED_JSON"      // User can fix JSON syntax
);

// User-fixable errors: return specific code
// Structural errors: return generic "VALIDATION_ERROR"
```

**Why hybrid?** Balance between:
- **User experience**: Users need to know if content is too large vs. empty
- **Security**: Don't reveal API schema structure (prevents probing attacks)

### 5. SSE Log Level

```java
// PlannerSseService.java - DEBUG level for cleanup
log.debug("Completed cleanup for SSE emitter");
```

**Why DEBUG?** SSE cleanup happens routinely and creates log noise at INFO level.

## Verification Results

| Feature | Verified By |
|---------|-------------|
| F1: Race condition fix | PlannerRepositoryTest: atomic operations |
| F2: PII protection | PublicPlannerResponseTest: always Anonymous |
| F3: Configurable limits | PlannerContentValidatorTest: @Value injection |
| F4: Granular error codes | PlannerContentValidatorTest: specific codes |
| F5: SSE log level | Manual verification: log.debug() |

## Test Results

```
Tests run: 136, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Security Considerations

### Error Code Exposure (Hybrid Approach)

| Error Code | Exposed to Client | Reason |
|------------|-------------------|--------|
| `EMPTY_CONTENT` | ✅ Yes | User can fix by providing content |
| `SIZE_EXCEEDED` | ✅ Yes | User can fix by reducing size |
| `MALFORMED_JSON` | ✅ Yes | User can fix by correcting JSON |
| `MISSING_REQUIRED_FIELD` | ❌ No | Reveals required fields |
| `UNKNOWN_FIELD` | ❌ No | Reveals valid field names |
| `INVALID_CATEGORY` | ❌ No | Reveals valid categories |
| `INVALID_FIELD_TYPE` | ❌ No | Reveals type constraints |
| `INVALID_ID_REFERENCE` | ❌ No | Reveals ID validation logic |

Structural errors return generic `VALIDATION_ERROR` with message "Invalid planner content structure".

## Issues Resolved During Implementation

1. **Test mocks out of sync**: `PlannerServiceTest.castVote*` tests were using old mocks for `plannerRepository.save()`. Fixed to mock `findById()` for re-fetch after atomic operations.

2. **GlobalExceptionHandler discarding error codes**: Original implementation returned generic "INVALID_JSON" for all validation errors, negating granular error code work. Fixed with hybrid approach.
