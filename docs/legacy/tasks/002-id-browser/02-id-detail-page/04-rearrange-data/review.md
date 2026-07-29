# Code Quality Review: Modular Detail Page Layout

## Spec-Driven Compliance

- Spec-to-Code Mapping: FOLLOWED - All 17 requirements from research.md implemented
- Spec-to-Pattern Mapping: FOLLOWED - TierLevelSelector, shadcn Tabs/Slider patterns applied
- Technical Constraints: PARTIALLY VIOLATED - useEffect in DetailEntitySelector violates React Compiler rules
- Execution Order: FOLLOWED - Foundation → Components → Layout → Integration as planned
- Breakpoint Deviation: DOCUMENTED - 768px → 1024px change justified in research.md

## What Went Well

- Excellent constant centralization (DETAIL_PAGE, SANITY_INDICATOR_COLORS)
- Strong composition pattern - DetailRightPanel + DetailEntitySelector reusable across entity types
- Proper Suspense wrapper pattern in IdentityDetailPage
- Type safety maintained with DetailEntityType union and explicit prop interfaces
- cn() utility used consistently, no template literals

## Code Quality Issues

- [HIGH] useEffect state sync in DetailEntitySelector violates React Compiler optimization rules
- [HIGH] HP calculation formula duplicated inline, not centralized for reuse
- [MEDIUM] Hardcoded z-10 in DetailRightPanel without SELECTOR_Z_INDEX constant
- [MEDIUM] Array index used as key for skill cards - fragile if order changes
- [LOW] Level input validation logic could be extracted to reusable hook

## Technical Debt Introduced

- Manual state synchronization pattern needs refactoring for React Compiler compliance
- HP formula duplication will need extraction when building EGO detail page
- Magic z-index value will need constants refactor when adding more sticky elements

## Backlog Items

- Extract HP/stat calculation to lib/calculations.ts utility
- Add SELECTOR_Z_INDEX to DETAIL_PAGE constants
- Refactor DetailEntitySelector to remove useEffect (controlled component pattern)
- Create useLevelInput custom hook for level validation reuse
- Add E2E test for mobile tab switching behavior
