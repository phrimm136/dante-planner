# Task: Planner Config Loading Robustness Pass

## Description

Complete the planner configuration loading system to ensure data integrity and robustness. The implementation addresses three gaps identified in the current system:

**Gap 1: Hardcoded MD version in frontend**
- Four UI components use hardcoded `mdVersion={6}` instead of `config.mdCurrentVersion`
- When backend updates to MD7, these components will still render MD6 data
- Components affected: StartBuffSection, StartBuffEditPane, StartGiftSummary, StartGiftEditPane

**Gap 2: Schema validation gap**
- Frontend schema accepts empty `rrAvailableVersions` arrays
- An empty array indicates backend misconfiguration and should fail fast
- Add `.min(1)` constraint to reject empty arrays

**Gap 3: Missing backend validation**
- Backend accepts any `contentVersion` when creating planners
- No validation against configured versions allows invalid planners

**Version Support Model:**
- Mirror Dungeon supports multiple versions over time (6 now, 7 in ~2 months)
- Existing planners with older versions remain readable
- **New planner creation requires the LATEST version only**

**Business Rules for New Planner Creation:**
- MIRROR_DUNGEON: Must equal `mdCurrentVersion` (currently 6). Old versions (5) and future versions (7) rejected.
- REFRACTED_RAILWAY: Must be in `rrAvailableVersions` (currently 1 or 5). User can select which.

**Note:** RR feature is for future implementation - not exposed to users yet.

## Research

Completed - see `research.md` for full analysis. Key findings:
- No ambiguities in spec
- Pattern source: `PlannerContentValidator.java` for backend validator
- Config values already in `application.properties:46-51`
- Frontend hook `usePlannerConfig.ts` already fetches config correctly

## Scope

Read for context:
- `frontend/src/hooks/usePlannerConfig.ts` - config fetching pattern
- `frontend/src/hooks/usePlannerConfig.test.ts` - existing test structure
- `frontend/src/routes/PlannerMDNewPage.tsx` - config usage (line 205), hardcoded values (lines 739, 747, 759, 768)
- `backend/src/main/java/org/danteplanner/backend/validation/PlannerContentValidator.java` - validator pattern to copy
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` - where validation is called
- `backend/src/main/resources/application.properties` - config source (lines 46-51)

## Target Code Area

**Backend - Create:**
- `backend/src/main/java/org/danteplanner/backend/validation/ContentVersionValidator.java`
- `backend/src/test/java/org/danteplanner/backend/validation/ContentVersionValidatorTest.java`

**Backend - Modify:**
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` (inject validator, add call)

**Frontend - Modify:**
- `frontend/src/schemas/PlannerSchemas.ts:525` (add `.min(1)`)
- `frontend/src/hooks/usePlannerConfig.test.ts` (update empty array expectation)
- `frontend/src/routes/PlannerMDNewPage.tsx:739,747,759,768` (replace hardcoded values)

## System Context (Senior Thinking)

- **Feature domain:** Planner (MD) - version configuration subsystem
- **Core files in this domain:**
  - Frontend: `hooks/usePlannerConfig.ts`, `schemas/PlannerSchemas.ts`
  - Backend: `controller/PlannerController.java` (getConfig), `dto/planner/PlannerConfigResponse.java`
- **Cross-cutting concerns touched:**
  - Validation (Zod frontend, Jakarta backend)
  - Configuration (@Value injection from application.properties)
  - Error handling (PlannerValidationException, GlobalExceptionHandler)

Reference: `docs/architecture-map.md` - "Planner Config" row in Backend Core Files table

## Impact Analysis

- **Files being modified:**
  - `PlannerService.java` - HIGH impact (all CRUD flows through it, but change is 2 lines)
  - `PlannerSchemas.ts` - MEDIUM impact (config validation, but change is 1 character)
  - `PlannerMDNewPage.tsx` - LOW impact (page-isolated, 4 value replacements)

