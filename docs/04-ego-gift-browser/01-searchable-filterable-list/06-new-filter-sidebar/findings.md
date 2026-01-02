# Learning Reflection: EGO Gift Filter Sidebar

## What Was Easy

- Pattern reuse from RankFilter and SeasonDropdown reduced complexity
- Constants addition straightforward once data shape understood
- Type extensions minimal with familiar Zod patterns
- Filter logic extraction to utilities made testing natural
- FilterSidebar/FilterPageLayout handled responsive breakpoints automatically

## What Was Challenging

- Component location ambiguity: /common/ vs domain-specific folder decision
- Tier extraction fragility: no validation for malformed tag arrays
- Type casting in filter components due to dropdown value handling
- Missing translation key for "None" keyword option
- CSS visibility toggling makes filter state testing harder

## Key Learnings

- Spec-to-pattern mapping before coding prevents false starts
- Extracting OR/AND filter logic to utilities enables independent testing
- Secondary filters must remain active even when collapsed (state ≠ visibility)
- Discovering data fields during implementation is risky—validate upfront
- Icon path utilities incomplete—no getAttributeTypeIconPath() exists

## Spec-Driven Process Feedback

- Research.md mapping was accurate—pattern sources identified correctly
- Plan.md phases held but Steps 4-8 could parallelize (components independent)
- Instructions.md testing missed edge cases (rapid toggle, missing fields)
- Code.md honest about critical fixes rebuilt confidence

## Pattern Recommendations

- Document toggle button limits (3-6 buttons max before unwieldy)
- Standardize dropdown value types—avoid per-component casting
- Extract CSS-based filtering to shared useVisibilityFilter hook
- Create tier tag validation utility with explicit error handling
- Add translation key completeness check before implementation

## Next Time

- Front-load data shape validation with runtime tests
- Write filter utility tests before page integration
- Use explicit dependency matrix in plan.md for parallelization
- Validate icon paths exist before component creation
- Schedule mobile testing checkpoint earlier in process
