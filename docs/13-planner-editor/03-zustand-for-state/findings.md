# Findings: Zustand State Management for Planner Editor

## What Was Easy

- Zustand patterns reused cleanly from existing useSseStore and useFirstLoginStore
- Instance-scoped context pattern isolated state between tabs immediately
- DevTools middleware integration was drop-in for debugging
- Hot/Warm/Cold slice categorization made subscription boundaries obvious
- Dependency analysis upfront identified all 15+ child components correctly

## What Was Challenging

- Cascading re-renders from callbacks: store pattern assumes memoized children, but callback props broke memo comparisons
- Set comparison in memo: JavaScript Set equality is by reference, needed custom comparators
- getState callback stability: inline function created infinite re-render loop (1000+ renders)
- usePlannerSave composition: hook needed entire state but parent couldn't pass callbacks safely
- Viewer context edge case: components reused outside store provider threw on context null

## Key Learnings

- Store initialization timing matters: lazy-init on mount prevents race conditions with route parsing
- Granular subscriptions don't eliminate memo: child memos still need custom comparisons for callbacks
- Sets need explicit handling: array deserialization must convert to Set at init time, type casting alone fails
- Callback props are anti-pattern in store architecture: children should call store.updateX() directly
- Dual-mode components need override pattern: props like floorSelectionsOverride allow reuse outside context
- Performance target achievable but requires precision: <15ms only after fixing 8 separate cascade sources

## Spec-Driven Process Feedback

- Research mapping 95% accurate: missed only Set comparison in memo edge case
- Plan order (store → parent → children → integration) worked sequentially with minimal backtracking
- Checkpoint verification caught all 8 re-render cascades during manual testing
- Spec correctly kept dialog states local (useState) vs store

## Pattern Recommendations

- Document custom memo comparisons for Sets in fe-data skill
- Add "callback-free child architecture" pattern showing direct store calls vs callback props
- Include useCallback wrapper pattern for hook getters to prevent infinite loops
- Establish Set handling convention for deserialization at init time
- Record "cascading re-render debugging" checklist for memo failures

## Next Time

- Add memo verification to checklist before marking done (React Profiler each memoized child)
- Separate store creation from parent refactoring to isolate issues
- Test Set operations explicitly with unit tests for serialization/deserialization
- Establish callback pattern rules upfront before implementation starts
- Plan fallback hook pattern for reused components from start (not as post-implementation fix)
