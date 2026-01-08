# Task: Planner Comment System (Backend)

## Description

Add threaded comment functionality for published planners with upvote-only voting. Backend schema and API only (no frontend).

### Core Functionality

**Comment CRUD:**
- Users can create comments on published planners
- Comments support threaded replies (parent-child relationship)
- Maximum nesting depth of 5 levels; replies at max depth become siblings (arca.live style)
- Users can edit their own comments forever (track with `editedAt` timestamp, show `[edited]` indicator)
- Users can soft-delete their own comments
- Moderators/Admins can soft-delete any comment

**Deleted Comment Display:**
- Always show `[deleted]` placeholder for deleted comments (even if no replies)
- Content set to null, author info preserved (unless user deleted)
- Don't distinguish between author-deleted vs moderator-deleted

**Reply Restrictions:**
- Cannot reply to deleted top-level comments
- CAN reply to children of deleted comments (to preserve thread continuity)

**Planner Access Control:**
- Published planners: anyone can view and create comments
- Unpublished planners: only owner can see comments

**Comment Voting:**
- Upvote-only system (no downvotes) to reduce echo chamber effect
- Toggle behavior: clicking upvote again removes the vote (soft-delete)
- Vote type enum for future expansion (currently UP only)
- Denormalized `upvote_count` on comment for fast fetching
- Atomic increment/decrement operations to prevent race conditions
- Soft-delete on votes (matches PlannerVote pattern)

**Deleted User Handling:**
- Use existing sentinel user pattern (id=0, email='[deleted]')
- When user account is hard-deleted, reassign their comments to sentinel user
- When user account is hard-deleted, reassign their comment votes to sentinel user
- Frontend displays `[deleted]` as username when author.id === 0
- Comment content preserved (only author anonymized)

**Authorization Matrix:**

| Action | Comment Author | Planner Owner | Moderator+ |
|--------|----------------|---------------|------------|
| Edit comment | ✅ Own only | ❌ | ❌ |
| Delete comment | ✅ Own only | ❌ | ✅ Any |

Note: Planner owner has NO special comment permissions.

## Research

- Existing sentinel user pattern in `UserAccountLifecycleService.java` (SENTINEL_USER_ID = 0L)
- Existing vote reassignment in `PlannerVoteRepository.reassignVotesToSentinel()`
- Composite key pattern in `PlannerVote.java` and `PlannerVoteId.java` (implements Persistable)
- Soft-delete pattern across entities (deletedAt column)
- Atomic increment/decrement pattern in `PlannerRepository.java` (incrementUpvotes, decrementUpvotes)
- Rate limiting pattern in `RateLimitConfig.java` (Bucket4j)
- Moderation patterns in `ModerationService.java` and `ModerationController.java`
- Exception handling in `GlobalExceptionHandler.java`
- VoteType enum pattern in `entity/VoteType.java`

## Scope

Files/folders to READ for context:

```
backend/src/main/java/org/danteplanner/backend/entity/PlannerVote.java
backend/src/main/java/org/danteplanner/backend/entity/PlannerVoteId.java
backend/src/main/java/org/danteplanner/backend/entity/VoteType.java
backend/src/main/java/org/danteplanner/backend/repository/PlannerRepository.java
backend/src/main/java/org/danteplanner/backend/repository/PlannerVoteRepository.java
backend/src/main/java/org/danteplanner/backend/service/PlannerService.java (castVote method)
backend/src/main/java/org/danteplanner/backend/service/UserAccountLifecycleService.java
backend/src/main/java/org/danteplanner/backend/service/ModerationService.java
backend/src/main/java/org/danteplanner/backend/controller/ModerationController.java
backend/src/main/java/org/danteplanner/backend/config/RateLimitConfig.java
backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java
backend/src/main/resources/db/migration/V009__add_user_soft_delete.sql
```

## Target Code Area

Files to CREATE:

