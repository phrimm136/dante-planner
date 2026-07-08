# Deck Builder - Code Review

## Feedback on Code

- **Good component separation**: DeckBuilder cleanly delegates to SinnerGrid, StatusViewer, and TierLevelSelector with clear responsibilities
- **Strong type safety**: DeckTypes.ts provides comprehensive interfaces for equipment state, preventing runtime errors
- **Effective code consolidation**: Merged duplicate filter components into shared SinnerFilter and KeywordFilter
- **Proper lazy loading**: IntersectionObserver in TierLevelSelector prevents loading hundreds of components on page load
- **Performance cleanup needed**: Initial over-optimization with unnecessary useCallback/useMemo was correctly identified and removed

## Areas for Improvement

1. **Large DeckBuilder component**: At ~400 lines, DeckBuilder handles too many concerns including state, filtering, sorting, and rendering. This makes testing and maintenance difficult.

2. **Inconsistent ID handling**: The codebase mixes sinner names (PascalCase strings) with sinner indices (numbers) for the same logical concept, creating confusion in equipment vs deploymentOrder.

3. **Missing deck persistence**: Equipment and deployment state lives only in component state. Users lose their configuration on page refresh or navigation.

4. **StatusViewer recalculates on every render**: The EA calculations depend on deckState which changes frequently. Without proper memoization boundaries, this causes unnecessary work.

5. **TierLevelSelector hover UX**: Mouse-enter/leave triggers can cause flickering when cursor moves between tier icons and the popup edges.

## Suggestions

1. **Extract custom hooks**: Move equipment management logic into useEquipment and deployment logic into useDeploymentOrder hooks to reduce DeckBuilder complexity.

2. **Standardize sinner identification**: Use a single identifier type (either name or index) consistently throughout, with explicit conversion functions at boundaries.

3. **Add URL state or localStorage persistence**: Store deck configuration in URL params or localStorage so users can share builds and retain state across sessions.

4. **Consider click-based popup**: Replace hover-based TierLevelSelector with click-to-open modal for better mobile support and more reliable interactions.

5. **Add unit tests for EA calculations**: The affinity weighting and keyword counting logic in StatusViewer is critical for correctness and should have dedicated tests.
