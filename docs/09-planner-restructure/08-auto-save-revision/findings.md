# Learning Reflection: Unified Planner Save Hook

## What Was Easy

- Spec-to-code mapping: Research provided 9 clear requirements with target files
- Pattern reuse: Existing utilities (usePlannerAutosave, LinkDialog) made unification straightforward
- Error handling structure: ConflictError class fit cleanly into existing api.ts patterns
- Dependency order execution: Phase-based approach eliminated integration surprises
- TypeScript strict mode: Typed ConflictError caught version desync bugs early

## What Was Challenging

- Race condition between auto-save and SSE: Conflict dialog may appear after SSE updates
- Version tracking state mutation: Required try-catch rollback pattern for failed overwrites
- Dialog dismissal handling: Multiple escape paths needed defensive handlers
- Toast return path complexity: Needed save() → boolean return pattern
- Serialization performance: JSON.stringify on every dirty check (M1 deferred)

## Key Learnings

- Optimistic locking requires defensive coding: Increment version only after API promise resolves
- Modal dialogs need escape hatch thinking: Browser close, network disconnect leave state orphaned
- Unified hook API benefits from clear ownership: Mixed return types caused toast logic bugs
- Research phase uncovered 4 critical bugs that code review alone would miss
- Pattern enforcement reduces boilerplate: Copying dirty-checking patterns saved ~100 lines
- i18n scope creeping: 5+ new translation keys not surfaced during planning

## Spec-Driven Process Feedback

- Research.md accuracy: 95% - Only missed SSE race documentation need
- Plan.md ordering: Excellent - Phase execution eliminated rework
- Instructions.md clarity: Clear for happy path, ambiguous on edge cases
- Testing guidelines helped: Manual UI testing caught H1 toast issue before unit tests

## Pattern Recommendations

- Add to fe-data skill: "Conflict Resolution with Optimistic Locking" pattern
- Add to fe-component skill: "Multi-Escape Dialog Pattern" for modals with external triggers
- Add to hooks skill: "Dual-Purpose Hook API" - unifying passive and active operations
- Anti-pattern: "String-based error detection via message.includes()" - use typed error classes
- Process pattern: "Spec-driven research reveals systemic bugs"

## Next Time

- Add SSE race condition test during conflict dialog
- Checkpoint after Step 2 API changes to verify existing consumers
- i18n scope discovery: Ask "What user-facing strings does this feature add?" during planning
- Defensive coding checklist for modal state: Unify all escape paths under single handler
- Document limitations explicitly in plan.md Risk Assessment section
