# Execution Plan: Remove Downvote from Planner Voting

## Planning Gaps
**NONE DETECTED** - Research is complete and comprehensive. All files identified, patterns documented, dependencies mapped.

## Execution Overview
This is a **code removal/simplification task** that eliminates downvoting from the voting system. The change is **BREAKING** and **IRREVERSIBLE** (deletes all downvote data permanently). The execution follows a strict dependency order: Database migration → Backend entity/repository/service cleanup → Frontend UI/types/i18n cleanup → Test updates.

**Key Risk:** Coordinated deployment required (backend + frontend simultaneously) to avoid enum deserialization errors.

## Dependency Analysis (Senior Thinking)

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| **V023__remove_downvotes.sql** | **HIGH** | V022 migration | All planner queries |
| **VoteType.java** | **HIGH** | None | PlannerService, PlannerVote, VoteRequest, all tests |
| **Planner.java** | **MEDIUM** | VoteType changes | PlannerRepository, PlannerService, all DTOs |
| **PlannerRepository.java** | **HIGH** | Planner.java | PlannerService, all queries |
| **PlannerService.java** | **HIGH** | All above | PlannerController, tests |
| **VoteRequest.java** | **LOW** | VoteType.java | PlannerController |
| **PlannerCardContextMenu.tsx** | **MEDIUM** | VoteDirection type | PlannerMDGesellschaftPage |
| **usePlannerVote.ts** | **MEDIUM** | VoteDirection type | PlannerCardContextMenu |
| **PlannerListTypes.ts** | **MEDIUM** | None | All planner list components |
| **i18n files (4 languages)** | **LOW** | None | PlannerCardContextMenu |
| **Test files (3 backend)** | **MEDIUM** | All service/repo changes | CI/CD pipeline |

### Ripple Effect Map
- **If VoteType.java changes** → PlannerVote, PlannerService, VoteRequest, all tests need updates
- **If Planner.java drops downvotes field** → PublicPlannerResponse still includes field (set to 0 for backward compat), repository queries simplified
- **If PlannerRepository.java removes downvote methods** → PlannerService.castVote() switch statement simplified (remove DOWN case)
- **If PlannerRepository.java net vote queries change** → Recommendation rankings recalculated (all planners with downvotes rank higher)
- **If VoteDirection type changes** → usePlannerVote.ts, PlannerCardContextMenu, all vote UI components affected
- **If i18n keys removed** → Translation loading won't fail (missing keys return fallback), but TypeScript may show warnings

### High-Risk Modifications
- **V023__remove_downvotes.sql**: Deletes all DOWN votes permanently (irreversible). **Mitigation**: Backup database before deployment, verify migration on staging first.
- **PlannerRepository.java net vote queries**: Affects ranking of ALL published planners. **Mitigation**: Document ranking change in release notes, monitor recommendation list after deployment.
- **VoteType.java enum removal**: Breaks API contract if client sends DOWN. **Mitigation**: Deploy frontend simultaneously, add API version header in future.

## Execution Order

### Phase 1: Database Schema (Irreversible Data Deletion)
1. **Create V023__remove_downvotes.sql migration**
   - Depends on: V022 migration exists
   - Enables: F1 (database schema ready for downvote-free system)
   - Actions:
     - Delete all rows from planner_votes WHERE vote_type = 'DOWN'
     - Drop downvotes column from planners table
     - Verify no indexes reference downvotes column

### Phase 2: Backend Data Layer (Entities & Enums)
2. **Modify VoteType.java enum**
   - Depends on: Step 1 (migration created)
   - Enables: F2 (backend only accepts UP votes)
   - Actions:
     - Remove `DOWN("DOWN")` enum constant
     - Keep UP, keep Jackson annotations (@JsonCreator, @JsonValue)
     - fromValue() will throw IllegalArgumentException for "DOWN"

3. **Modify Planner.java entity**
   - Depends on: Step 1 (migration created)
   - Enables: F3 (entity matches new schema)
   - Actions:
     - Remove `private Integer downvotes = 0;` field (line 92-93)
     - Keep upvotes field unchanged
     - All builder/getter/setter for downvotes removed automatically (Lombok)

### Phase 3: Backend Repository Layer (Atomic Operations & Queries)
4. **Modify PlannerRepository.java**
   - Depends on: Step 3 (Planner.java updated)
   - Enables: F4 (queries use upvotes-only logic), F5 (atomic downvote methods removed)
   - Actions:
     - Remove incrementDownvotes() method (lines 148-150)
     - Remove decrementDownvotes() method (lines 159-161)
     - Update all queries: Replace `(p.upvotes - p.downvotes)` → `p.upvotes` in:
       - findRecommendedPlanners() (line 76)
       - findRecommendedPlannersByCategory() (line 96)
       - trySetRecommendedNotified() (line 288)
       - findRecommendedPlannersWithSearch() (line 231)
       - findRecommendedPlannersByCategoryWithSearch() (line 259)
     - Keep incrementUpvotes(), decrementUpvotes() unchanged