```
backend/src/main/resources/db/migration/V015__add_planner_comments.sql
backend/src/main/java/org/danteplanner/backend/entity/PlannerComment.java
backend/src/main/java/org/danteplanner/backend/entity/PlannerCommentVote.java
backend/src/main/java/org/danteplanner/backend/entity/PlannerCommentVoteId.java
backend/src/main/java/org/danteplanner/backend/entity/CommentVoteType.java
backend/src/main/java/org/danteplanner/backend/repository/PlannerCommentRepository.java
backend/src/main/java/org/danteplanner/backend/repository/PlannerCommentVoteRepository.java
backend/src/main/java/org/danteplanner/backend/service/CommentService.java
backend/src/main/java/org/danteplanner/backend/controller/CommentController.java
backend/src/main/java/org/danteplanner/backend/dto/comment/CreateCommentRequest.java
backend/src/main/java/org/danteplanner/backend/dto/comment/UpdateCommentRequest.java
backend/src/main/java/org/danteplanner/backend/dto/comment/CommentResponse.java
backend/src/main/java/org/danteplanner/backend/dto/comment/CommentVoteResponse.java
backend/src/main/java/org/danteplanner/backend/exception/CommentNotFoundException.java
backend/src/main/java/org/danteplanner/backend/exception/CommentForbiddenException.java
```

Files to MODIFY:

```
backend/src/main/java/org/danteplanner/backend/service/UserAccountLifecycleService.java
backend/src/main/java/org/danteplanner/backend/service/ModerationService.java
backend/src/main/java/org/danteplanner/backend/controller/ModerationController.java
backend/src/main/java/org/danteplanner/backend/config/RateLimitConfig.java
backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java
backend/src/main/resources/application.properties
```

## System Context (Senior Thinking)

- **Feature domain:** Planner Publishing & Community Features
- **Core files in this domain:**
  - `controller/PlannerController.java` (existing planner endpoints)
  - `service/PlannerService.java` (existing planner business logic)
  - `service/ModerationService.java` (moderation actions)
  - `entity/UserRole.java` (NORMAL < MODERATOR < ADMIN)
- **Cross-cutting concerns touched:**
  - Authentication (JWT via Spring Security)
  - Authorization (role-based, ownership checks)
  - Rate limiting (Bucket4j)
  - Soft-delete pattern (deletedAt columns)
  - Sentinel user pattern (id=0 for deleted users)
  - Atomic counter pattern (@Modifying queries)
  - Exception handling (GlobalExceptionHandler)

## Impact Analysis

**Files being modified:**

| File | Impact Level | Reason |
|------|--------------|--------|
| `UserAccountLifecycleService.java` | Medium | Add comment/vote reassignment to sentinel |
| `ModerationService.java` | Medium | Add comment deletion method |
| `ModerationController.java` | Medium | Add moderation endpoint |
| `RateLimitConfig.java` | Medium | Add comment bucket config |
| `GlobalExceptionHandler.java` | Low | Add new exception handlers |
| `application.properties` | Low | Add rate limit config |

**What depends on these files:**
- `UserAccountLifecycleService` → Called by `UserCleanupScheduler` for hard deletes
- `ModerationService` → Called by `ModerationController`
- `RateLimitConfig` → Used by all rate-limited endpoints
- `GlobalExceptionHandler` → Handles all REST exceptions

**Potential ripple effects:**
- Adding to `performHardDelete()` must not break existing vote reassignment
- New rate limit bucket must use same pattern as existing buckets

**High-impact files to watch:**
- `SecurityConfig.java` - May need to configure new endpoint permissions
- `GlobalExceptionHandler.java` - New exceptions must follow existing pattern

## Risk Assessment

**Edge cases not yet defined:**
- Maximum comment content length (recommended: 10,000 chars)
- Minimum comment content length (recommended: 1 char, not empty)
- Rate limit values (recommended: 10 comments/min per user)

**Performance concerns:**
- N+1 queries when loading comments with authors and vote counts
- Mitigation: Batch-load all comments, use denormalized `upvote_count`
- Mitigation: Batch queries for user vote status

