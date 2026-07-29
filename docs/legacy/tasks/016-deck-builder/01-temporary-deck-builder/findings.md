# Learning Reflection: Standalone Deck Builder Page

## What Was Easy
- DeckBuilderPane extraction: 600+ lines of well-structured logic ready to move
- Store provider pattern: PlannerMDNewPage demonstrated Zustand setup cleanly
- Route integration: Route ordering rule documented in research; placement straightforward
- i18n additions: Identical key structure across language files
- Test verification: Manual testing checklist covered all major paths

## What Was Challenging
- State/ref hybrid architecture: Required simplification to single snapshot state
- Progressive rendering freeze: Hidden tab still rendering 100+ cards offscreen
- Discriminated union props: Implicit undefined mode defaults created ambiguity
- Store reset timing: Understanding useId lifecycle for ephemeral state guarantee
- Clipboard error handling: Missing logging made debugging harder

## Key Learnings
- Progressive rendering needs snapshot semantics to prevent re-sort jank
- Discriminated union props clarify intent and make contract testable
- Store provider lifecycle controls isolation without explicit cleanup hooks
- Scroll preservation timing is fragile; read before, write after state update
- Reference inventory before extraction catches hidden dependencies
- Tab-specific scroll refs prevent state coupling on entity mode switch
- CSS hiding scales better than conditional rendering for tab content

## Spec-Driven Process Feedback
- Research mapping highly accurate: All 9 spec-to-code mappings proved correct
- No spec ambiguities discovered during implementation
- Technical constraints prevented architectural rework
- Testing guidelines were complete: 24-step checklist covered all cases
- Pattern enforcement via line-by-line reference prevented missing handlers

## Pattern Recommendations
- Document discriminated union pattern in frontend-dev-guidelines
- Add progressive rendering batching to component patterns
- Create extraction checklist with reference inventory methodology
- Document Zustand store provider isolation behavior
- Extract clipboard operations to shared useClipboard hook

## Next Time
- Catalog hook/ref/memo/effect count before extraction
- Test store isolation explicitly before code review
- Add debug logging for scroll ref timing during development
- Use discriminated union props from start of architecture
- Profile progressive rendering on target device during feature development
