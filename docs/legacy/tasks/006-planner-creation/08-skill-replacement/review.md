# Skill Replacement - Code Quality Review

## Spec-Driven Compliance

- Spec-to-Code Mapping FOLLOWED: All items from research.md implemented (12-sinner grid, modal, EA state, responsive breakpoints, image layers)
- Spec-to-Pattern Mapping FOLLOWED: Referenced SinnerGrid responsive pattern, Dialog from DeckBuilder, Suspense pattern from EGOGiftObservationSection
- Technical Constraints RESPECTED: EA per-sinner, constants used, shadcn Dialog, Suspense wrapping, useSuspenseQuery present
- Execution Order DEVIATED: DeckBuilder refactor happened mid-stream instead of final integration phase
- Gap Analysis ADDRESSED: All missing items created (SkillReplacementSection, SkillExchangeModal, SkillInfo type)
- Integration Points MATCHED: Section placed correctly, state lifted to page level
- i18n Pattern FOLLOWED: Keys added following pages.plannerMD pattern

## What Went Well

- Pattern compliance: Explicit function declarations, no manual memo/useCallback (React Compiler)
- Existing pattern reuse: SkillImageSimple extracted from SkillImageComposite pattern, colorCode JSON import matches existing pattern
- Type consolidation: SkillInfo centralized in DeckTypes.ts, eliminating 3-file duplication
- Constants usage: DEFAULT_SKILL_EA, OFFENSIVE_SKILL_SLOTS properly imported
- Responsive grid: Correct Tailwind breakpoints (grid-cols-2 sm:3 md:4 lg:6)

## Code Quality Issues

- [HIGH] State architecture mismatch: skillEAState not in DeckState type - dual sources of truth
- [HIGH] Breaking change scope: DeckBuilder consumers outside PlannerMDNewPage now broken
- [MEDIUM] Inline clipPath polygons could be Tailwind custom utilities
- [MEDIUM] Data fetching in wrong layer: SkillReplacementSection fetches internally vs receiving props
- [MEDIUM] Missing Zod schemas for SkillInfo and SkillEAState types
- [LOW] Magic numbers (scale-75, w-7 h-7) not in constants.ts
- [LOW] Translation gaps: "Current Skills", "Exchange Options" hardcoded in English

## Technical Debt Introduced

- skillEAState isolation: Not in DeckState means deck save/load needs future refactoring
- DeckBuilder API contract: Breaking change not documented in CHANGELOG
- Data layer mixing: SkillReplacementSection fetches internally unlike other sections
- Pattern divergence: Other sections receive props, this one fetches internally

## Backlog Items

- BL-001: Add SkillEAState to DeckState and integrate into deck save/load (deckCode.ts)
- BL-002: Extract clipPath polygon utilities to Tailwind config
- BL-003: Add Zod schemas for SkillInfo and SkillEAState with validation
- BL-004: Lift useIdentityListData to parent, pass skillInfos as props
- BL-005: Document DeckBuilder API changes and migration path
