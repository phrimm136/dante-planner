# Research: Handle Backend Critical Issues

## Spec Ambiguities

None found. All requirements are clear from instructions.md and prior review.md files.

## Spec-to-Code Mapping

| Requirement | Files to Modify | Action |
|-------------|-----------------|--------|
| Race condition fix | `PlannerRepository.java`, `PlannerService.java` | Add atomic queries, refactor castVote |
| Email exposure fix | `PublicPlannerResponse.java` | Replace email extraction with "Anonymous" |
| Magic constants | `PlannerContentValidator.java`, `application.properties` | Move constants to properties |
| Granular error codes | `PlannerContentValidator.java` | Use distinct error codes per failure type |
| Log level change | `PlannerSseService.java` | Change log.info → log.debug (line 180) |

## Spec-to-Pattern Mapping

| Requirement | Pattern Source | Notes |
|-------------|----------------|-------|
| Atomic increment | New pattern (no @Modifying exists yet) | Use @Modifying + @Query for UPDATE |
| @Value injection | `PlannerService.java:50-51` | Constructor injection pattern |
| Error codes | `PlannerValidationException` already supports | Just pass different codes |
| Log levels | `PlannerSseService.java:107,126,147` | Already uses log.debug for similar ops |

## Pattern Enforcement

| File to Modify | MUST Read First | Pattern to Copy |
|----------------|-----------------|-----------------|
| `PlannerRepository.java` | `PlannerVoteRepository.java` | Query annotation style |
| `PlannerService.java` | Current castVote method | Transaction boundary |
| `PlannerContentValidator.java` | `PlannerService.java` constructor | @Value injection pattern |

## Existing Utilities

| Category | Location | Found |
|----------|----------|-------|
| Exceptions | `exception/` | PlannerValidationException (has errorCode field) |
| Validation | `validation/` | PlannerContentValidator, SinnerIdValidator, GameDataRegistry |
| Configuration | `config/` | RateLimitConfig uses @ConfigurationProperties |
| Repository | `repository/` | PlannerRepository, PlannerVoteRepository |

## Gap Analysis

### Currently Missing
- Atomic increment/decrement queries in PlannerRepository
- Configurable size limits in application.properties
- Granular validation error codes (only "INVALID_JSON" used)

### Needs Modification
- `PlannerService.castVote()` - uses read-modify-write, needs atomic ops
- `PublicPlannerResponse.fromEntity()` - extracts email prefix
- `PlannerContentValidator` - hardcoded constants, single error code
- `PlannerSseService.cleanupZombieConnections()` - logs at INFO

### Can Reuse
- `PlannerValidationException` - already supports errorCode parameter
- `@Value` injection pattern - already used in PlannerService constructor
- `log.debug` pattern - already used elsewhere in PlannerSseService

## Testing Requirements

### Unit Tests

| Test Class | Tests to Add |
|------------|--------------|
| `PlannerRepositoryTest` | incrementUpvotes, decrementUpvotes atomic behavior |
| `PlannerServiceTest` | castVote with concurrent calls (CountDownLatch) |
| `PublicPlannerResponseTest` | fromEntity with null/empty/valid email |
| `PlannerContentValidatorTest` | Distinct error codes per validation failure |

### Integration Tests

| Test Class | Tests to Add |
|------------|--------------|
| `PlannerControllerTest` | Vote endpoint returns correct counts under load |
| `PlannerControllerTest` | Published endpoint shows "Anonymous" author |

## Technical Constraints

- **Transaction boundary**: Atomic queries execute outside current transaction context, use @Modifying(flushAutomatically = true)
- **Vote floor**: Decrement must not go below 0 (use WHERE upvotes > 0)
- **Refresh entity**: After atomic update, re-fetch planner to get updated counts for response
- **Property naming**: Follow existing pattern `planner.validation.max-content-size`
- **Test properties**: Add matching entries to `application-test.properties`

## Error Code Specification

| Validation Failure | Error Code |
|--------------------|------------|
| Empty/null content | EMPTY_CONTENT |
| Size exceeded | SIZE_EXCEEDED |
| Malformed JSON | MALFORMED_JSON |
| Missing required field | MISSING_REQUIRED_FIELD |
| Unknown field | UNKNOWN_FIELD |
| Invalid category | INVALID_CATEGORY |
| Invalid field type | INVALID_FIELD_TYPE |
| Invalid ID reference | INVALID_ID_REFERENCE |
