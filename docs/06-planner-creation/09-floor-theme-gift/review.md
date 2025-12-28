# Review: Floor Theme Gift

## Spec-Driven Compliance

- **Spec-to-Code Mapping: PARTIAL FAIL** - Missing Layer 6 (attackType/attributeType icons) and Layer 8 (hover/selection highlights) from ThemePackViewer
- **Spec-to-Pattern Mapping: PASS** - Correctly followed StartGiftSection callback pattern, useSuspenseQuery with Zod
- **Technical Constraints: PASS** - Zod validation, difficulty rules (1F chain, 1-10 normal+hard, 11-15 extreme), gift filtering (matching ID OR empty)
- **Execution Order: MOSTLY FOLLOWED** - Plan phases 1-4 completed except image composition script has schema mismatch

## What Went Well

- Difficulty logic correctly chains (getAvailableDifficulties implements "1F OR previous normal" rule)
- Theme pack filtering maps floor numbers to selectableFloors indices correctly
- EGO gift filtering implements "themePack contains ID OR empty" efficiently
- State management uses parent-owned callback pattern (no prop drilling)
- Zod schemas use .strict() to catch data inconsistencies

## Code Quality Issues

- [HIGH] Schema missing Layer 6 fields (attackType, attributeType) - cannot render type icons
- [HIGH] No hover/selection highlight states (onHover.webp, onSelect.webp unused)
- [MEDIUM] getThemePackFramePath and getThemePackHighlightPath defined but never called
- [MEDIUM] specialName color tag parsing not implemented - always uses textColor
- [LOW] compose-theme-packs.py has double-slash syntax error on line 110

## Spec Gaps / Missing Features

- Layer 6: attackType/attributeType icons not rendered (spec: "on the lower-left")
- Layer 8: Hover/selection highlight frames not implemented
- Layer positions not configurable by user (spec: "must be configurable")
- specialName with color tags not parsed (spec: "ignore textColor and use the tag")

## Technical Debt

- Pre-composition blocks runtime layer toggling (logo visibility, boss sprite swap)
- Schema-script divergence requires coordinated migration to add fields
- Zero infrastructure for user-configurable layer positions

## Backlog Items

- Add attackType/attributeType overlay icons to ThemePackViewer lower-left
- Implement hover state with onHover.webp frame, selection state with highlight
- Sync ThemePackConfigSchema with compose-theme-packs.py expected fields
- Parse specialName color tags and override textColor when present
- Fix compose-theme-packs.py line 110 double-slash path error
