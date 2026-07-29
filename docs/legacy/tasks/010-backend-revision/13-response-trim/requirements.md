# Task: API Response Data Leakage Fix

## Description
Fix security vulnerabilities where API responses leak internal database IDs and unnecessary data. The current implementation exposes sequential `Long` IDs (internal DB primary keys) instead of using the existing `UUID publicId` fields. This enables enumeration attacks and information disclosure.

### Critical Issues (PUBLIC endpoints)
- `CommentResponse` exposes `Long id` (comment), `Long parentCommentId`, and `AuthorInfo.Long id` (user) on the public `GET /api/planner/{id}/comments` endpoint
- Anyone can enumerate all comments and correlate user activity across the system

### Medium Issues (Auth-only endpoints)
- `PlannerResponse` contains `Long userId` which is stored by frontend but **never used** (dead code)
- `UserDto` exposes `Long id` instead of `UUID publicId`

### Design Decisions
1. **Replace** `Long` with `UUID` (breaking change, requires frontend schema updates)
2. **Convert** `parentCommentId` to `UUID` (requires parent publicId lookup)
3. **Use** `publicId` for `UserDto`
4. **Remove** `userId` from `PlannerResponse` (dead code)
5. **Remove** `AuthorInfo.id` entirely - use `isAuthor` boolean pattern from `CommentTreeNode`

### Golden Standard Reference
`CommentTreeNode.java` is the correct pattern:
- Uses `UUID id` (via `comment.getPublicId()`)
- No user ID exposed (uses `authorAssoc`, `authorSuffix`, `isAuthor` boolean)
- `isAuthor` computed server-side

## Design Clarifications
- **CommentResponse.java**: DELETE - discovered to be dead code (never instantiated)
- **Migration V031**: NOT NEEDED - comment system already uses correct patterns
- **Entity changes**: NOT NEEDED - `CommentTreeNode` already uses `publicId`
- **Deployment strategy**: Backend and frontend deployed simultaneously
- **`savedAt` field**: Deferred to separate task (not in scope)

## Scope Simplification (Planning Discovery)
The comment system already uses correct patterns:
- `CommentTreeNode` for GET (uses UUID, no user ID exposed)
- `CreateCommentResponse` for POST (uses UUID)
- `UpdateCommentResponse` for PUT (uses UUID)

**Actual work required:**
1. Remove `userId` from `PlannerResponse` (backend + frontend)
2. Change `UserDto.id` from `Long` to `UUID`
3. Delete dead `CommentResponse.java`

## Research
- [ ] Check `PlannerComment` entity for `publicId` field availability (confirmed: exists)
- [ ] Check `User` entity for `publicId` field availability (confirmed: exists)
- [ ] Verify `CommentTreeNode.java` pattern for proper implementation reference

