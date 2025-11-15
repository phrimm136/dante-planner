# Findings and Reflections: Sinner Filter Implementation

## Key Takeaways

- State management with Set data structure was straightforward and provided efficient O(1) lookups for filtering operations
- Props drilling pattern worked well for this limited scope but will become cumbersome when combining multiple filters
- Theme system required extension to solve icon visibility issue, highlighting importance of testing UI components in both light and dark modes early
- Compact design iterations (h-20 to h-14, w-10 to w-8) revealed that initial mockup dimensions don't always translate well to actual screen space
- Horizontal scrolling for 12 items creates usability challenges on mobile that weren't apparent during desktop development
- Empty selection behavior (show all vs show none) is a UX decision that significantly impacts user experience but wasn't specified in requirements
- Sinner order in the filter differs from potential canonical order in game data, creating maintenance burden

## Things to Watch

- Hardcoded sinner list will require code changes whenever game adds new characters, creating ongoing maintenance debt
- Missing accessibility features (keyboard navigation, ARIA labels, screen reader support) limit application reach and may violate accessibility standards
- Filter state doesn't persist across page navigation or browser refresh, creating poor user experience for repeat visits
- Image loading failures have no fallback mechanism, leaving users with broken filter buttons and unclear options
- Mobile touch targets at w-8 may be too small for accurate selection, potentially causing user frustration

## Next Steps

- Implement URL query parameters or localStorage for filter persistence to maintain user preferences across sessions
- Add comprehensive accessibility support including keyboard navigation and proper ARIA attributes before public release
- Consider extracting sinner list from centralized data source to automatically sync with game content updates
- Design mobile-optimized filter UI (dropdown or modal) for viewports below tablet size to improve touch interaction
- Establish pattern for combining multiple filters (sinner, keyword, search) with clear precedence and interaction rules
