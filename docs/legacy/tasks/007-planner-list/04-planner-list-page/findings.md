# Findings: Planner List Page

## What Was Easy
- Bottom-up architecture (backend -> data layer -> UI) prevented circular dependencies
- TanStack Query useSuspenseQuery pattern enabled rapid hook implementation
- Spec-driven execution with 45 ordered steps meant zero ambiguity
- Test coverage (65 total) caught N+1 bugs and schema mismatches early
- Batch query pattern was reusable for both votes and bookmarks

## What Was Challenging
- Migration index naming: V006 violated convention, required V007 hotfix
- Schema field mismatches: VoteResponseSchema vs backend DTO caught during tests
- Date/timezone edge cases: threshold-based formatting (HH:mm vs MM/DD) required careful testing
- User context batching: N+1 issue discovered during code review, required refactor
- Suspense boundary placement: double wrapping suggested unclear responsibilities

## Key Learnings
- Batch queries mandatory at scale: 1+2N to 3 queries prevents performance regression
- Version-prefixed index names prevent migration naming conflicts
- Query key factories non-negotiable for proper cache invalidation
- Zod schemas catch backend/frontend divergence before runtime
- Relative vs absolute time formatting requires timezone awareness
- Spec accuracy improves with "Depends On" / "Enables" notation in plan

## Spec-Driven Process Feedback
- Plan accuracy: 95% - 45 steps executed as written, only metadata issues surprised
- Phase ordering worked: backend -> data layer -> UI -> tests prevented blocking
- Research mapping accurate: implementation matched specs, no architectural pivots
- Scope clarity critical: documented My Plans TODO prevented scope creep
- Review cycle value: caught real N+1 bug plus false positives

## Pattern Recommendations
- Add batch query pattern to be-service skill: findByFieldIn for list endpoints
- Establish migration naming convention: version prefix for indices in template
- Document Suspense boundary placement for list pages in fe-component
- Add query key factory example to fe-data skill with cache invalidation
- Document timezone-aware date formatting thresholds

## Next Time
- Read existing migrations before writing new ones (V006/V007 issue)
- Validate schema alignment early (cross-check DTOs and Zod before hooks)
- Estimate review cycle upfront for performance-critical features
- Assume batch queries required for any list endpoint with user context
- Create snapshot tests for date formatting edge cases
