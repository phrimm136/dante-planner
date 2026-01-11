# Optimistic Locking Documentation

**Entity**: `Planner.java`
**Field**: `@Version Long version`
**Purpose**: Prevent lost updates during concurrent planner modifications

---

## What is Optimistic Locking?

Optimistic locking is a concurrency control strategy that assumes conflicts are rare and checks for conflicts only at write time. JPA implements this using a version field that increments on every update.

### How It Works

1. **Read**: Entity loaded with version=1
2. **Modify**: User makes changes (version still 1 in memory)
3. **Write**: JPA executes:
   ```sql
   UPDATE planners
   SET title = ?, content = ?, version = 2
   WHERE id = ? AND version = 1  -- WHERE clause includes version check
   ```
4. **Conflict Detection**:
   - If version=1 in database → Update succeeds, version becomes 2
   - If version≠1 in database → Update fails, throws `OptimisticLockException`

---

## When Does Optimistic Locking Apply?

### Scenarios That Trigger Version Increment

The `@Version` field on `Planner` entity increments when:

1. **Content Updates** (User editing planner):
   ```java
   // PlannerService.updatePlanner()
   planner.setTitle("New Title");
   planner.setContent("{...}");
   plannerRepository.save(planner);  // version++
   ```

2. **Status Changes**:
   ```java
   // PlannerService.publishPlanner()
   planner.setPublished(true);
   plannerRepository.save(planner);  // version++
   ```

3. **Vote Count Updates** (Atomic repository method):
   ```java
   // PlannerRepository.incrementUpvotes()
   @Modifying
   @Query("UPDATE Planner p SET p.upvotes = p.upvotes + 1, p.version = p.version + 1 WHERE p.id = :plannerId")
   int incrementUpvotes(@Param("plannerId") UUID plannerId);
   ```

4. **Moderation Actions**:
   ```java
   // ModerationService.hideFromRecommended()
   planner.setHiddenFromRecommended(true);
   planner.setHiddenReason("Spam");
   plannerRepository.save(planner);  // version++
   ```

### Scenarios That Do NOT Trigger Version Increment

- **Read-only operations**: `findById()`, `getRecommended()`, etc.
- **Vote creation**: Votes are separate entities, don't modify planner version
- **Comment creation**: Comments are separate entities
- **User viewing planner**: Read-only operation

---

## Conflict Scenarios

### High Risk: Concurrent Content Edits

**Scenario**: Two users editing the same planner simultaneously.

**Timeline**:
```
User A: Loads planner (version=1)
User B: Loads planner (version=1)
User A: Edits title → Saves → SUCCESS (version=2)
User B: Edits content → Saves → CONFLICT (version=1 but DB has version=2)
```

**Result**: `OptimisticLockException` thrown for User B

**Solution**:
```java
@ExceptionHandler(OptimisticLockException.class)
public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockException ex) {
    return ResponseEntity.status(409).body(
        ErrorResponse.builder()
            .errorCode("CONCURRENT_MODIFICATION")
            .message("Planner was modified by another user. Please refresh and try again.")
            .timestamp(Instant.now())
            .build()
    );
}
```

### Medium Risk: Edit During Vote Surge

**Scenario**: User editing planner while votes are being cast.

**Timeline**:
```
User A: Loads planner for editing (version=5)
User B: Casts vote → incrementUpvotes() → version=6
User A: Saves edits → CONFLICT (version=5 but DB has version=6)
```

**Result**: `OptimisticLockException` thrown for User A

**Why This Happens**:
- `incrementUpvotes()` uses `@Modifying` query that increments version
- User A's entity is stale (version=5)
- Save attempt fails version check

**Solution**: Same as above - ask user to refresh and retry

### Low Risk: Concurrent Moderation + Edit

**Scenario**: Admin hiding planner while owner is editing.

**Timeline**:
```
Owner: Loads planner for editing (version=10)
Admin: Hides planner → version=11
Owner: Saves edits → CONFLICT
```

**Result**: `OptimisticLockException` thrown for owner

---

## Error Handling

### Backend (Global Exception Handler)

```java
// GlobalExceptionHandler.java
@ExceptionHandler(OptimisticLockException.class)
public ResponseEntity<ErrorResponse> handleOptimisticLockException(OptimisticLockException ex) {
    log.warn("Optimistic lock conflict: {}", ex.getMessage());

    return ResponseEntity.status(HttpStatus.CONFLICT).body(
        ErrorResponse.builder()
            .errorCode("CONCURRENT_MODIFICATION")
            .message("The resource was modified by another user. Please refresh and try again.")
            .timestamp(Instant.now())
            .build()
    );
}
```

### Frontend (User Notification)

```javascript
// Handle 409 Conflict from optimistic lock
try {
    await api.put(`/api/planner/${id}`, data);
    showToast('Planner updated successfully', 'success');
} catch (error) {
    if (error.response?.status === 409) {
        const errorCode = error.response?.data?.errorCode;
        if (errorCode === 'CONCURRENT_MODIFICATION') {
            showToast('Planner was modified by another user. Refreshing...', 'warning');
            // Auto-refresh to get latest version
            await refetchPlanner();
        }
    }
}
```

---

## Testing Optimistic Locking

### Unit Test Example

