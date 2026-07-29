# Code Review: Start Buff Section

## Feedback on Code

**What went well:**
- Clean separation of concerns: types, hooks, formatting utilities, and UI components in separate files
- ID encoding scheme elegantly encodes both baseId and enhancement level in a single number
- useBattleKeywords hook follows project patterns with Zod validation and query key factory
- useLayoutEffect usage in AutoSizeText prevents visual flickering during text measurement

**What needs improvement:**
- Mixed language comments (Korean/English) reduces consistency and maintainability
- Negative ID convention for deselection is non-obvious and could cause confusion
- Hardcoded pixel values and magic numbers scattered throughout components

## Areas for Improvement

1. **State synchronization complexity**: StartBuffSection maintains duplicate enhancement state that must stay in sync with StartBuffCard's local state, creating potential for drift when selection changes externally

2. **Inefficient buff lookup**: Using array find operations to locate buffs by ID on every render; performance may degrade with larger datasets

3. **Incomplete loading state handling**: useBattleKeywords isPending and isError are destructured but never checked; UI may render with undefined battleKeywords

4. **Brittle border-image implementation**: EnhancementButton relies on pixel-perfect slice/border/outset values that must be manually updated if images change

5. **Component coupling**: StartBuffCard requires allBuffs array to look up enhanced versions, creating unnecessary data dependency

## Suggestions

1. **Create buff lookup map**: Convert allBuffs array to Map keyed by ID at the section level, pass lookup function instead of full array to cards

2. **Lift enhancement state**: Consider controlled component pattern where enhancement state lives entirely in parent, eliminating synchronization issues

3. **Add loading/error boundaries**: Wrap components in Suspense boundaries or add explicit loading states for battleKeywords fetch

4. **Extract magic numbers to constants**: Create configuration object for EnhancementButton slice/border values with descriptive names

5. **Consider memo optimization**: Wrap StartBuffCard in React.memo with custom comparison to prevent unnecessary re-renders when unrelated cards change
