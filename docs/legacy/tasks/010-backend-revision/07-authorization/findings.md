# Authorization System - Learning Reflection

## What Was Easy

- Enum pattern from MDCategory: Direct copy with @JsonValue/@JsonCreator worked immediately
- Token layering: Role in access token, excluded from refresh, cleanly separated auth paths
- Service injection: Constructor injection and @Transactional transferred seamlessly
- Role hierarchy bean: Spring Security's RoleHierarchy made hierarchical rules trivial
- Migration strategy: DEFAULT 'NORMAL' was safer than backfill scripts

## What Was Challenging

- TOCTOU race condition: Missed concurrent change window; pessimistic locking was non-obvious
- Ordinal() fragility: Using enum ordinal for rank comparison created debt; explicit rank retrofit
- Token blacklist unbounded growth: No eviction policy; required post-implementation cleanup
- Refresh token role staleness: Initial design embedded role in refresh, creating stale windows
- Timeout index underutilization: V014 partial index wasn't tested against query patterns

## Key Learnings

- Explicit over implicit: Enum rank fields (1,2,3) instead of ordinal() caught bugs
- Pessimistic locking trade-off: Blocks writes but prevents races; acceptable for low-frequency ops
- Stateless auth with stateful exceptions: JWT is fast, but demotion needs server-side blacklist
- Scheduled cleanup is essential: In-memory state requires TTL-based eviction
- Index queries need end-to-end testing: Partial indexes only useful if queries exploit them
- Spec-driven migrations prevent scope creep: 19 files identified upfront, no surprises
- Role hierarchy is application-level: Spring handles hierarchy, DB grants need explicit checking

## Spec-Driven Process Feedback

- Research mapping was comprehensive: All 19 files predicted correctly
- Execution sequence prevented coupling: Data → Token → Security → Service → Controller
- Safeguard requirements were explicit: 5 privilege escalation guards matched exactly
- Technical constraints section saved iteration: RoleHierarchy and token decisions pre-resolved
- Testing guidelines caught edge cases: "Tokens without role = NORMAL" documented early

## Pattern Recommendations

- Add pessimistic locking pattern to be-service: @Lock(PESSIMISTIC_WRITE) for race-critical ops
- Document enum rank fields pattern: Explicit rank over ordinal() for comparison enums
- Scheduled cleanup for in-memory state: TTL-based eviction guidance in be-async skill
- Refresh token role staleness pattern: Sensitive state not in refresh token (be-security)
- Index validation workflow: EXPLAIN PLAN checks in be-service pattern docs

## Next Time

- Verify index usage upfront with EXPLAIN PLAN and add query methods immediately
- Establish cleanup baseline (TTL, eviction) in initial design, not post-implementation
- Test demotion flows with active tokens in integration tests
- Document which operations force DB lookup vs. use cached JWT claim
- Consider multi-server implications earlier; document Redis migration decision tree
