# Research: Planner Config Loading Robustness Pass

## Spec Ambiguities

**NONE FOUND** - Spec is complete with explicit requirements:
- Business rules for version enforcement are clear
- Four hardcoded locations explicitly identified
- Backend validation timing specified
- Schema constraint requirement explicit

---

## Spec-to-Code Mapping

| Requirement | File | Change |
|-------------|------|--------|
| Gap 1: Hardcoded mdVersion | `routes/PlannerMDNewPage.tsx:739,747,759,768` | Replace `mdVersion={6}` → `config.mdCurrentVersion` |
| Gap 2: Empty array validation | `schemas/PlannerSchemas.ts:525` | Add `.min(1)` to rrAvailableVersions |
| Gap 3: Backend validation | `service/PlannerService.java` | Inject new validator, call before content validation |
| New file needed | `validation/ContentVersionValidator.java` | Create version validator component |

---

## Spec-to-Pattern Mapping

| Requirement | Pattern Source | What to Copy |
|-------------|----------------|--------------|
| Backend validator | `PlannerContentValidator.java` | @Component, @Value injection, error factories |
| Service integration | `PlannerService.java` constructor | Constructor injection pattern |
| Error handling | `PlannerValidationException` | Use existing exception with new error code |
| Frontend schema | `PlannerSchemas.ts` | Zod array constraint pattern |

---

## Pattern Enforcement (MANDATORY)

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `ContentVersionValidator.java` | `PlannerContentValidator.java` | @Component, @Value injection, constructor, error methods |
| `ContentVersionValidatorTest.java` | `PlannerServiceTest.java` | Unit test structure, validation test cases |

---

## Existing Utilities

| Category | Location | Status |
|----------|----------|--------|
| Backend validators | `validation/PlannerContentValidator.java` | Reuse error patterns |
| Config source | `application.properties:46-51` | Already has version configs |
| Frontend hook | `hooks/usePlannerConfig.ts` | Already fetches config |
| Frontend schema | `schemas/PlannerSchemas.ts:519-526` | Modify existing schema |

---

## Gap Analysis

### Currently Missing
- `ContentVersionValidator.java` - new validator
- `ContentVersionValidatorTest.java` - unit tests

### Needs Modification
- `PlannerSchemas.ts:525` - add `.min(1)`
- `PlannerService.java` - inject validator, add validation call
- `PlannerMDNewPage.tsx:739,747,759,768` - replace hardcoded values
- `usePlannerConfig.test.ts` - update empty array test expectation

### Can Reuse
- `PlannerValidationException` for errors
- `GlobalExceptionHandler` for response mapping
- @Value injection pattern
- Constructor injection pattern

---

## Testing Requirements

### Manual UI Tests

1. **Version binding verification**
   - Open DevTools Network on `/planner/md/new`
   - Create planner, verify request has `contentVersion: 6` from config

2. **Backend validation - reject old**
   - POST to `/api/planner/md` with `contentVersion: 5`
   - Verify 400 INVALID_CONTENT_VERSION

3. **Backend validation - accept current**
   - POST with `contentVersion: 6`
   - Verify 201 Created

4. **Backend validation - reject future**
   - POST with `contentVersion: 7`
   - Verify 400 INVALID_CONTENT_VERSION

### Automated Unit Tests

**ContentVersionValidator:**
- MD validation: rejects 5, accepts 6, rejects 7
- RR validation: accepts 1 and 5, rejects 3
- Null version: returns error with message
- Validator injectable into service

**PlannerService Integration:**
- createPlanner rejects MD with old version
- createPlanner accepts MD with current version
- Validation runs before content parsing

**Frontend Schema:**
- PlannerConfigSchema rejects empty rrAvailableVersions array

---

## Impact Analysis

### File Risk Levels

| File | Impact | Reason |
|------|--------|--------|
| `PlannerService.java` | HIGH | All CRUD flows through it, but change is 2 lines |
| `PlannerSchemas.ts` | MEDIUM | Config validation, but change is 1 character |
| `PlannerMDNewPage.tsx` | LOW | Page-isolated, 4 value replacements |

### Ripple Effects

- **Intentional**: New planners with old versions rejected
- **Safe**: Existing planners remain readable
- **Automatic sync**: Frontend fetches version from backend

---

## Technical Constraints

- Backend validator: Use `@Component`, not `@Service`
- Injection: Constructor only (no field injection)
- Config: Use `@Value("${property}")` pattern
- Errors: Throw `PlannerValidationException` with error code
- Timing: Validate BEFORE content JSON parsing
- Frontend: Use Zod `.min(1)` syntax

---

## Implementation Order

1. Create `ContentVersionValidator.java` (copy pattern from PlannerContentValidator)
2. Inject validator into `PlannerService.java`
3. Add validation call in `createPlanner()` before content validation
4. Create `ContentVersionValidatorTest.java`
5. Update `PlannerSchemas.ts` - add `.min(1)`
6. Update `usePlannerConfig.test.ts` - fix empty array expectation
7. Update `PlannerMDNewPage.tsx` - replace 4 hardcoded values
8. Run all tests and manual verification