- **What depends on these files:**
  - PlannerService: All planner endpoints (create, update, import)
  - PlannerSchemas: usePlannerConfig hook, planner data validation

- **Potential ripple effects:**
  - Intentional: Creating planner with old contentVersion will fail
  - Safe: Existing planners remain readable (validation only on create)
  - Automatic: Frontend fetches version from backend, sync is automatic

- **High-impact files to watch:**
  - `PlannerService.java` - ensure validation before content validation
  - Do NOT modify `PlannerController.java` - validation belongs in service layer

## Risk Assessment

- **Edge cases:**
  - Frontend/backend config sync: Unlikely issue - frontend fetches from backend
  - Backend config invalid: Fail fast with schema validation
  - Version rollout: Backend updates first, frontend auto-fetches new version

- **Backward compatibility:**
  - Existing planners NOT affected (validation only on create)
  - Old planners with version 5 remain accessible
  - When MD7 releases: update `application.properties`, users create new planners

- **Performance concerns:** None - single integer comparison per create request

- **Security considerations:**
  - Prevents creation with arbitrary contentVersion values
  - Closes potential data integrity attack vector

## Testing Guidelines

### Manual UI Testing

1. Start dev servers (`yarn dev` and `./mvnw spring-boot:run`)
2. Navigate to `/planner/md/new`
3. Open browser DevTools → Network tab
4. Create a new planner with default settings
5. Click Save/Create button
6. Verify request body contains `"contentVersion": 6` (from config, not hardcoded)
7. Verify planner created successfully (201 response)

**API Testing - Version Enforcement:**
1. Use curl to POST to `/api/planner/md` with `contentVersion: 5`
2. Verify response is 400 with `INVALID_CONTENT_VERSION`
3. POST with `contentVersion: 6`
4. Verify response is 201 Created
5. POST with `contentVersion: 7`
6. Verify response is 400 with `INVALID_CONTENT_VERSION`

### Automated Functional Verification

- [ ] Backend rejects MD planner with old version (5): Returns 400 INVALID_CONTENT_VERSION
- [ ] Backend rejects MD planner with future version (7): Returns 400 INVALID_CONTENT_VERSION
- [ ] Backend accepts MD planner with current version (6): Returns 201 Created
- [ ] Backend rejects RR planner with unavailable version (3): Returns 400 INVALID_CONTENT_VERSION
- [ ] Backend accepts RR planner with available version (1 or 5): Returns 201 Created
- [ ] Frontend schema rejects empty rrAvailableVersions: Zod validation fails
- [ ] Frontend uses config.mdCurrentVersion: Request contains dynamic value

### Edge Cases

- [ ] Null contentVersion: Backend returns 400 "Content version is required"
- [ ] Negative contentVersion: Backend returns 400 INVALID_CONTENT_VERSION
- [ ] Old MD version (5): Backend returns 400 - new planners must use latest
- [ ] Future MD version (7): Backend returns 400 - version not yet released
- [ ] Empty rrAvailableVersions from backend: Frontend fails with Zod validation error

### Integration Points

- [ ] usePlannerConfig hook: Continues to work with updated schema
- [ ] StartBuffSection/StartGiftSummary: Receive mdVersion from config
- [ ] PlannerService.createPlanner: Validates before content validation
- [ ] GlobalExceptionHandler: Returns user-friendly error for INVALID_CONTENT_VERSION

## Implementation Order

1. Create `ContentVersionValidator.java` (copy pattern from PlannerContentValidator)
2. Inject validator into `PlannerService.java`
3. Add validation call in `createPlanner()` before content validation
4. Create `ContentVersionValidatorTest.java`
5. Update `PlannerSchemas.ts` - add `.min(1)`
6. Update `usePlannerConfig.test.ts` - fix empty array expectation
7. Update `PlannerMDNewPage.tsx` - replace 4 hardcoded values
8. Run all tests and manual verification