## Scope
Read for context:
- `backend/src/main/java/org/danteplanner/backend/dto/comment/CommentTreeNode.java` (golden standard)
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerComment.java` (has `publicId`)
- `backend/src/main/java/org/danteplanner/backend/entity/User.java` (has `publicId`)
- `frontend/src/types/CommentTypes.ts` (expects UUID, no Long IDs)
- `frontend/src/schemas/CommentSchemas.ts` (validates UUID)
- `frontend/src/schemas/PlannerSchemas.ts` (ServerPlannerResponseSchema)
- `frontend/src/hooks/usePlannerSyncAdapter.ts` (stores userId but never uses)

## Target Code Area

### Backend (Modify)
- `backend/src/main/java/org/danteplanner/backend/dto/planner/PlannerResponse.java`
  - Remove `Long userId` field
  - Remove from `fromEntity()` method
- `backend/src/main/java/org/danteplanner/backend/dto/UserDto.java`
  - Change `Long id` → `UUID id`
  - Update builder to use `user.getPublicId()`
- `backend/src/main/java/org/danteplanner/backend/service/UserService.java`
  - Update `UserDto.builder()` to use `publicId`

### Backend (Delete - Dead Code)
- `backend/src/main/java/org/danteplanner/backend/dto/comment/CommentResponse.java`
  - Delete entire file (never instantiated in production)
- Remove unused imports from `CommentController.java`, `CommentService.java`, `CommentServiceTest.java` if present

### Frontend (Modify)
- `frontend/src/schemas/PlannerSchemas.ts`
  - Remove `userId` from `ServerPlannerResponseSchema`
- `frontend/src/types/PlannerTypes.ts`
  - Remove `userId` from `ServerPlannerResponse` interface
  - Remove `userId` from `PlannerMetadata` interface (or keep as optional null)
- `frontend/src/hooks/usePlannerSyncAdapter.ts`
  - Remove `userId: String(response.userId)` line

### Optional Cleanup
- `backend/src/main/java/org/danteplanner/backend/dto/planner/PublishedPlannerDetailResponse.java`
  - Consider removing `deviceId` (frontend defaults to `'published'`)

## System Context (Senior Thinking)
- Feature domain: Comment System, Planner CRUD, Authentication
- Core files from architecture-map:
  - Comment: `CommentController.java`, `CommentService.java`, `PlannerComment.java`, `dto/comment/*`
  - Planner: `PlannerController.java`, `PlannerService.java`, `dto/planner/*`
  - Auth: `AuthController.java`, `UserService.java`, `UserDto.java`
- Cross-cutting concerns:
  - DTO layer (API contract)
  - Entity layer (publicId fields exist)
  - Frontend schemas (Zod validation)
  - Frontend types (TypeScript interfaces)

## Impact Analysis
- Files being modified:
  - `CommentResponse.java` (Medium impact - comment endpoints)
  - `PlannerResponse.java` (Medium impact - planner CRUD endpoints)
  - `UserDto.java` (Low impact - login response only)
  - `CommentService.java` (Medium impact - comment CRUD)
  - Frontend schemas/types (Medium impact - requires sync with backend)
- What depends on these files:
  - `CommentController` uses `CommentResponse`
  - `PlannerController` uses `PlannerResponse`
  - `AuthController` uses `UserDto` via `UserService`
  - Frontend hooks consume these response types
- Potential ripple effects:
  - Breaking change for any frontend code expecting `Long` IDs
  - Frontend schema validation will fail until updated
- High-impact files to watch:
  - `GlobalExceptionHandler.java` - may need to handle new validation errors

## Risk Assessment
- Edge cases:
  - Comments with deleted parent: `parentCommentId` UUID lookup returns null
  - Sentinel user (id=0): Already handled in `AuthorInfo.fromUser()`
  - Top-level comments: `parentCommentId` is null (no change needed)
- Backward compatibility:
  - **BREAKING CHANGE**: Frontend must update schemas/types simultaneously
  - API consumers expecting `Long` IDs will break
- Security considerations:
  - This fix IMPROVES security by removing enumerable internal IDs
  - No new security risks introduced
- Performance:
  - `parentCommentId` UUID lookup adds one query or JOIN
  - Consider batch loading parent publicIds for list responses

## Testing Guidelines

### Manual UI Testing
1. Open browser and navigate to a published planner detail page
2. Scroll to comments section
3. Verify comments load without errors
4. Check browser Network tab - verify comment response contains UUID `id` (not numeric)
5. Post a new comment
6. Verify the comment appears with correct author info
7. Reply to an existing comment
8. Verify the reply appears nested correctly
9. Login and verify "Edit" button appears only on own comments
10. Navigate to Settings page
11. Verify login works and settings load
12. Check Network tab - verify login response has UUID `id` (not numeric)
13. Create a new planner
14. Verify save works without errors
15. Check Network tab - verify planner response has NO `userId` field

### Automated Functional Verification
- [ ] Comment list: Returns `UUID id` for each comment (not `Long`)
- [ ] Comment list: Returns `UUID parentCommentId` for replies (not `Long`)
- [ ] Comment list: `AuthorInfo` contains only `usernameKeyword` and `usernameSuffix` (no `id`)
- [ ] Comment create: Returns `UUID id` in response
- [ ] Comment upvote: Returns `UUID commentId` in response
- [ ] Planner CRUD: Response contains no `userId` field
- [ ] Login: `UserDto` contains `UUID id` (user's publicId)
- [ ] Frontend: Zod schemas validate responses without errors

### Edge Cases
- [ ] Deleted parent comment: `parentCommentId` UUID is still returned (or null if parent hard-deleted)
- [ ] Sentinel user (deleted account): `AuthorInfo` has null keyword/suffix
- [ ] Top-level comment: `parentCommentId` is null
- [ ] Guest viewing comments: Comments load, `isAuthor` is false for all, `hasUpvoted` is false

### Integration Points
- [ ] Frontend comment section: Renders comments correctly with new response shape
- [ ] Frontend planner save: Works without `userId` in response
- [ ] Frontend login flow: Auth state correct with `publicId`
- [ ] SSE notifications: Comment notifications still work (use `publicId`)
