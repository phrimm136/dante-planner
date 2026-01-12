# Backend Testing Infrastructure: Learning Findings

**Reflected:** 2026-01-12

## What Was Easy

- Pattern extraction from PlannerControllerTest for MockMvc, nested classes, authorization
- TestDataFactory adoption eliminated ~500 lines boilerplate
- Constraint violation testing with entityManager.flush()
- TestContainers worked smoothly after dependency upgrade
- Concurrent flakiness resolved via deterministic timestamps (entity setters)

## What Was Challenging

- H2 vs MySQL FK enforcement differences (6 tests disabled)
- AFTER_COMMIT event incompatibility with @Transactional rollback (9 tests disabled)
- TestContainers Docker API mismatch (1.19.3 → 1.21.3 upgrade required)
- UUID/CHAR column mapping differences between H2 and MySQL
- MockMvc can't verify SameSite cookie attribute directly

## Key Learnings

- TestDataFactory pattern enables scale; incremental adoption reduces risk
- Database dialect abstraction breaks silently; dialect-specific tests needed
- @Transactional rollback prevents AFTER_COMMIT listeners from firing
- Entity setters for timestamps produce flake-free tests vs Thread.sleep
- TestContainers version compatibility requires explicit docker-java.properties
- DataIntegrityViolationException messages differ by database; can't validate portably
- Rate limit buckets persist across tests; requires test-scoped bucket names

## Spec-Driven Process Feedback

- Research mapping 95% accurate; missed Docker API version issue
- TestDataFactory-first strategy unblocked all controller tests
- Architecture map lacked AFTER_COMMIT incompatibility documentation
- Success criteria exceeded: 24.3s (8x faster than 180s target)

## Pattern Recommendations

- Add "Entity Setters for Test Determinism" to be-testing skill
- Establish @Tag("containerized") as organizational standard
- Document database dialect test bifurcation (H2 vs MySQL)
- Make TestDataFactory an org-wide standard with IDE templates
- Document rate limit test anti-pattern (bucket state persistence)

## Next Time

- Verify Docker/TestContainers compatibility before architecture decisions
- Document AFTER_COMMIT event testing gap in learning docs
- Create test infrastructure smoke test in CI
- Establish rate-limit testing strategy before implementation
- Create UUID migration checklist for @JdbcTypeCode annotations
