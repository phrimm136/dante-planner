# Learning Reflection: StartBuff Summary/Edit Mode Separation

## What Was Easy
- Component separation pattern: StartBuffMiniCard/StartBuffCard split aligned with SRP
- Reusing existing utilities: getStartBuffIconPath, EGOGiftEnhancementIndicator available
- Test structure clarity: 33 tests with clear coverage for enhancement levels and edge cases
- Asset path helpers: Following assetPaths.ts convention made additions predictable

## What Was Challenging
- Premature abstraction tension: Spec called for MD6_ACCENT_COLOR but got Record pattern
- Type safety mismatch: MDVersion literal type vs runtime number acceptance gap
- Documentation inconsistency: research.md and code.md diverged on constant naming
- Magic numbers: Card/icon dimensions scattered rather than extracted to constants

## Key Learnings
- Spec-to-abstraction mapping requires discipline: YAGNI conflicts with future-proofing
- Documentation must travel with code changes: Deviations need immediate updates
- Type contracts matter: Type-only constraints need runtime validation to match
- Flex child text truncation is fragile: Parent sizing affects ellipsis behavior
- Component patterns are reusable across domains: EGOGift patterns worked for StartBuff
- Inline styles defeat Tailwind benefits: Acceptable trade-off but worth documenting

## Spec-Driven Process Feedback
- Research.md was mostly accurate: Only MD_ACCENT_COLORS vs MD6_ACCENT_COLOR deviated
- Plan execution order worked well: Foundation → Component → Refactor → Integration clean
- Edge cases in instructions.md were comprehensive: Tests covered all enhancement levels
- Missing clarity on constant strategy: No guidance on abstract vs simple constant decision

## Pattern Recommendations
- Add YAGNI vs Future-Proofing decision rubric to skill docs
- Extract magic numbers pattern: All inline px values need named constants
- Type-runtime alignment guidance: Literal types need enforced helper constraints
- Flex truncation pattern: Document parent sizing requirements for text-ellipsis
- Dual-purpose pattern exit criteria: When to refactor out of shared viewMode pattern

## Next Time
- Call out spec deviations upfront with tech debt ticket link
- Sync documentation before implementation complete
- Extract constants before building components
- Use type guards earlier to catch type-runtime mismatches
- Recognize shared styling patterns suggest component extraction opportunity
