# Research: API Response Data Leakage Fix

## Clarifications Resolved
- **isAuthor computation**: Option A selected - Add `currentUserId` parameter to `CommentResponse.fromEntity()`, compute `isAuthor` like CommentTreeNode

## Spec Ambiguities
**NONE** - Spec is well-defined and internally consistent.

## Spec-to-Code Mapping

| Requirement | Current State | Files to Modify |
|-------------|---------------|-----------------|
| CommentResponse Long ids | VULNERABLE | `dto/comment/CommentResponse.java` |
| PlannerResponse userId | LEAKAGE | `dto/planner/PlannerResponse.java`, FE types/schemas |
| UserDto Long id | LEAKAGE | `dto/UserDto.java` |
| Parent UUID lookup | MISSING | `entity/PlannerComment.java`, migration V031 |
| CommentService responses | NEEDS UPDATE | `service/CommentService.java` |
| Frontend dead userId | CLEANUP | `usePlannerSyncAdapter.ts`, schemas, types |

## Spec-to-Pattern Mapping

| Requirement | Reference Pattern | Source |
|-------------|-------------------|--------|
| UUID instead of Long | `CommentTreeNode.java` line 70 | `comment.getPublicId()` |
| No user ID in responses | `CommentTreeNode.java` (uses isAuthor) | Remove AuthorInfo.id |
| Batch parent lookup | `CommentService.java` lines 75-81 | Batch load parent publicIds |
| DTO with publicId | `CommentTreeNode.fromEntity()` | Update all fromEntity() methods |

## Pattern Enforcement

| File to Modify | MUST Read First | Pattern to Copy |
|----------------|-----------------|-----------------|
| `V031__add_comment_parent_public_id.sql` | `V030__add_planner_first_published_at.sql` | Migration structure |
| `CommentResponse.java` | `CommentTreeNode.java` | UUID exposure, isAuthor pattern |
| `CommentService.java` | Same file lines 75-90 | Batch query pattern |

## Pattern Copy Analysis

**Reference: CommentTreeNode.java (Golden Standard)**
- Uses `UUID id` via `comment.getPublicId()`
- No user ID exposed (uses `isAuthor` boolean computed server-side)
- Null-safe author handling (line 51): checks author != null before extracting fields
- Sentinel user (id=0) handled via batch user loading

**CommentResponse Transformation:**
- `Long id` → `UUID id` (from `comment.getPublicId()`)
- `Long parentCommentId` → `UUID parentCommentId` (from parent's publicId)
- `AuthorInfo.Long id` → REMOVE, add `boolean isAuthor` (computed from currentUserId)
- Add `currentUserId` parameter to `fromEntity()` method signature

## Gap Analysis

**Currently Missing:**
- `parentPublicId` field on `PlannerComment` entity
- Migration V031 for `parent_public_id` column
- `isAuthor` boolean in `CommentResponse.AuthorInfo`
- `currentUserId` param in `CommentResponse.fromEntity()`

**Needs Modification:**
- `CommentResponse.fromEntity()` signature and all callers
- `UserDto` to use `user.getPublicId()`
- `PlannerResponse` to remove `userId`
- Frontend schemas/types to match

**Can Reuse:**
- Batch loading pattern from `CommentService.getCommentTree()`
- Sentinel user handling already in place

## Testing Requirements

### Manual UI Tests
1. View published planner comments - verify UUID ids in Network tab
2. Post comment - verify response has UUID id, no Long
3. Reply to comment - verify parentCommentId is parent's UUID
4. Login and check own comment - verify isAuthor: true
5. View as guest - verify all isAuthor: false
6. Save planner - verify no userId in response
7. Check login response - verify UserDto uses publicId

### Automated Tests
- CommentResponse.id maps to comment.getPublicId()
- CommentResponse.parentCommentId maps to parent's publicId (null for top-level)
- CommentResponse.AuthorInfo has no id field, has isAuthor
- UserDto uses user.getPublicId()
- PlannerResponse has no userId field
- Frontend schemas validate successfully

### Edge Cases
- Deleted parent comment: parentPublicId still populated
- Sentinel user: empty username fields, correct isAuthor
- Top-level comment: parentCommentId is null
- Guest viewing: all isAuthor: false

## Technical Constraints
- Flyway migrations immutable - create V031
- MySQL BINARY(16) for UUID storage
- Batch loading required to avoid N+1
- Breaking change - simultaneous BE/FE deploy required
