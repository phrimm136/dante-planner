# Review: Planner List Page

## Spec-Driven Compliance
- Spec-to-Code Mapping: FOLLOWED - All entities, DTOs, hooks match research.md spec
- Spec-to-Pattern Mapping: FOLLOWED - PlannerCard/IdentityCard, hooks/useIdentityListData patterns
- Technical Constraints: RESPECTED - Pagination, Zod validation, useSuspenseQuery
- Execution Order: FOLLOWED - Bottom-up backend -> data layer -> UI approach
- Deviation: My Plans TODO (line 77) - documented scope limitation, Community view primary

## What Went Well
- N+1 query fix with batch vote/bookmark fetching reduces 1+2N to 3 queries
- Clean separation: 7 components, 6 hooks, proper layering
- Query key factory pattern enables proper cache invalidation
- Tests comprehensive: 65 total (25 BE + 40 FE) covering critical paths

## Code Quality Issues
- [HIGH] Missing Sentry error tracking in 5 mutation hooks (violations error-tracking skill)
- [MEDIUM] Hardcoded /api/planner/md URLs in 6 hooks (should extract to constants)
- [MEDIUM] VoteResponseSchema field names mismatch with backend DTO fields
- [MEDIUM] Double Suspense boundaries in PlannerListPage could simplify
- [LOW] Missing aria-label on clickable PlannerCard for accessibility
- [LOW] Dead code: getUserVote method in PlannerService no longer used

## Technical Debt Introduced
- My Plans TODO comment at line 77 - feature scoped out but comment remains
- Search implementation: 4 repository methods risk query explosion with more filters
- No optimistic updates - mutations invalidate cache causing full refetch

## Backlog Items
- Add Sentry.captureException to mutation hook onError callbacks
- Extract API endpoints to constants.ts API_ENDPOINTS object
- Implement optimistic updates for vote/bookmark UX improvement
- Add aria-labels and keyboard navigation for a11y compliance
- Remove unused getUserVote private method from PlannerService
