# Code Review: Start Gift Selection

## Feedback on Code

### What Went Well
- Strong adherence to existing patterns from StartBuffSection and EGOGiftCard components
- Proper React Query implementation with query keys, staleTime, and appropriate caching
- Clean component composition with well-separated concerns
- Lazy loading for gift descriptions optimizes performance and avoids loading all i18n files
- TypeScript types and Zod schemas provide runtime validation for data integrity

### What Needs Improvement
- Missing translation keys in code documentation but likely not in actual i18n files
- EA calculation logic spread across components could benefit from centralization
- Event propagation handling in StartGiftRow uses stopPropagation which can complicate debugging
- No keyboard navigation support for accessibility
- State management could be simplified by lifting more state to parent component

## Areas for Improvement

### Accessibility Issues
- Keyboard navigation not implemented for gift card selection and row selection
- No ARIA labels or roles for screen reader support
- Focus management not handled when selections change
- Missing keyboard shortcuts for common operations like deselecting or clearing selections
- Impact: Users relying on keyboard or assistive technologies cannot use the feature

### Component Coupling
- StartGiftRow directly calls multiple callbacks and manages complex click logic
- Gift selection logic split between StartGiftSection and StartGiftRow creates tight coupling
- EgoGiftMiniCard has both display and selection concerns mixed together
- Impact: Difficult to reuse components in other contexts, harder to test in isolation

### Error Handling Gaps
- No error boundaries to catch rendering failures in child components
- Validation errors from Zod schemas only logged but no user-facing error messages
- Failed data loads show generic "Failed to load data" without retry mechanism
- Missing fallback images for icons that fail to load
- Impact: Poor user experience when data loading or rendering fails

### Performance Concerns
- parseStyleTags creates new React elements on every render without memoization
- No virtualization for the 10 keyword rows despite being a scrollable list
- Color code lookup happens on every EgoGiftMiniCard render without caching
- useEffect dependency on selectedGiftIds object reference causes unnecessary rerenders
- Impact: Potential performance degradation with frequent reselections or large gift sets

### Hardcoded Values
- Ring color for selection highlight hardcoded as fcba03 instead of using theme tokens
- MaxSelectable calculation has hardcoded base value of 1 instead of configuration
- Tooltip max width and styling hardcoded instead of using design system
- Impact: Inconsistent styling, difficult to maintain theme changes globally

## Suggestions

### Centralize Selection Logic
- Extract all selection state management into a custom hook like useGiftSelection
- Move EA calculation and validation logic into this hook
- Simplify component props to just display state and single onChange callback
- Provides single source of truth for selection rules and makes testing easier

### Enhance Accessibility
- Add keyboard navigation with arrow keys for row and gift selection
- Implement ARIA attributes for selection states and disabled states
- Add focus management to highlight selected row and gifts
- Include escape key handler to clear selections
- Makes feature usable for all users regardless of input method

### Improve Data Loading UX
- Replace generic loading text with skeleton components matching final layout
- Add retry buttons on error states with exponential backoff
- Show progressive loading states for individual components instead of entire section
- Implement optimistic updates for instant feedback on selections
- Creates smoother experience and reduces perceived loading time

### Optimize Rendering Performance
- Memoize parseStyleTags results and cache by description text
- Use React.memo on EgoGiftMiniCard with custom comparison function
- Convert selectedGiftIds Set to stable reference using useMemo
- Consider virtualization if keyword count grows beyond 10 in future versions
- Reduces unnecessary renders and improves responsiveness

### Extract Reusable Patterns
- Create shared SelectableCard component abstraction used by both buff and gift cards
- Extract tooltip with lazy-loaded content pattern into reusable hook
- Create design system tokens for selection highlight colors and styles
- Build generic keyword selector component for reuse across different sections
- Reduces code duplication and ensures consistency across features
