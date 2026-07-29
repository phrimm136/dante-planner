# Execution Plan: Gesellschaft Username Generation

## Planning Gaps

**Non-blocking gap:**
- **Association Curation**: Final list pending user decision
- **Mitigation**: Proceed with placeholder list; finalize before Step 11 (testing)

---

## Execution Overview

- **Feature**: Auto-generated immutable usernames (`Faust-{Assoc}-{suffix}`)
- **Scope**: 11 files (4 create, 7 modify)
- **Phases**: 5 (Database → Services → API → Frontend → Tests)

---

## Dependency Analysis

| File | Impact | Depends On | Used By |
|------|--------|------------|---------|
| `User.java` | Medium | - | UserDto, UserService, all user queries |
| `UserService.java` | Medium | User.java | AuthController, OAuth flow |
| `PublicPlannerResponse.java` | Medium | User.java | All public planner endpoints |
| `Header.tsx` | Low | UserDto, i18n | Page-isolated |

**High-Risk:**
- `PublicPlannerResponse.java` - Breaking API change, requires atomic deploy

---

## Execution Order

### Phase 1: Database & Entity

**Step 1: Create migration**
- File: `V011__add_username_columns.sql`
- Action: CREATE
- Depends on: None
- Enables: F1

**Step 2: Update User entity**
- File: `User.java`
- Action: MODIFY (add usernameKeyword, usernameSuffix)
- Depends on: Step 1
- Enables: F1, F2

### Phase 2: Backend Services

**Step 3: Create UsernameConfig**
- File: `UsernameConfig.java`
- Action: CREATE
- Depends on: None
- Enables: F3

**Step 4: Create RandomUsernameGenerator**
- File: `RandomUsernameGenerator.java`
- Action: CREATE
- Depends on: Step 3
- Enables: F4

**Step 5: Update UserService**
- File: `UserService.java`
- Action: MODIFY (call generator in findOrCreateUser)
- Depends on: Steps 2, 4
- Enables: F5

### Phase 3: API Layer

**Step 6: Update UserDto**
- File: `UserDto.java`
- Action: MODIFY (add username fields)
- Depends on: Step 2
- Enables: F6

**Step 7: Update UserService.toDto()**
- File: `UserService.java`
- Action: MODIFY (include username in DTO)
- Depends on: Step 6
- Enables: F6

**Step 8: Update PublicPlannerResponse**
- File: `PublicPlannerResponse.java`
- Action: MODIFY (replace "Anonymous" with username)
- Depends on: Step 2
- Enables: F7

### Phase 4: Frontend

**Step 9: Create i18n files**
- Files: `association.json` × 4 languages
- Action: CREATE
- Depends on: Step 3
- Enables: F8

**Step 10: Update Header.tsx**
- File: `Header.tsx`
- Action: MODIFY (display translated username)
- Depends on: Steps 6, 9
- Enables: F8

### Phase 5: Tests

**Step 11: Unit tests for RandomUsernameGenerator**
- File: `RandomUsernameGeneratorTest.java`
- Action: CREATE
- Depends on: Step 4

**Step 12: Integration tests for UserService**
- File: `UserServiceTest.java`
- Action: MODIFY
- Depends on: Step 5

**Step 13: Manual UI testing**
- Depends on: Steps 10, 12

---

## Verification Checkpoints

| After Step | Verify |
|------------|--------|
| 1 | `./mvnw flyway:info` shows V011 pending |
| 2 | Entity compiles |
| 5 | `./mvnw test-compile` passes |
| 8 | PublicPlannerResponse builds |
| 10 | `yarn tsc` passes |
| 12 | `./mvnw test` passes |
| 13 | Manual checklist complete |

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Collision infinite loop | 28M namespace; log if >10 retries |
| API breaking change | Deploy backend + frontend atomically |
| Missing i18n key | Fallback to UsernameConfig English name |
| Concurrent registration | DB UNIQUE + retry handles race |

---

## Rollback Strategy

1. **Before migration**: Delete migration file
2. **After migration**: Create V012 to drop columns, revert code
3. **Frontend deployed**: Graceful fallback to "Anonymous"