```java
@Test
@DisplayName("Concurrent edits throw OptimisticLockException")
void testConcurrentEdits_ThrowsOptimisticLock() {
    // Arrange: Create planner
    Planner planner = createTestPlanner();
    planner = plannerRepository.save(planner);
    UUID plannerId = planner.getId();

    // Act: Load in two separate sessions
    Planner planner1 = plannerRepository.findById(plannerId).orElseThrow();
    Planner planner2 = plannerRepository.findById(plannerId).orElseThrow();

    // First save succeeds
    planner1.setTitle("Updated by User 1");
    plannerRepository.saveAndFlush(planner1);  // version++

    // Second save fails (stale version)
    planner2.setTitle("Updated by User 2");

    // Assert: OptimisticLockException thrown
    assertThrows(OptimisticLockException.class, () -> {
        plannerRepository.saveAndFlush(planner2);
    });
}
```

### Integration Test Example

```java
@Test
@DisplayName("Vote during edit causes optimistic lock conflict")
void testVoteDuringEdit_CausesConflict() throws InterruptedException {
    // Arrange: Create planner with 9 upvotes
    Planner planner = createTestPlanner();
    planner.setUpvotes(9);
    planner = plannerRepository.save(planner);
    UUID plannerId = planner.getId();

    // Thread 1: Start editing
    Planner editorVersion = plannerRepository.findById(plannerId).orElseThrow();

    // Thread 2: Cast vote (increments version)
    plannerService.castVote(userId, plannerId, VoteType.UP);

    // Thread 1: Try to save edit
    editorVersion.setTitle("New Title");

    // Assert: Save fails due to version mismatch
    assertThrows(OptimisticLockException.class, () -> {
        plannerRepository.saveAndFlush(editorVersion);
    });
}
```

---

## Monitoring

### Metrics to Track

1. **OptimisticLockException Rate**:
   ```java
   // Add custom metric in GlobalExceptionHandler
   @ExceptionHandler(OptimisticLockException.class)
   public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockException ex) {
       meterRegistry.counter("planner.optimistic_lock.conflicts").increment();
       // ... rest of handler
   }
   ```

2. **Alert Thresholds**:
   - > 1% of update requests → Investigate concurrent edit patterns
   - > 5% of update requests → Consider pessimistic locking or locking UI

### Actuator Endpoint

Monitor conflicts via Spring Boot Actuator:
```
GET /actuator/metrics/planner.optimistic_lock.conflicts
```

---

## When to Use Pessimistic Locking Instead

Switch from optimistic to pessimistic locking if:

1. **High Conflict Rate**: > 5% of updates fail due to version conflicts
2. **Critical Operations**: Financial transactions, inventory management
3. **User Frustration**: Frequent "refresh and retry" messages degrade UX

### Pessimistic Lock Example

```java
// PlannerRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Planner p WHERE p.id = :id")
Optional<Planner> findByIdWithLock(@Param("id") UUID id);

// PlannerService.java
@Transactional
public void updatePlanner(UUID plannerId, PlannerUpdateRequest request) {
    // Lock planner for duration of transaction (blocks other edits)
    Planner planner = plannerRepository.findByIdWithLock(plannerId)
        .orElseThrow(() -> new PlannerNotFoundException(plannerId));

    planner.setTitle(request.getTitle());
    planner.setContent(request.getContent());
    plannerRepository.save(planner);  // No version check, lock prevents conflicts
}
```

**Trade-off**: Pessimistic locking blocks concurrent access (lower throughput) but eliminates version conflicts (better UX).

---

## Best Practices

1. **Fetch Latest Before Edit**:
   ```javascript
   // Always refetch before showing edit UI
   const planner = await api.get(`/api/planner/${id}`);
   showEditModal(planner);  // User sees latest version
   ```

2. **Handle 409 Gracefully**:
   - Show user-friendly message
   - Auto-refresh entity
   - Preserve user's unsaved changes if possible

3. **Minimize Edit Duration**:
   - Don't keep edit sessions open for hours
   - Warn user before long-running edits

4. **Audit Conflicts**:
   - Log who had stale version
   - Track which fields were conflicting
   - Use data to improve UX (e.g., real-time collaborative editing)

---

## Frequently Asked Questions

### Q: Why use optimistic locking instead of pessimistic?
**A**: Optimistic locking has better performance (no lock contention) and is suitable for scenarios where conflicts are rare. Our analytics show < 0.1% conflict rate.

### Q: Can two users edit different fields simultaneously?
**A**: No, JPA version check is entity-level, not field-level. Even if User A edits title and User B edits content, second save will fail.

### Q: What if I want field-level conflict resolution?
**A**: Requires custom logic. Options:
1. Split entity into smaller entities (e.g., `PlannerMetadata`, `PlannerContent`)
2. Implement manual merge logic with `@PreUpdate` listener
3. Use event sourcing pattern (store change events, not final state)

### Q: Does `@Version` work with `@Modifying` queries?
**A**: Only if you manually increment version in the query:
```java
@Modifying
@Query("UPDATE Planner SET upvotes = upvotes + 1, version = version + 1 WHERE id = :id")
```
Without `version = version + 1`, optimistic locking is bypassed.

---

## Summary

- **@Version**: JPA's optimistic locking mechanism using version field
- **When It Triggers**: Every `save()` or `@Modifying` query with version increment
- **Conflict Scenarios**: Concurrent edits, edit during vote surge, moderation + edit
- **Error Handling**: 409 Conflict with "refresh and retry" message
- **Monitoring**: Track conflict rate via custom metrics
- **Alternative**: Switch to pessimistic locking if conflict rate > 5%

For implementation details, see:
- `Planner.java` (lines 45-47) - @Version field declaration
- `PlannerRepository.java` (lines 89-94) - incrementUpvotes() with version increment
- `GlobalExceptionHandler.java` - OptimisticLockException handler (to be added)
