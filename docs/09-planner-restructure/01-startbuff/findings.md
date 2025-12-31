# Learning Reflection: StartBuff View+Edit Pane

## What Was Easy

- Optional props with defaults preserved backward compatibility without breaking changes
- Existing Dialog pattern from ThemePackSelectorPane was straightforward to replicate
- Clear specs enabled comprehensive test scenarios (18 total tests)
- StartBuffCard modifications were surgical—only ConditionalButton rendering changed

## What Was Challenging

- Grid rendering duplication occurred despite spec saying "thin wrapper"
- Spec interpretation gap: assumed wrap StartBuffSection, but rendered cards directly instead
- Hook abstraction tension: useStartBuffSelection for 2 callers—premature abstraction
- State complexity in cards: view/edit mode + selection + keyboard accessibility grew together

## Key Learnings

- "Thin wrapper" spec needs enforcement—code review catches intent violations
- Parallel phases expose gaps in what Section passes vs. what Card expects
- Test-driven validation essential—18 tests caught EditPane deviation from spec
- Props interface explosion risk as features layer (onViewClick, viewMode, onClick)
- Pattern consistency matters: FloorThemeGiftSection helpful but implementation deviated

## Spec-Driven Process Feedback

- Research mapping was accurate: Spec-to-Code tables identified all required changes
- Plan execution order worked: Sequential phases prevented circular dependencies
- Clarity gap: Spec said "reuse" but didn't explicitly forbid duplicate grid rendering

## Pattern Recommendations

- **Add to skills**: "View+Edit Pane Pattern: wrap viewer component, don't duplicate rendering"
- **Anti-pattern**: Don't extract hooks for 2 callers unless shared logic will grow
- **Props design**: Document when optional props create complexity vs. separate variants

## Next Time

- Catch duplication earlier: flag EditPane rendering duplicate grid during Phase 2
- Limit abstraction scope: define reuse threshold (3+ callers) before extracting hooks
- Refactor before tests: fix structural issues before finalizing test structure
- Manual testing step: verify IndexedDB persistence + cost integration explicitly
