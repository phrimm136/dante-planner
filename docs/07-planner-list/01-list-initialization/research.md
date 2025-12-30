# Research: Planner List Backend Infrastructure

## Spec Ambiguities

**None found.** The spec is clear on schema changes, API endpoints, authorization rules, and edge cases.

---

## Spec-to-Code Mapping

| Requirement | Codebase Location | Modification Needed |
|-------------|-------------------|----------------------|
| `published` boolean column | `Planner.java` | Add field with @Column + @Builder.Default |
| `upvotes`/`downvotes` columns | `Planner.java` | Add 2 fields with @Column + @Builder.Default |
| `selectedKeywords` SET column | `Planner.java` | Add field with custom JPA converter |
| `PlannerVote` entity | New file | Create `entity/PlannerVote.java` |
| Vote repository queries | New file | Create `repository/PlannerVoteRepository.java` |
| GET /api/planners/published | `PlannerController.java` | Add paginated public endpoint |
| GET /api/planners/recommended | `PlannerController.java` | Add paginated endpoint (net votes >= 10) |
| PUT /api/planners/{id}/publish | `PlannerController.java` | Add owner-only toggle endpoint |
| POST /api/planners/{id}/vote | `PlannerController.java` | Add authenticated vote endpoint |
| Service layer voting logic | `PlannerService.java` | Add methods for publish + vote |
| Voting DTOs | New files | `PublicPlannerResponse`, `VoteRequest`, `VoteResponse` |
| Schema migration | New file | `V003__add_planner_publishing.sql` |

---

## Spec-to-Pattern Mapping

| Requirement | Existing Pattern | How to Apply |
|-------------|------------------|--------------|
| MySQL SET enum | JPA AttributeConverter | Create KeywordSetConverter |
| Authorization (owner only) | @AuthenticationPrincipal + service check | Follow findPlannerOrThrow pattern |
| Pagination | Page<T> + Pageable | Follow existing getPlanners pattern |
| Soft delete handling | deletedAt != null checks | Extend repository queries |
| DTO mapping | fromEntity() static builder | Follow PlannerSummaryResponse pattern |
| Response wrapping | ResponseEntity<T> | Use ResponseEntity.ok() patterns |
| SSE notifications | PlannerSseService.notifyPlannerUpdate() | Trigger on publish/vote changes |

---

## Pattern Enforcement (MANDATORY)

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `PlannerVote.java` | `Planner.java` | Entity annotations, relationships, audit fields |
| `PlannerVoteRepository.java` | `PlannerRepository.java` | Repository interface, @Query methods |
| `PublicPlannerResponse.java` | `PlannerSummaryResponse.java` | DTO structure, fromEntity() builder |
| `VoteRequest.java` | `UpdatePlannerRequest.java` | Request DTO, Bean Validation |
| `V003__*.sql` | Existing migrations in db/migration/ | Flyway naming, SET syntax, indexes |

---

## Existing Utilities (CHECK BEFORE CREATING)

| Category | Location | Existing |
|----------|----------|----------|
| Keywords | `frontend/src/lib/constants.ts` | PLANNER_KEYWORDS (17 values) |
| DTOs | `backend/dto/planner/` | PlannerSummaryResponse, PlannerResponse, CreatePlannerRequest |
| Exceptions | `backend/exception/` | PlannerNotFoundException, PlannerConflictException |
| Controller | `PlannerController.java` | Rate limiting, @AuthenticationPrincipal, @DeviceId |
| Repository | `PlannerRepository.java` | @EntityGraph, custom @Query methods |

---

## Gap Analysis

**Currently missing:**
- PlannerVote entity + repository
- Voting DTOs (VoteRequest, VoteResponse, PublicPlannerResponse)
- Publishing/voting endpoints in PlannerController
- Publishing/voting methods in PlannerService
- Schema migration for new columns
- JPA converter for MySQL SET type

**Needs modification:**
- Planner.java: Add 4 new fields (published, upvotes, downvotes, selectedKeywords)
- PlannerRepository.java: Add query methods for published/recommended
- PlannerService.java: Add public/voting business logic
- PlannerController.java: Add 4 new endpoints

**Can reuse:**
- GlobalExceptionHandler (exception handling)
- RateLimitConfig (rate limiting)
- DTO builder patterns (fromEntity methods)
- PlannerSseService (SSE notifications)
- Authorization pattern (@AuthenticationPrincipal)
- Pagination pattern (Spring Data Pageable)
- Soft delete pattern (deletedAt checks)

---

## Testing Requirements

### Unit Tests Required
- PlannerService voting: Cast, change, remove vote, count updates
- PlannerService publish toggle: Owner permissions
- Vote deduplication: No duplicate votes
- Keyword validation: SET column accepts/rejects values

### Integration Tests Required
- Vote endpoint flow: Create → Vote UP → Verify counts
- Publish endpoint flow: Create → Toggle → Verify in published list
- Vote change scenario: UP → DOWN → Verify counts
- Authorization: Guest vote 401, non-owner publish 403
- Pagination: Published endpoint respects page/size
- Soft delete: Deleted planners excluded from public lists
- CASCADE delete: Planner/user deletion removes votes

---

## Technical Constraints

1. **MySQL SET JPA Mapping**: Custom AttributeConverter<Set<String>, String>
2. **Flyway Versioning**: Check existing migrations, use next version number
3. **Spring Security Authorization**:
   - Public endpoints: No @AuthenticationPrincipal required
   - Owner-only: Compare planner.getUser().getId() == userId
   - Vote auth: Check userId != null
4. **Composite Key for PlannerVote**: Use @IdClass or @EmbeddedId with unique constraint
5. **Denormalized Counts**: Update Planner.upvotes/downvotes on every vote
6. **Pagination Defaults**: Spring Data defaults (page 0, size 20)