**Backward compatibility:**
- No breaking changes to existing APIs
- New tables, new endpoints only

**Security considerations:**
- Rate limiting prevents comment spam
- Authorization checks in service layer (not just controller)
- Sentinel user (id=0) blocked from authentication in JwtAuthenticationFilter
- Content stored as plain text (frontend handles sanitization)

## API Endpoints

### Comment CRUD

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `GET` | `/api/planner/{plannerId}/comments` | Public* | List all comments (flat list) |
| `POST` | `/api/planner/{plannerId}/comments` | User | Create comment |
| `PUT` | `/api/comments/{commentId}` | Author | Edit comment |
| `DELETE` | `/api/comments/{commentId}` | Author | Soft-delete own comment |

*Public if planner is published; owner-only if unpublished

### Comment Voting

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `POST` | `/api/comments/{commentId}/upvote` | User | Toggle upvote |

### Moderation

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| `DELETE` | `/api/moderation/comments/{commentId}` | Moderator+ | Moderator soft-delete |

## Database Schema

### planner_comments

```sql
CREATE TABLE planner_comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    planner_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    parent_comment_id BIGINT,
    content TEXT NOT NULL,
    depth TINYINT NOT NULL DEFAULT 0,
    upvote_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    edited_at TIMESTAMP,
    deleted_at TIMESTAMP,

    CONSTRAINT fk_comment_planner FOREIGN KEY (planner_id) REFERENCES planners(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_comment_parent FOREIGN KEY (parent_comment_id) REFERENCES planner_comments(id) ON DELETE SET NULL,

    INDEX idx_comment_planner (planner_id, deleted_at),
    INDEX idx_comment_user (user_id),
    INDEX idx_comment_parent (parent_comment_id)
);
```

### planner_comment_votes

```sql
CREATE TABLE planner_comment_votes (
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    vote_type VARCHAR(10) NOT NULL DEFAULT 'UP',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,

    PRIMARY KEY (comment_id, user_id),
    CONSTRAINT fk_comment_vote_comment FOREIGN KEY (comment_id) REFERENCES planner_comments(id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_vote_user FOREIGN KEY (user_id) REFERENCES users(id),

    INDEX idx_comment_vote_comment (comment_id),
    INDEX idx_comment_vote_user (user_id)
);
```

## Entity Design

### CommentVoteType.java

```java
public enum CommentVoteType {
    UP;  // Currently only UP, enum allows future expansion (HELPFUL, INSIGHTFUL, etc.)
}
```

### PlannerComment.java Key Fields

```java
@Entity
@Table(name = "planner_comments")
public class PlannerComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "planner_id", columnDefinition = "CHAR(36)", nullable = false)
    private UUID plannerId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private int depth;  // 0-5

    @Column(name = "upvote_count", nullable = false)
    private int upvoteCount;  // Denormalized for fast fetch

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // Soft delete helpers
    public boolean isDeleted() { return deletedAt != null; }
    public void softDelete() { this.deletedAt = Instant.now(); }

    // Edit helper
    public void edit(String newContent) {
        this.content = newContent;
        this.editedAt = Instant.now();
    }
}
```

### PlannerCommentVote.java (Composite Key with Soft Delete)

```java
@Entity
@Table(name = "planner_comment_votes")
@IdClass(PlannerCommentVoteId.class)
public class PlannerCommentVote implements Persistable<PlannerCommentVoteId> {

    @Id
    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vote_type", nullable = false)
    private CommentVoteType voteType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Transient
    private boolean isNew = true;

    // Persistable implementation (same pattern as PlannerVote)

    // Soft delete helpers
    public boolean isDeleted() { return deletedAt != null; }
    public void softDelete() { this.deletedAt = Instant.now(); }
    public void reactivate() {
        this.deletedAt = null;
        this.updatedAt = Instant.now();
    }
}
```

## Repository Queries

### PlannerCommentRepository.java

