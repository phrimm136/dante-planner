# Execution Plan: Settings Page - Username Keyword Change

## Execution Overview

Backend-first approach: DTOs → service → controller, then frontend: types → schemas → hooks → components → route. GET associations is PUBLIC, PUT requires auth. Uses `DropdownMenuRadioGroup` pattern (not Select component).

---

## Execution Order

### Phase 1: Backend Data Layer (DTOs)

1. **`dto/user/AssociationDto.java`**: Create record with keyword, displayName
   - Depends on: none
   - Enables: F1

2. **`dto/user/AssociationListResponse.java`**: Create record wrapping List<AssociationDto>
   - Depends on: Step 1
   - Enables: F1

3. **`dto/user/UpdateUsernameKeywordRequest.java`**: Create record with @NotBlank keyword
   - Depends on: none
   - Enables: F3

### Phase 2: Backend Logic Layer

4. **`config/UsernameConfig.java`**: Add `getAssociationsWithInfo()` returning List<AssociationDto>
   - Depends on: Step 1
   - Enables: F1

5. **`service/UserService.java`**: Add `updateUsernameKeyword(Long userId, String keyword)`
   - Depends on: none
   - Enables: F3
   - Validate via `isValidAssociation()`, update and save

### Phase 3: Backend Interface Layer

6. **`controller/UserController.java`**: Add GET `/api/user/associations`
   - Depends on: Steps 2, 4
   - Enables: F1

7. **`controller/UserController.java`**: Add PUT `/api/user/me/username-keyword`
   - Depends on: Steps 3, 5
   - Enables: F3
   - Use `RateLimitConfig.checkCrudLimit()`

8. **`config/SecurityConfig.java`**: Whitelist GET associations
   - Depends on: Step 6
   - Enables: F1 (public access)

### Phase 4: Frontend Data Layer

9. **`types/UserSettingsTypes.ts`**: Create Association, UpdateKeywordRequest types
   - Depends on: none
   - Enables: F1, F3

10. **`schemas/UserSettingsSchemas.ts`**: Create Zod schemas
    - Depends on: Step 9
    - Enables: F1, F3

11. **`hooks/useUserSettingsQuery.ts`**: Create useAssociationsQuery + useUpdateKeywordMutation
    - Depends on: Step 10
    - Enables: F1, F3
    - Invalidate `authQueryKeys.me` on success

### Phase 5: Frontend Interface Layer

12. **`components/settings/UsernameSection.tsx`**: Create section with dropdown + preview
    - Depends on: Step 11
    - Enables: F1, F2, F3, F4
    - Use DropdownMenuRadioGroup pattern
    - Conditional auth/unauth rendering

13. **`routes/SettingsPage.tsx`**: Create page with Suspense boundary
    - Depends on: Step 12
    - Enables: All features

### Phase 6: Integration

14. **`lib/router.tsx`**: Add `/settings` route
    - Depends on: Step 13
    - Enables: Navigation

15. **`components/Header.tsx`**: Link Settings button to /settings
    - Depends on: Step 14
    - Enables: Header navigation

### Phase 7: Tests

16. **`UserControllerTest.java`**: Test GET/PUT endpoints
    - Test: 11 items, no auth for GET, auth required for PUT, 400 for invalid

17. **`useUserSettingsQuery.test.tsx`**: Test hook behavior
    - Test: query fetches, mutation invalidates cache

18. **`UsernameSection.test.tsx`**: Test component
    - Test: auth/unauth rendering, save button state

---

## Verification Checkpoints

| After Step | Verify |
|------------|--------|
| Step 8 | curl GET associations returns 11 items (no auth) |
| Step 8 | curl PUT returns 401 without auth |
| Step 15 | Navigate to /settings, page renders |
| Step 15 | Header Settings button navigates correctly |
| Step 18 | All tests pass |

---

## Rollback Strategy

**Safe Stopping Points:**
- After Step 8: Backend complete
- After Step 15: Feature complete, pending tests

**If failure:**
- Backend: Revert DTOs, service, controller, SecurityConfig
- Frontend: Revert types, schemas, hooks, components, router
