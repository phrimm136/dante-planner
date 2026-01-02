# Findings: Planner View Count

## What Was Easy

- Composite key pattern was direct copy from PlannerVote/PlannerVoteId - just swapped field types
- Existing `PlannerRepository.incrementViewCount()` already worked atomically
- Security Config required one line to add public endpoint
- IP extraction (X-Forwarded-For → getRemoteAddr) is standard HTTP practice
- Test structure inherited from PlannerVoteRepository tests

## What Was Challenging

- Race condition between check-exists and insert required DataIntegrityViolationException handling
- Persistable<T> interface required trial-and-error - sparse documentation on isNew() override
- LocalDate timezone semantics: verifying dedup triggers at midnight UTC, not local
- User-Agent truncation: spec unclear on before vs after hashing (chose before-hash)
- Unpublished planner: unclear if 404 should log or silent (went silent per "reduce noise")

## Key Learnings

- Spec-driven order matters: Phase 1 (schema) before Phase 2 (utility) enables incremental verification
- Composite keys need explicit @IdClass on entity - field names must match exactly
- Persistable<T> returns false from isNew() after insert to prevent accidental UPDATEs
- Hash determinism requires immutable ordering with delimiters (e.g., `:`) documented in comments
- @Transactional rollback isolates integration tests - without it, duplicate tests interfere
- LocalDate UTC boundaries require explicit timezone-aware test cases

## Spec-Driven Process Feedback

- research.md mapping was 95% accurate - only surprise was race condition handling
- plan.md execution order was validated by architecture - no circular dependencies
- Spec lacked explicit race-condition semantics: silent vs logged exception
- Technical constraints section was complete - zero surprises
- Edge cases E1-E5 appeared exactly as specified

## Pattern Recommendations

- Document @IdClass + Persistable<T> pattern in be-service skill with PlannerView as example
- Add race-condition upsert pattern: check → insert → catch DataIntegrityViolationException
- Create hash utility template for privacy-sensitive data (SHA-256, ordering, truncation)
- Document timezone handling for LocalDate dedup in skill resources
- Add test isolation pattern explaining @Transactional behavior

## Next Time

- Clarify race-condition semantics upfront during research phase
- Include timezone decisions in spec, not implementation
- Create skeleton @IdClass before tests to reduce uncertainty
- Separate "best-effort" constraints from edge-case handling decisions
- Add timezone boundary test to manual verification checklist

---

Reflected: 2026-01-02