```java
public interface PlannerCommentRepository extends JpaRepository<PlannerComment, Long> {

    /**
     * Find all comments for a planner (flat list for frontend tree building).
     * Includes deleted comments to preserve thread structure.
     */
    @Query("""
        SELECT c FROM PlannerComment c
        WHERE c.plannerId = :plannerId
        ORDER BY c.createdAt ASC
        """)
    List<PlannerComment> findByPlannerId(@Param("plannerId") UUID plannerId);

    /**
     * Atomically increment the upvote count for a comment.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PlannerComment c SET c.upvoteCount = c.upvoteCount + 1 WHERE c.id = :commentId")
    int incrementUpvoteCount(@Param("commentId") Long commentId);

    /**
     * Atomically decrement the upvote count for a comment.
     * Uses WHERE clause to prevent negative values.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PlannerComment c SET c.upvoteCount = c.upvoteCount - 1 WHERE c.id = :commentId AND c.upvoteCount > 0")
    int decrementUpvoteCount(@Param("commentId") Long commentId);

    /**
     * Reassign comments to sentinel user when user is hard-deleted.
     */
    @Modifying
    @Query("UPDATE PlannerComment c SET c.userId = :sentinelId WHERE c.userId = :userId")
    int reassignCommentsToSentinel(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);
}
```

### PlannerCommentVoteRepository.java

```java
public interface PlannerCommentVoteRepository extends JpaRepository<PlannerCommentVote, PlannerCommentVoteId> {

    /**
     * Find vote by comment and user (including soft-deleted).
     */
    Optional<PlannerCommentVote> findByCommentIdAndUserId(Long commentId, Long userId);

    /**
     * Check which comments user has upvoted (non-deleted votes only).
     */
    @Query("""
        SELECT v.commentId FROM PlannerCommentVote v
        WHERE v.commentId IN :commentIds AND v.userId = :userId AND v.deletedAt IS NULL
        """)
    List<Long> findUpvotedCommentIds(
        @Param("commentIds") List<Long> commentIds,
        @Param("userId") Long userId
    );

    /**
     * Reassign votes to sentinel user when user is hard-deleted.
     */
    @Modifying
    @Query("UPDATE PlannerCommentVote v SET v.userId = :sentinelId WHERE v.userId = :userId")
    int reassignVotesToSentinel(@Param("userId") Long userId, @Param("sentinelId") Long sentinelId);
}
```

## DTO Structures

### CommentResponse.java

```java
public record CommentResponse(
    Long id,
    UUID plannerId,
    Long parentCommentId,
    int depth,
    String content,           // null if deleted
    AuthorInfo author,        // sentinel user (id=0) for deleted users
    Instant createdAt,
    Instant editedAt,         // null if never edited
    boolean isDeleted,
    int upvoteCount,
    boolean hasUpvoted        // current user's vote status (false if unauthenticated)
) {
    public record AuthorInfo(
        Long id,
        String usernameKeyword,   // null if sentinel user
        String usernameSuffix     // null if sentinel user
    ) {}
}
```

### CreateCommentRequest.java

```java
public record CreateCommentRequest(
    @NotBlank
    @Size(max = 10000)
    String content,
    Long parentCommentId  // null for top-level
) {}
```

### UpdateCommentRequest.java

```java
public record UpdateCommentRequest(
    @NotBlank
    @Size(max = 10000)
    String content
) {}
```

### CommentVoteResponse.java

```java
public record CommentVoteResponse(
    Long commentId,
    int upvoteCount,
    boolean hasUpvoted
) {}
```

## Vote Flow Logic

### Toggle Upvote Flow

```
User clicks upvote on comment
    │
    ├─ Check: Existing vote record?
    │   │
    │   ├─ NO → Create new vote
    │   │       incrementUpvoteCount(commentId)
    │   │       return hasUpvoted=true
    │   │
    │   └─ YES → Check: Vote is deleted?
    │       │
    │       ├─ YES (soft-deleted) → Reactivate vote
    │       │       clear deletedAt, set updatedAt
    │       │       incrementUpvoteCount(commentId)
    │       │       return hasUpvoted=true
    │       │
    │       └─ NO (active) → Soft-delete vote
    │               set deletedAt
    │               decrementUpvoteCount(commentId)
    │               return hasUpvoted=false
```