### Phase 4: Backend Service Layer (Business Logic)
5. **Modify PlannerService.java**
   - Depends on: Step 4 (PlannerRepository updated)
   - Enables: F6 (vote logic simplified), F7 (notification threshold uses upvotes only)
   - Actions:
     - Remove `case VoteType.DOWN:` branch from castVote() (lines 490-492)
     - Keep `case VoteType.UP:` branch unchanged
     - Simplify net vote calculation (lines 479-481, 498-500): `netBefore/netAfter` now just use upvotes
     - Update javadoc for castVote() to reflect upvote-only system
     - Update line 482 comment: "Atomic increment for upvote"

6. **Modify VoteRequest.java DTO**
   - Depends on: Step 2 (VoteType.java updated)
   - Enables: F8 (API contract reflects upvote-only)
   - Actions:
     - Update class javadoc to document upvote-only voting
     - Remove mention of "DOWN" from JavaDoc examples
     - Keep @NotNull constraint on voteType field

### Phase 5: Frontend Types & Hooks (Data Layer)
7. **Modify PlannerListTypes.ts**
   - Depends on: None (frontend changes independent)
   - Enables: F9 (frontend types match backend enum)
   - Actions:
     - Change VoteDirection from `'UP' | 'DOWN'` to `'UP'` (line 42)
     - This forces TypeScript compile errors in components using 'DOWN'

8. **Modify usePlannerVote.ts**
   - Depends on: Step 7 (VoteDirection type updated)
   - Enables: F10 (hook only accepts UP votes)
   - Actions:
     - Update hook documentation to reflect upvote-only system
     - Remove any references to DOWN in comments/JSDoc
     - No code changes needed (already uses VoteDirection type)

### Phase 6: Frontend UI Components (Presentation Layer)
9. **Modify PlannerCardContextMenu.tsx**
   - Depends on: Step 7 (VoteDirection type updated)
   - Enables: F11 (downvote button removed from UI)
   - Actions:
     - Remove handleDownvote() function (lines 163-186)
     - Remove downvote DropdownMenuItem (lines 288-304)
     - Remove ThumbsDown import (line 13)
     - Keep handleUpvote() unchanged

### Phase 7: Frontend i18n (Translation Cleanup)
10. **Modify static/i18n/EN/planner.json**
    - Depends on: Step 9 (UI no longer references these keys)
    - Enables: F12 (EN translations clean)
    - Actions:
      - Remove "downvote" key (line 192)
      - Remove "downvoted" key (line 193)
      - Keep "upvote", "upvoted", "alreadyVoted" keys

11. **Modify static/i18n/CN/planner.json**
    - Depends on: Step 9
    - Enables: F12 (CN translations clean)
    - Actions: Same as EN (remove downvote, downvoted keys)

12. **Modify static/i18n/JP/planner.json**
    - Depends on: Step 9
    - Enables: F12 (JP translations clean)
    - Actions: Same as EN (remove downvote, downvoted keys)

13. **Modify static/i18n/KR/planner.json**
    - Depends on: Step 9
    - Enables: F12 (KR translations clean)
    - Actions: Same as EN (remove downvote, downvoted keys)

### Phase 8: Backend Tests (Verification)
14. **Update PlannerServiceTest.java**
    - Depends on: Steps 2-6 (all backend logic updated)
    - Enables: T1 (service tests pass)
    - Actions:
      - Remove all VoteType.DOWN test cases
      - Remove assertions checking `.downvotes` field
      - Update net vote calculation tests to use upvotes only
      - Verify 400 Bad Request when client sends "DOWN" string

15. **Update PlannerRepositoryTest.java**
    - Depends on: Step 4 (repository updated)
    - Enables: T2 (repository tests pass)
    - Actions:
      - Remove incrementDownvotes(), decrementDownvotes() test methods
      - Update recommended planner query tests (net vote = upvotes)
      - Verify trySetRecommendedNotified() works with upvotes-only threshold

16. **Update PlannerVoteRepositoryTest.java**
    - Depends on: Step 2 (VoteType enum updated)
    - Enables: T3 (vote repository tests pass)
    - Actions:
      - Remove test cases creating VoteType.DOWN votes
      - Keep UP vote tests unchanged
      - Add test verifying only UP votes can be created

