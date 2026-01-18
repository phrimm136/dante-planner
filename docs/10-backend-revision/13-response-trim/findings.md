# Learning Reflection: API Response Data Leakage Fix

## What Was Easy

- Spec clarity: Instructions provided exact file paths and changes needed
- Pattern consistency: CommentTreeNode established clear UUID/publicId pattern to follow
- Dead code detection: CommentResponse.java discovery simplified scope (delete vs refactor)
- Frontend/backend coordination: Zod schemas already expected UUID structures

## What Was Challenging

- Scope divergence: Research over-proposed work (migration, refactor); actual work was simpler
- Test mock setup: Adding publicId to mock Users across 15 test files required synchronization
- Pre-existing test failures: 10 unrelated failures created noise during verification
- VoteResponse field naming: Discovered upvotes/userVote vs upvoteCount/hasUpvoted mismatch
- MAX_DEPTH flattening: Tests assumed MAX_DEPTH=5 but code uses MAX_VALUE

## Key Learnings

- Spec-driven planning catches scope creep by verifying assumptions against codebase first
- Breaking changes require coordinated testing across BE/FE layers
- Dead code hides in DTO layers; grep for usage before refactoring
- UUID migration patterns differ by entity (User.publicId, Comment.publicId, Planner.id)
- Mock consistency: UUID fields must be randomUUID but consistent per test
- Frontend Zod validation immediately catches API contract changes
- Test-first scope validation reveals field naming inconsistencies early

## Spec-Driven Process Feedback

- Research mapping 90% accurate but over-proposed complexity
- Plan order worked well: Backend DTOs → Dead code → Frontend → Tests
- Spec clarity high: No ambiguities during execution
- Discovery gap: Plan missed pre-existing test failures and field naming issues
- Coordination requirement was implicit, not explicit in spec

## Pattern Recommendations

- Document UUID exposure strategy per domain in skill docs
- Add "usage verification" step to dead code detection
- Add mock builder patterns with UUID fields to be-testing skill
- Create "breaking change" checklist for DTO modifications

## Next Time

- Run test discovery before planning (grep @Test + DTO names)
- Separate refactor vs delete decisions earlier in research
- Batch mock updates as single refactoring pass
- Validate research assumptions with grep before planning
- Document baseline test status before starting implementation