## Testing Guidelines

### Manual API Testing

1. Start backend server
2. Authenticate as test user via OAuth
3. Create a published planner (or use existing)
4. `POST /api/planner/{id}/comments` with `{"content": "Test comment"}`
5. Verify 201 response with comment data, upvoteCount=0
6. `POST /api/planner/{id}/comments` with `{"content": "Reply", "parentCommentId": <id>}`
7. Verify reply has depth=1
8. `GET /api/planner/{id}/comments`
9. Verify flat list with both comments, correct parent-child relationship
10. `PUT /api/comments/{id}` with `{"content": "Edited"}`
11. Verify editedAt is set
12. `POST /api/comments/{id}/upvote`
13. Verify upvoteCount=1, hasUpvoted=true
14. `POST /api/comments/{id}/upvote` again (toggle off)
15. Verify upvoteCount=0, hasUpvoted=false
16. `POST /api/comments/{id}/upvote` again (toggle on)
17. Verify upvoteCount=1, hasUpvoted=true (reactivate)
18. `DELETE /api/comments/{id}`
19. Verify isDeleted=true, content=null in response

### Automated Functional Verification

- [ ] Create top-level comment: Returns 201 with depth=0, upvoteCount=0
- [ ] Create reply: Returns 201 with correct depth
- [ ] Max depth enforcement: Depth capped at 5, replies flatten
- [ ] Edit own comment: editedAt timestamp set
- [ ] Edit other's comment: Returns 403
- [ ] Delete own comment: Soft-delete, content nulled
- [ ] Delete other's comment: Returns 403
- [ ] Moderator delete: Returns 204
- [ ] Upvote creates new vote: upvoteCount increments
- [ ] Upvote toggle removes: upvoteCount decrements, vote soft-deleted
- [ ] Upvote toggle re-adds: upvoteCount increments, vote reactivated
- [ ] Rate limit: Returns 429 after limit exceeded
- [ ] Unpublished planner: Returns 403 for non-owner

### Edge Cases

- [ ] Reply to deleted top-level: Returns 403 with clear message
- [ ] Reply to child of deleted: Allowed, returns 201
- [ ] Empty content: Returns 400 validation error
- [ ] Content > 10000 chars: Returns 400 validation error
- [ ] Non-existent planner: Returns 404
- [ ] Non-existent parent comment: Returns 404
- [ ] Deleted user comments: Shows sentinel author (id=0)
- [ ] Comment on unpublished planner by non-owner: Returns 403
- [ ] Concurrent upvotes: Atomic counter prevents race condition
- [ ] Upvote count never negative: WHERE clause in decrement query

### Integration Points

- [ ] User hard-delete: Comments reassigned to sentinel user
- [ ] User hard-delete: Comment votes reassigned to sentinel user
- [ ] Planner delete: Comments cascade deleted (ON DELETE CASCADE)
- [ ] Sentinel user: Cannot authenticate (blocked by JwtAuthenticationFilter)

## Implementation Order

1. Schema: V015__add_planner_comments.sql
2. Enum: CommentVoteType.java
3. Entities: PlannerComment, PlannerCommentVote, PlannerCommentVoteId
4. Repositories: PlannerCommentRepository, PlannerCommentVoteRepository
5. DTOs: CreateCommentRequest, UpdateCommentRequest, CommentResponse, CommentVoteResponse
6. Exceptions: CommentNotFoundException, CommentForbiddenException
7. Service: CommentService (CRUD + voting with atomic counters)
8. Controller: CommentController
9. Integration: UserAccountLifecycleService, ModerationService, ModerationController
10. Config: RateLimitConfig, GlobalExceptionHandler
11. Tests: Unit + Integration
