# Deck Builder - Findings and Reflections

## Key Takeaways

- Reusing existing IdentityCard/EGOCard components with added props was straightforward and avoided duplication
- Consolidating filter components into shared common/ directory reduced maintenance burden significantly
- Performance optimization requires careful measurement - initial over-engineering with excessive useCallback/useMemo added complexity without benefit
- The useRef pattern for stable callbacks is effective but non-obvious; warrants documentation
- IntersectionObserver-based lazy loading solved the initial page load bottleneck for hundreds of cards
- Renaming constants (BASE_LEVEL → MAX_LEVEL, SINS → AFFINITIES) touched many files but improved semantic clarity
- Spec list data structure differs from detail data structure - required separate schemas

## Things to Watch

- Hover-based interactions may cause issues on touch devices and with fast mouse movements
- Equipment state grows linearly with sinners (12) and EGO ranks (5) - manageable now but watch for complexity
- EA calculation weights (3/2/1 for skills) are hardcoded - may need configuration if game mechanics change
- Default equipment IDs assume specific naming pattern - will break if data conventions change
- Filter state resets on component unmount - users may expect persistence across navigation

## Next Steps

- Implement deck code import/export for sharing builds (deferred from initial scope)
- Add URL state synchronization for shareable deck links
- Extract equipment and deployment logic into dedicated custom hooks
- Consider click-to-open modal for TierLevelSelector to improve mobile UX
- Add integration tests for EA calculation correctness
