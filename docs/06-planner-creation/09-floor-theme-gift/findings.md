# Findings: Floor Theme Gift

## What Was Easy

- Data structure mapping: dungeonIdx/selectableFloors to UI logic was straightforward with clarifications
- Component hierarchy reuse: StartGiftSection and EGOGiftSelectionList patterns already proven
- Zod validation: useSuspenseQuery + Zod schemas worked seamlessly with existing infrastructure
- State management: Parent-owned callback pattern kept per-floor state clean
- i18n placeholders: Adding translation keys was simple additive change

## What Was Challenging

- Difficulty rule complexity: Chaining logic required explicit DUNGEON_IDX mapping and null checking
- Spec-to-code gaps: Layer 6/8 features discovered missing post-implementation via review
- Image composition scope: Pre-composed images + runtime text required understanding Python script first
- Floor index ambiguity: selectableFloors indices (0-4) representing different floor ranges
- Extreme pack special case: dungeonIdx 3 with no selectableFloors broke normal/hard patterns

## Key Learnings

- Spec precision matters: research.md clarifications prevented wrong implementations
- Pre-composition trade-offs: Static images prevent runtime toggling but simplify rendering
- Schema-script sync: Python script and TypeScript schemas must stay coordinated
- Floor categorization inheritance: Different floor counts share structure but different rules
- Empty category filtering: Don't render difficulty tabs with zero packs (filter before render)

## Spec-Driven Process Feedback

- Research.md: Good at clarifications; could include image layer ordering earlier
- Plan.md: Phase ordering worked but missed image asset generation dependencies
- Instructions.md: Layer 6/8 features weren't implemented due to asset/infrastructure gaps

## Pattern Recommendations

- Document image composition limits in skill docs (static vs runtime trade-off)
- Extract floor categorization (5F/10F/15F) into reusable hook
- Create automated schema-script validation tests
- Add FLOOR_DIFFICULTY_RULES constants for explicit business logic

## Next Time

- Add image validation step before component implementation
- Split research phase from implementation phase
- Run code review after component structure phase, not after full integration
- Create floor mapping reference tables to prevent index bugs
- Automate difficulty rule testing with explicit floor/difficulty matrix
