# Code Quality Review: Planner List Initialization

**Reviewer**: code-architecture-reviewer agent
**Date**: 2025-12-30
**Verdict**: NEEDS WORK (2 HIGH priority issues)

## Spec-Driven Compliance

- ✅ Spec-to-Code Mapping: All required components implemented (API migration, Planner columns, PlannerVote entity, voting DTOs, endpoints)
- ✅ Spec-to-Pattern Mapping: @IdClass composite key, JPA AttributeConverter for SET column, @AuthenticationPrincipal auth pattern
- ⚠️ Technical Constraints: MySQL SET converter, Flyway V002, permitAll configured, but API path mismatch exists
- ✅ Execution Order: Bottom-up from schema → entities → repos → DTOs → service → controller → tests
- ❌ DEVIATION: Plan specifies `/planner/md/*` but SecurityConfig shows `/api/planner/md/*`. Frontend expects `/api/planners/*`

## What Went Well

- Strong transaction boundaries - @Transactional at service layer only
- N+1 prevention - @EntityGraph used consistently on repository queries
- Comprehensive test coverage - 204 tests passing covering vote scenarios
- Denormalized counts update immediately for query performance
- Security properly configured - public vs authenticated endpoints

## Code Quality Issues

- **[HIGH]** Race condition in vote counting (PlannerService.castVote) - Read-modify-write not atomic, concurrent votes can lose data. Need @Version or atomic increment
- **[HIGH]** Email extraction security leak (PublicPlannerResponse) - Exposes username from email. Tracked in TODO but needs immediate fix
- **[MEDIUM]** Missing @Valid on VoteRequest - Invalid enum values reach service layer
- **[MEDIUM]** Inconsistent null handling in castVote - Implicit dependency on publish check is fragile
- **[LOW]** Missing @Builder on PlannerVote - Inconsistent with Planner entity pattern

## Technical Debt Introduced

- Author name extraction stub uses email prefix (security risk) - pending displayName spec
- Vote count race condition deferred to "high-scale optimization" - real corruption risk
- API path confusion between backend and frontend - breaking change pending

## Backlog Items

- Implement @Version field on Planner for optimistic locking on vote counts
- Add Bean Validation to VoteRequest
- Add User.displayName column + update PublicPlannerResponse
- Create frontend task to update API base URL
- Consider composite index on (published, category, vote_score) for performance
