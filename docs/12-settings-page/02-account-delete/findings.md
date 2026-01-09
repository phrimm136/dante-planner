# Account Deletion Feature - Learning Reflection

## What Was Easy

- Pattern reuse was structured: Research.md clearly mapped existing patterns (UsernameSection Suspense, ConflictResolutionDialog dismiss prevention, useUpdateKeywordMutation) with specific line references
- Test harness well-defined: Plan.md included explicit test requirements as Phase 6 steps, reducing guesswork
- Typed API client already generic: delete<T>() already fit the fetch<T>() pattern, minimal change needed
- Schema validation pattern established: Zod safeParse pattern from useUpdateKeywordMutation translated directly

## What Was Challenging

- Auth cache invalidation race condition: Mutation hook and component both called setQueryData(), causing timing issues; required moving invalidation to component's onSuccess only
- Test file extension mismatch: useDeleteAccountMutation.test.ts contained JSX causing parse errors; required .tsx rename mid-implementation
- Date parsing fragility: Malformed ISO timestamps could fail silently in toLocaleDateString(); added defensive try-catch with fallback
- Concurrent mutation-component callback execution: TanStack Query calls mutation's onSuccess before component's onSuccess, creating non-obvious dependency order

## Key Learnings

- Spec precision saves iteration: Research.md's field name audit (permanentDeleteAt vs permanentDeleteScheduledAt) prevented round-trip bugs
- Pattern copying requires line-by-line reading: Simply knowing "follow ConflictResolutionDialog" insufficient; dismissal prevention logic required exact understanding
- Mutation lifecycle is non-obvious: TanStack Query onSuccess execution order (hook then component) creates subtle dependencies
- OAuth login duplication is pattern-blocking: Extracting to shared hook violated pattern enforcement; accepted as technical debt with documentation
- Defensive date handling pays off: ISO timestamp parsing fragile at boundaries; explicit error handling prevents silent failures
- Test-driven design caught real bugs: Tests forced clarity on mutation callback order and race conditions that manual review missed

## Spec-Driven Process Feedback

- Research.md mapping was 100% accurate: Every spec element mapped correctly to implementation
- Plan.md execution order worked well: Dependency graph prevented circular dependencies, enabled clear rollback points
- Mutation hook pattern needed explicit detail: Plan could document that mutation.onSuccess fires before component.onSuccess
- Edge cases in instructions.md were comprehensive: Password re-entry, grace period, soft-delete reactivation all handled correctly

## Pattern Recommendations

- Document TanStack Query callback ordering in mutation pattern docs: mutationFn.onSuccess fires before component-level callbacks
- Create OAuth login extraction pattern or accept duplication: Current pattern enforcement blocks shared hook; need explicit reuse pattern
- Add defensive date parsing to date-handling patterns: toLocaleDateString() fragile with malformed ISO; recommend try-catch + fallback

## Next Time

- Verify callback execution order in plan phase: When mutations have onSuccess at both levels, document execution sequence
- Add file extension validation checklist: Test files with JSX must be .tsx
- Create edge case audit for date/time handling: Any ISO timestamp parsing needs explicit null/malformed tests
- Document pattern duplication trade-offs upfront: OAuth appears in both sections—document as accepted duplication vs extraction cost
