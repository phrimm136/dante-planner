# Findings: Format Description Text

## What Was Easy

- Pattern reuse from `formatSanityCondition.ts` eliminated guesswork on pure function + regex approach
- Existing `useBattleKeywords` and `useColorCodes` hooks provided proven data fetching patterns
- Zod schema validation gave confidence in data structure before rendering
- Writing 62 tests exposed issues before user-facing problems
- Physical folder inspection resolved spec path ambiguity

## What Was Challenging

- Global regex state broke on multiple parses; required pivot to `String.matchAll()`
- Multi-type keyword resolution created tight coupling between resolver and rendering
- Icon loading state management introduced SRP violation in FormattedKeyword
- Newline handling timing could break if keywords span lines
- Color fallback chain has 3 levels vulnerable to data changes

## Key Learnings

- Stateless regex via `String.matchAll()` eliminates state reset bugs entirely
- Discriminated unions with type field enable exhaustive switch without error surprises
- Pure functions in lib/ + hooks for data = clean separation without tangled dependencies
- Accessibility requires explicit design upfront, not afterthought fixes
- Test coverage catches specification gaps (newlines, nested brackets)
- Icon `onError` handler prevents UI breakage via graceful degradation
- Click-based popover simpler than hover but still needs focus management

## Spec-Driven Process Feedback

- research.md mapping was accurate; all 6 pattern sources correctly identified
- Icon path clarification was critical; filesystem inspection resolved before code
- 5-phase execution order prevented circular dependencies
- Edge cases underspecified; newlines/nested brackets discovered in testing
- Color fallback chain should be explicitly documented in spec

## Pattern Recommendations

- Add skill note: global regex needs lastIndex reset or use String.matchAll()
- Document discriminated union + exhaustive switch pattern for multi-type resolution
- Include graceful degradation (onError handler) in component patterns for images
- Codify pure function + hook split pattern for testability
- Make accessibility (keyboard, ARIA) non-negotiable spec requirements

## Next Time

- Test edge cases during spec review, not test writing phase
- Verify actual asset paths exist before finalizing spec
- Audit component responsibility upfront; plan state extraction before building
- Add accessibility checklist to task spec template
- Explicate all fallback chains completely in spec to prevent assumptions
