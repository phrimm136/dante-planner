# Learning Reflection: DeckBuilder Popup Pane Refactor

## What Was Easy
- Pattern-driven creation using StartBuff Summary+EditPane blueprint
- State lifting clarity - spec defined DeckFilterState upfront
- SinnerGrid/StatusViewer already pure (no extraction needed)
- shadcn/ui Dialog + DeckBuilderActionBar ensured button consistency
- Existing utilities (deckCode.ts, filter logic) required no reimplementation

## What Was Challenging
- contentReady useState/useEffect pattern created unnecessary re-renders
- "Identical positioning" harder than expected due to CSS context differences
- Filter persistence edge cases (Set<string> survival on unmount)
- Hardcoded "Formation" string missed until review phase

## Key Learnings
- Spec-to-code mapping works best as two-pass process
- Type-first implementation (DeckFilterState) prevents prop mismatches
- Shared components solve visual consistency AND single mutation point
- React Query cache inheritance eliminated refetch concerns
- "Implementation complete" != bug-free; review found 3 reliability issues
- Automated tests for edge cases (rAF cleanup) still pending

## Spec-Driven Process Feedback
- Research.md mapping was 100% accurate; saved ~2 hours exploration
- Types → Components → Integration → Cleanup order prevented state bugs
- "MUST Read First" table prevented reinventing StartBuff patterns
- Gap: "Positioned identically" needed CSS context clarification

## Pattern Recommendations
- Document contentReady as anti-pattern; use conditional rendering instead
- Add Set<string> persistence pattern for filter state
- Promote StartBuff Summary+EditPane as official reusable pattern
- Spell out visual consistency with CSS context requirements
- Document rAF cleanup must guard against unmount

## Next Time
- Run constants/pattern check BEFORE implementation, not at review
- Add screenshot comparison test for visual consistency requirements
- Include test coverage in spec phase (not "optional")
- Two-phase review: architecture pass + reliability pass
- Clarify persistence scope: page reload vs just Dialog toggle
