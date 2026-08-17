# Learning Reflection: Backend Planner Security & Reliability

## What Was Easy

- **Exception pattern reuse** - UserNotFoundException and RateLimitExceededException copied from PlannerNotFoundException; zero design decisions needed.
- **Configuration-driven rate limiting** - @ConfigurationProperties cleanly separated concerns; Spring Boot injection automatic.
- **Validation structure** - PlannerContentValidator mapped cleanly to research.md spec.
- **Service layer foundation** - Existing validateContentSize/validateNoteSize made refactoring minimal.

## What Was Challenging

- **Strict validation edge cases** - "Reject unknown fields" didn't list exhaustive known field set; required reading validator implementation.
- **Double JSON parsing refactor** - Converting to accept JsonNode disrupted internal logic; careful tracing required.
- **SSE cleanup timing** - 60s interval chosen without research justification; unclear if it matches usage patterns.
- **Migration key deferral** - Step 11 skipped due to external dependency; created architectural mismatch.

## Key Learnings

- **Research.md clarity matters** - Spec-to-code mappings cut implementation time by eliminating ambiguity.
- **Pattern enforcement scales** - Copy-paste from existing exceptions yielded 100% type-safety with zero design.
- **Bucket4j single-instance tradeoff** - In-memory ConcurrentHashMap creates technical debt (no Redis path).
- **Magic constants sneak in** - Size limits hardcoded instead of application.properties; patterns must flag this.
- **Spec gaps cascade** - Migration key deferred because User entity changes required separate task.

## Spec-Driven Process Feedback

- **Research.md was accurate** - Spec-to-code mappings matched executed work; no surprises.
- **Plan.md order prevented rework** - Dependencies marked correctly; could not proceed out of order.
- **Spec ambiguity on strictness** - "Reject unknown fields" vs forward-compatible needed explicit decision.
- **Missing external dependency tracking** - Step 11 User entity dependency not surfaced in plan.md.

## Pattern Recommendations

- **Document rate limit storage strategy** - In-memory vs. Redis should be explicit; no abstraction for scaling.
- **Add configuration validation constraints** - @Min/@Max/@Positive annotations on config classes.
- **Standardize error code granularity** - Pattern for MISSING_REQUIRED_FIELD, INVALID_CATEGORY, UNKNOWN_FIELD.
- **Scheduled task logging levels** - Distinguish operational errors vs. debug/observability logs.

## Next Time

- **Identify external dependencies in research phase** - Mark blockers upfront instead of deferring.
- **Validate configuration completeness** - Grep application.properties for magic constants.
- **Document observability plan** - Commit to Micrometer metrics before closing task.
- **Clarify spec ambiguities before planning** - Strict vs. forward-compatible decision should be explicit in research.md.
