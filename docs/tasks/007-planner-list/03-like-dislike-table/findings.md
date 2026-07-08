# Findings: Planner Vote Soft Delete

## What Was Easy
- Soft delete pattern reuse from Planner.java (isDeleted(), softDelete())
- Migration structure clarity from V002 template
- State machine decomposition into 4 focused helper methods
- Atomic counter operations via existing repository methods
- Test structure familiarity with existing codebase patterns

## What Was Challenging
- Persistable interface lifecycle for composite key persistence
- Index optimization required V004 revision after initial V003
- Race condition surfaced during review, forced V005 for @Version
- Error message distinction only in logs, not exceptions
- Manual timestamp management (no @PreUpdate for soft delete)

## Key Learnings
- Three migrations for one feature indicates spec-phase gaps
- Composite keys + reactivation require explicit UPDATE patterns
- Persistable interface adds lifecycle callback dependencies
- Index optimization must follow actual query predicates
- Optimistic locking complements soft delete state transitions
- Test coverage for idempotency exposes edge cases early

## Spec-Driven Process Feedback
- Research mapping: 6/6 requirements correctly identified
- Plan execution order: Phase 1-4 dependency chain held
- Spec lacked: Race condition scenario (led to V005)
- Gap analysis incomplete: Index, version, error messaging discovered in review
- Test scenarios grew from 3 to 6 due to edge cases

## Pattern Recommendations
- Document Persistable interface trade-offs for composite keys
- Soft delete index pattern: column ordering for (entity_id, deleted_at)
- Idempotent operation checklist for soft delete operations
- Race condition guard pattern: when to apply @Version
- Error messaging stratification: exceptions vs logs vs both

## Next Time
- Review query patterns before writing migrations
- Persist spec revisions to research.md after discoveries
- Explicit race condition analysis during planning
- Write test names during plan phase to expose edge cases
- Set rule: single migration per feature phase
