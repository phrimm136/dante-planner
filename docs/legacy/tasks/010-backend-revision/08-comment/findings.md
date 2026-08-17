# Learning Reflection: Planner Comment System

## What Was Easy

- Composite key pattern reuse - PlannerVote provided exact blueprint for PlannerCommentVote
- Atomic counter queries - @Modifying with WHERE clause copied directly from PlannerRepository
- Soft-delete conventions - deletedAt, isDeleted(), softDelete() patterns consistent across codebase
- Role-based authorization - Existing NORMAL < MODERATOR < ADMIN hierarchy mapped directly
- Exception handler integration - Followed exact naming conventions mechanically

## What Was Challenging

- Vote soft-delete vs reassignment - Composite key conflicts forced soft-delete instead of reassign
- Optimistic locking gap - Spec didn't clarify @Version; added V016 migration retroactively
- Depth enforcement semantics - "Flatten to siblings" required service-layer logic clarification
- Null author serialization - Record structure didn't naturally express nullable nested fields

## Key Learnings

- Composite keys require Persistable boilerplate - @Transient isNew flag is non-negotiable
- Atomic operations need WHERE guards - Decrement must have `upvoteCount > 0` clause
- Soft-delete cascades complicate reassignment - Asymmetry between soft-deleted and active votes
- Rate limiting bucket config needs lockstep updates - RateLimitConfig AND application.properties
- Sentinel user pattern spreads across layers - Auth filter, DTOs, batch loads all need updates

## Spec-Driven Process Feedback

- research.md accuracy: 95% - Only missed V016 for @Version
- Plan execution order worked - No backward dependencies in phase sequence
- Ambiguity surfaced during implementation - Vote soft-delete vs reassign needed clarification

## Pattern Recommendations

- Document Persistable.isNew() contract in be-service skill - Silent save failures occur without it
- Add WHERE guard requirement to atomic counter skill docs - Prevent negative values
- Create soft-delete reassignment decision matrix - Scope varies per entity type
- Document rate limit bucket dual-registration anti-pattern - Easy to miss one location

## Next Time

- Add @Version to all soft-delete entities at creation - Prevents retroactive migrations
- Make sentinel user filtering configurable in batch loads - Pass exclude set, don't hardcode
- Create concurrent vote toggle integration test immediately - High-risk for atomicity bugs
- Comment vote reassignment decisions in SQL migrations - Prevent future maintainer confusion
