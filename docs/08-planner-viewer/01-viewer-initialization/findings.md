# Learning Reflection

## What Was Easy

- Bottom-up component building: Leaf components had zero dependencies and built cleanly without ripple effects
- Pattern reuse from editor: GuideModeViewer leveraged exact PlannerMDNewPage layout structure
- Suspense + ErrorBoundary adoption: React primitives handled async loading cleanly with useSuspenseQuery pattern
- TypeScript catching prop contracts: Type system forced prop name consistency, preventing silent data flow breaks
- Test-driven validation: 14 passing tests immediately revealed hook naming inconsistencies

## What Was Challenging

- Hook naming and return signatures: useThemePackListData returned wrong shape, causing silent null reference bugs
- Route integration ambiguity: usePlannerStorage lacked assumed getSavedPlanner method, required wrapper hook
- Session-only state lifecycle: Empty array deployment reset vs equipment state required mid-implementation clarification
- Webkit scrollbar CSS limitations: Pseudo-elements don't evaluate CSS custom properties, required direct hex values
- Post-implementation feature bloat: 4 major enhancements after completion signaled planning gaps

## Key Learnings

- Silent data contracts break at runtime: Hook return shape mismatch compiled fine but broke at i18n key access
- Stable dependencies for memoization: Gift sorting must exclude hover state from dependency array
- Empty array state signals fallback: Using [] to mean "use preset" is implicit contract requiring explicit JSDoc
- Prop aliasing hides refactoring needs: Passing {spec, i18n} as {themePackList, themePackI18n} masked pattern violation
- Read-only enforcement needs blocking + visual: HTML disabled alone insufficient, must prevent onClick
- Scrollbar styling is global state: Required coordination across multiple components
- Optional props create flexibility: maxBytes optional enables reuse across editor and viewer modes
- Session-only state persists on mode switch: useEffect cleanup breaks tracker state preservation

## Spec-Driven Process Feedback

- Bottom-up order WAS effective: Leaf components first prevented cascade dependency breaks
- Research.md 85% accurate: Missed hook method signature (getSavedPlanner doesn't exist)
- Plan clarity gaps: Empty planner edge case listed as checkpoint without pre-implementation validation
- Route boundary assumptions failed: usePlannerStorage contract mismatch required mid-execution workaround
- Post-implementation scope creep: 4 enhancements suggest original spec was MVP-only, not complete feature

## Pattern Recommendations

- fe-data violations need linting: Hook return signatures must match {spec, i18n} contract, aliasing hides violations
- Session-only state JSDoc: Document reset behavior for empty deploymentOrder fallback explicitly
- Stable sort dependencies critical: Memoized sort functions must exclude hover/UI state from dependencies
- Empty array implicit contracts: Avoid using [] to signal fallback without explicit JSDoc and tests
- Read-only semantic naming: Use readOnly boolean, not disabled + disabled state for clearer intent
- Optional props for mode reuse: Document why props are optional in JSDoc (editor vs viewer modes)

## Runtime Bug Insights

- Bug #1 preventable by contract verification: Research didn't validate usePlannerStorage against actual implementation
- Bug #2 silent pattern violation: TypeScript compiled wrong return shape, no strict typing on hook returns
- Tests missed bugs by mocking wrong layer: Integration-level tests exercising hook return shapes needed
- Type validation came too late: config.type guard added during review, not upfront validation

## Post-Implementation Enhancement Insights

- 4 enhancements signal incomplete scope: Deck editing, skill EA, note pattern, read-only enforcement not in plan
- Scope creep timing indicates spec growth: All enhancements after step 19 completion
- Pattern restoration reveals early misalignment: Dialog to inline switch indicates missed design decision upfront

## Next Time

- Validate hook contracts before planning: Explicitly call target hooks, verify return shapes match assumptions
- Spec "complete feature" vs "MVP" explicitly: Document expected post-work in plan.md as separate phase
- Design pattern decisions upfront: Read entire editor reference for patterns before creating components
- Integration tests over unit tests: Test real planner data through actual hooks, not mocks
- Session-only reset behavior in JSDoc: Document empty array/null fallback explicitly in type definitions
- Edge case validation pre-implementation: Test empty planner, single-sinner deck, zero themes before step 1
- Component prop aliasing needs justification: Add comment explaining why aliases exist and document mapping
