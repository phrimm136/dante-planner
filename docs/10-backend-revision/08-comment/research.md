# Research: Planner Comment System (Backend)

> **Status:** COMPLETE - No ambiguities
> **Generated:** 2026-01-08

---

## Spec Ambiguities

**NONE** - All critical decisions documented in instructions.md

---

## Spec-to-Code Mapping

| Requirement | Create | Modify |
|-------------|--------|--------|
| Comment CRUD | PlannerComment.java, CommentService.java, CommentController.java, DTOs | GlobalExceptionHandler.java |
| Threaded replies | parentCommentId + depth fields in entity | Repository query logic |
| Soft-delete | deletedAt field in entities | Service delete methods |
| Upvote-only voting | CommentVoteType.java, PlannerCommentVote.java, PlannerCommentVoteId.java | Atomic counter queries |
| Toggle upvote | Persistable pattern in vote entity | Service toggle logic |
| Deleted user handling | — | UserAccountLifecycleService.performHardDelete |
| Rate limiting | — | RateLimitConfig.java, application.properties |
| Database schema | V015__add_planner_comments.sql | — |

---

## Pattern Enforcement (MANDATORY)

| New File | MUST Read First | Patterns to Copy |
|----------|-----------------|------------------|
| PlannerComment.java | PlannerVote.java | Entity lifecycle, soft-delete helpers, JPA annotations |
| PlannerCommentVote.java | PlannerVote.java | Composite key (Persistable, @IdClass, @Transient isNew), soft-delete |
| PlannerCommentVoteId.java | PlannerVoteId.java | Record structure, equals/hashCode, Serializable |
| CommentVoteType.java | VoteType.java | @JsonValue/@JsonCreator, fromValue factory |
| PlannerCommentRepository.java | PlannerRepository.java | @Query, @Modifying, atomic counters with WHERE clause |
| PlannerCommentVoteRepository.java | PlannerVoteRepository.java | Composite key queries, reassign pattern |
| CommentService.java | ModerationService.java | Role checking, ownership validation, @Transactional |
| CommentController.java | ModerationController.java | @RestController, @AuthenticationPrincipal, ResponseEntity |
| CommentResponse.java | PublicPlannerResponse.java | Nested records, null handling |
| V015__*.sql | V009__add_user_soft_delete.sql | Flyway naming, FK conventions, index patterns |

---

## Existing Utilities (Reuse)

| Category | Location | Reuse |
|----------|----------|-------|
| Rate Limiting | RateLimitConfig.java | Add checkCommentLimit following same pattern |
| User Lifecycle | UserAccountLifecycleService.java | Extend performHardDelete |
| Exceptions | exception/*.java | Create Comment* exceptions same pattern |
| Soft Delete | All entities | Apply deletedAt + isDeleted/softDelete |
| Atomic Counters | PlannerRepository.java | Copy increment/decrement pattern |
| Sentinel User | SENTINEL_USER_ID = 0L | Extend reassignment to comments |

---

## Gap Analysis

**Create (16 files):**
- entity/PlannerComment.java
- entity/PlannerCommentVote.java
- entity/PlannerCommentVoteId.java
- entity/CommentVoteType.java
- repository/PlannerCommentRepository.java
- repository/PlannerCommentVoteRepository.java
- service/CommentService.java
- controller/CommentController.java
- dto/comment/CreateCommentRequest.java
- dto/comment/UpdateCommentRequest.java
- dto/comment/CommentResponse.java
- dto/comment/CommentVoteResponse.java
- exception/CommentNotFoundException.java
- exception/CommentForbiddenException.java
- migration/V015__add_planner_comments.sql

**Modify (6 files):**
- UserAccountLifecycleService.java (add reassignment calls)
- ModerationService.java (add deleteComment)
- ModerationController.java (add moderation endpoint)
- RateLimitConfig.java (add comment bucket)
- GlobalExceptionHandler.java (add 2 handlers)
- application.properties (rate limit config)

---

## Testing Requirements

### Unit Tests
- CommentService: create, edit, delete, depth enforcement, parent validation
- CommentService: upvote toggle (new/delete/reactivate), atomicity
- PlannerCommentRepository: atomic counters, reassignment
- CommentVoteType: serialization

### Integration Tests
- Create comment on published planner (201)
- Create reply with correct depth (201)
- Reply to deleted top-level (403)
- Reply to child of deleted (201)
- Edit own/other's comment (200/403)
- Delete own/moderator delete (204)
- Upvote toggle cycle
- Rate limit enforcement (429)
- Unpublished planner access (403)
- User hard-delete reassignment
- Planner delete cascade

---

## Technical Constraints

- **Database:** Composite PK for votes, FK CASCADE on planner, SET NULL on parent
- **Entities:** Persistable pattern for composite keys (critical for insert/merge)
- **Atomic ops:** @Modifying @Query with WHERE clause (prevents negative/race)
- **Validation:** @Size(max=10000) on content, depth max 5 (service enforced)
- **Spring:** Constructor injection only, @Transactional on public methods only

---

## Implementation Order

1. CommentVoteType.java (enum)
2. PlannerCommentVoteId.java (composite key)
3. PlannerComment.java (entity)
4. PlannerCommentVote.java (entity)
5. V015__add_planner_comments.sql (migration)
6. Repositories (Comment, CommentVote)
7. DTOs (Create, Update, Response, VoteResponse)
8. Exceptions (NotFound, Forbidden)
9. CommentService.java (business logic)
10. CommentController.java (REST endpoints)
11. Modify existing files (lifecycle, moderation, rate limit, exception handler)
12. Tests (unit + integration)
