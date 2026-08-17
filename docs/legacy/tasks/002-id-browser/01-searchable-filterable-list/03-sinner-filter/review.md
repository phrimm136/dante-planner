# Code Review: Sinner Filter Implementation

## Feedback on Code

**What Went Well:**
- Clean component separation with well-defined props interface makes the filter reusable and testable
- Efficient data structure choice using Set for O(1) membership testing performs well even with many identities
- Compact UI design (h-14 container, w-8 icons) provides good space efficiency without sacrificing usability
- Theme integration with new button color solves icon visibility across light and dark modes
- Clear user experience with empty selection showing all items follows common filter patterns

**Needs Improvement:**
- Hardcoded sinner list in component requires code changes when game adds new characters
- No accessibility features like keyboard navigation or ARIA labels makes filter unusable for screen readers
- Horizontal scrolling with 12 items may be difficult on mobile devices with narrow viewports
- Filter state resets on page navigation due to lack of persistence

## Areas for Improvement

1. **Data-driven sinner list**: The SINNERS constant array is hardcoded in the component. When the game adds new characters, developers must manually update this list. This creates maintenance burden and risk of desynchronization with the actual identity data.

2. **Accessibility gaps**: Filter buttons lack proper ARIA attributes, keyboard navigation, and focus management. Users relying on screen readers or keyboard-only navigation cannot effectively use the filter, limiting the application's reach.

3. **Mobile usability concerns**: With 12 small icons requiring horizontal scroll on mobile devices, users may struggle to see or select all options. The w-8 icons are particularly small on touch devices, increasing the chance of tap errors.

4. **Missing error boundaries**: If getSinnerIconPath fails or returns an invalid path, there's no fallback mechanism. Users would see broken images with no indication of what went wrong or which sinner the button represents.

5. **No filter state persistence**: Users lose their filter selections when navigating away from the page or refreshing the browser. This creates frustration for users who want to maintain their view preferences across sessions.

## Suggestions

1. **Derive sinner list from data source**: Extract the unique sinner list dynamically from the identity data or a centralized configuration file. This ensures the filter automatically stays synchronized as game content updates without requiring code changes.

2. **Implement comprehensive accessibility**: Add ARIA labels describing button states, support keyboard navigation with arrow keys and Enter/Space, manage focus visually, and announce selection changes to screen readers. Consider adding a visible label or count showing how many sinners are selected.

3. **Enhance mobile experience**: Consider a dropdown or modal-based selector for mobile viewports where horizontal space is limited. Implement larger touch targets on mobile devices, or add a condensed view that shows selected sinners with an expand button to see all options.

4. **Add filter state management layer**: Consider using URL query parameters or localStorage to persist filter selections. This allows users to bookmark filtered views or maintain their preferences across sessions. Implement a composable filter system that can combine sinner filter with upcoming keyword and search filters.

5. **Improve error handling and loading states**: Add fallback icons or text labels when images fail to load. Show loading indicators while fetching identity data. Consider lazy loading icons or using an icon sprite sheet to improve performance and reduce network requests.