## Verification Checkpoints

### After Phase 1 (Database Migration)
- **Verify F1**: Run migration on local DB
  - All DOWN votes deleted (SELECT COUNT(*) FROM planner_votes WHERE vote_type = 'DOWN' → 0)
  - downvotes column removed (DESCRIBE planners → no downvotes column)
  - Migration reversible via restore from backup only

### After Phase 4 (Backend Logic Complete)
- **Verify F2-F8**: Backend tests pass
  - PlannerServiceTest: All tests green
  - PlannerRepositoryTest: Recommended queries return correct results
  - API accepts UP, rejects DOWN with 400

### After Phase 6 (Frontend UI Complete)
- **Verify F9-F11**: TypeScript compilation succeeds
  - No compile errors referencing VoteDirection.DOWN
  - PlannerCardContextMenu renders without downvote button
  - Upvote button works (manual test in dev environment)

### After Phase 7 (i18n Complete)
- **Verify F12**: All language files clean
  - Grep for "downvote" in i18n/ returns no matches
  - UI renders in all 4 languages without missing key warnings

### After Phase 8 (Tests Complete)
- **Verify T1-T3**: All automated tests pass
  - Backend unit tests: 100% pass rate
  - Backend integration tests: Vote endpoints work correctly
  - No references to VoteType.DOWN in test code

## Risk Mitigation

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| **Irreversible data deletion** | Step 1 | Database backup MANDATORY before migration. Test on staging first. |
| **Frontend sends DOWN after backend deployed** | Steps 2-6 | Deploy frontend simultaneously. Monitor logs for IllegalArgumentException. |
| **Recommendation rankings change** | Step 4 | Document in release notes. Planners with previous downvotes rank higher (expected behavior). |
| **Jackson deserialization failure** | Step 2 | VoteType.fromValue() throws clear error. Frontend must not send DOWN. |
| **Concurrent votes during deployment** | Steps 1-6 | Deploy during low-traffic window. Consider maintenance mode. |
| **Missing i18n keys cause UI errors** | Steps 10-13 | i18next returns fallback for missing keys (safe). TypeScript may warn (acceptable). |

## Pre-Implementation Validation Gate

**BEFORE Step 1 execution, verify research completed:**

| Validation Category | Check | Blocker if Missing |
|---------------------|-------|-------------------|
| **Reference Completeness** | Read V021, V022 migration files? | ✅ YES (patterns confirmed) |
| **Contract Alignment** | VoteType enum matches backend/frontend? | ✅ YES (both use UP/DOWN strings) |
| **Dependency Resolution** | Flyway migration dependencies clear? | ✅ YES (V022 exists, V023 next) |
| **Structure Documentation** | Migration pattern documented (header + SQL)? | ✅ YES (follow V021 structure) |
| **Difference Justification** | Why delete data vs soft-delete? | ✅ YES (spec explicitly requires deletion) |

**Execution Rule**: All critical blockers resolved. Proceed with Step 1.

## Dependency Verification Steps

### After Step 4 (PlannerRepository.java modified)
- **Verify consumers**: PlannerService.castVote() compiles without errors
- **Verify queries**: findRecommendedPlanners() syntax correct (IntelliJ SQL validation)

### After Step 7 (VoteDirection type changed)
- **Verify consumers**: usePlannerVote.ts, PlannerCardContextMenu.tsx compile
- **Test integration**: No TypeScript errors referencing VoteDirection.DOWN

### After Step 13 (i18n files cleaned)
- **Verify consumers**: PlannerCardContextMenu renders in all 4 languages
- **Test integration**: No missing translation warnings in browser console

## Rollback Strategy

### If Step 1 fails (Migration)
- **Immediate action**: Restore database from backup
- **Safe point**: No code deployed yet, full rollback possible

### If Steps 2-6 fail (Backend)
- **Immediate action**: Revert backend deployment, redeploy previous version
- **Safe point**: Migration already applied - database won't have downvotes, but backend can handle missing field

### If Steps 7-13 fail (Frontend)
- **Immediate action**: Revert frontend deployment
- **Safe point**: Backend already deployed - frontend can still send UP votes, recommendation list works

### If Step 14-16 fail (Tests)
- **Immediate action**: Fix tests before deployment (blocking)
- **Safe point**: No deployment until tests pass

### Deployment Failure (Backend deployed, frontend not)
- **Symptom**: Frontend sends DOWN → 400 Bad Request
- **Immediate action**: Deploy frontend ASAP
- **Temporary workaround**: Display user-friendly error message for vote failures
