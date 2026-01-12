# MD Planner Editor Consolidation - Learning Findings

## What Was Easy

- Component extraction pattern: Clear separation between mode-specific wrappers and shared UI component
- Mode prop system: Clean boolean gate for conditional logic (draft recovery, state initialization)
- Dependency array discipline: Using syncVersion instead of planner object ensured proper re-initialization
- State hook preservation: All 15+ hooks worked identically after extraction
- Test coverage methodology: Unit + integration tests caught timing issues before manual testing

## What Was Challenging

- Infinite dialog loop: usePlannerStorage returning new instance each render broke singleton pattern
- Temporal Dead Zone in scroll restoration: Variable references before declaration not caught by TypeScript
- State initialization timing: Async planner loading meant useState with empty values, useEffect reconciles
- Progressive rendering reset: Category changes need smart partial reset, not hard reset
- Translation key scoping: Error pages needed new keys, required understanding i18n fallback behavior

## Key Learnings

- Singleton pattern for one-time init: Guard state (hasCheckedForDraft) cleaner than useRef + early return
- Dependency array stability: Use derived values (planner?.metadata.syncVersion) not object reference
- Variable declaration order: Must declare before useEffect dependencies reference them (TDZ)
- Conditional Suspense boundaries: Edit mode needs extra loading layer for planner data
- Draft recovery is mode-specific: Conditional on initialization, not exposed at component level
- Mode prop should be immutable: Changing mode during component lifetime breaks state coherence
- Set deserialization timing: Convert JSON arrays to Sets during state init, not during render

## Spec-Driven Process Feedback

- Research.md mapping was highly accurate: Phase breakdown matched reality
- Pattern enforcement hook blocked deferred improvements correctly: KeywordSelector documented for future
- Instructions.md risk assessment was realistic: All edge cases surfaced during implementation
- Testing guidelines cascaded correctly: Unit/integration/edge case separation prevented over-testing

## Pattern Recommendations

- Document singleton pattern for one-time effects in fe-component skill
- Emphasize object reference stability: Use derived values in dependency arrays
- Add TDZ awareness to JSX rules: Variables must be declared before useEffect dependencies
- Draft recovery is mode-specific: Conditional initialization logic (new vs edit modes)
- Nested Suspense for data dependencies: One Suspense per logical data dependency

## Next Time

- Verify variable declarations before using in dependency arrays (TDZ check)
- Default to derived state for stability-critical dependencies
- Extract shared initialization functions early to prevent duplication
- Document mode prop constraints explicitly in JSDoc
- Plan Suspense boundaries by data dependency, not component location
