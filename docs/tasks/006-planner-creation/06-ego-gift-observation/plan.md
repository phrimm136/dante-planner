# EGO Gift Observation - Implementation Plan

## Clarifications Needed

No clarifications needed - requirements are clear

## Task Overview

Implement the EGO Gift Observation section for the MD planner creator/editor. This feature allows users to select up to 3 EGO gifts from a predefined observation pool of 291 gifts, displaying the cost in starlight based on the number of selections. The UI consists of three parts: cost display (upper-right), filterable gift selection list (left), and showroom of selected gifts (right). Selected gifts are propagated to the planner editor for database persistence.

## Steps to Implementation

1. **Reconstruct EGO Gift Card Components**: Rebuild both EGOGiftCard and EgoGiftMiniCard to display background image with tier indicator (upper-left), enhancement indicator (upper-right), and keyword icon (lower-right). Support base level and +1/+2 enhancement states with appropriate background overlays and hardcoded Tailwind scaling for bgEnhanced images.

2. **Create Observation Data Schema and Hook**: Define Zod schema for egoGiftObservationData.json validation including cost list and gift ID list. Implement custom TanStack Query hook following queryOptions pattern with 7-day staleTime to load observation pool data.

3. **Extract Starlight Cost Display Component**: Create reusable cost display component from StartBuffCard pattern showing starlight icon and cost number. Component should accept cost value as prop and handle styling for base vs enhanced states.

4. **Build Gift Selection List Component**: Create universal filterable EGO gift list component that accepts ID list filter, supports keyword/search filtering like main ego-gift page, and uses reconstructed gift cards with selection behavior instead of routing. Implement click-to-select/unselect functionality.

5. **Build Gift Showroom Component**: Create showroom component displaying up to 3 selected gifts in horizontal layout. Implement click-to-remove functionality and hover tooltips showing gift name and base description using lazy loading pattern.

6. **Create Observation Section Container**: Build main observation section component managing selection state with Set<string>, enforcing 3-gift maximum, calculating dynamic starlight cost from observation data, and coordinating between selection list and showroom.

7. **Integrate with PlannerMDNewPage**: Add observation section to planner page alongside existing start buff and start gift sections. Wire up state propagation callback to pass selected gift IDs to parent for database persistence.

8. **Add i18n Translation Keys**: Define translation keys for observation section UI text including section title, cost label, empty state messages, and any tooltip text following existing i18n patterns.

9. **Test Component Integration**: Verify selection state synchronization between list and showroom, cost calculation accuracy for 1-3 selections, filtering/search functionality, hover tooltips with lazy description loading, and proper data propagation to parent.

10. **Handle Edge Cases**: Implement proper handling for null/None keywords, empty selection state display, max selection enforcement, Set state immutability for re-renders, and validation error handling for malformed data.

## Success Criteria

- Both EGOGiftCard and EgoGiftMiniCard display all visual elements: background, tier badge, enhancement indicator, keyword icon
- Observation data loads via TanStack Query with proper Zod validation and caching
- Cost display shows correct starlight amount (70/160/270) based on selection count
- Selection list filters to only 291 observation-eligible gifts with keyword/search support
- Clicking cards in selection list toggles selection up to 3-gift maximum
- Showroom displays selected gifts with click-to-remove and hover tooltips
- Selected gift IDs propagate to PlannerMDNewPage for database save
- All UI text uses i18n translations and follows existing design patterns

## Assumptions Made

- Both EGOGiftCard and EgoGiftMiniCard are reconstructed to include all visual elements specified in requirements
- Starlight cost display is extracted as standalone reusable component for future use
- Gift selection list component is named generically for reuse in future features requiring full pool selection
- Observation section follows same layout pattern as start buff and start gift sections
- Background image scaling uses hardcoded Tailwind classes based on visual analysis rather than runtime calculation
- Enhancement state in observation section defaults to base level (0) with no user-controlled enhancement
- Filtering logic reuses patterns from EGOGiftList but adapted for selection behavior instead of routing
- Selection state managed entirely in observation section container, not in individual cards
