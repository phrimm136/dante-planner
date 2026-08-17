# Learning Reflection: Identity Filter & Search Unified UI

## What Was Easy

- Reusing existing filter wrapper patterns (SinnerFilter, KeywordFilter) for new icon-based filters
- Leveraging shadcn/ui Sheet component for mobile drawer without custom implementation
- Applying responsive design via Tailwind breakpoints (lg) without complex media queries
- Multi-select dropdown pattern with checkbox items from existing DropdownMenu
- Constants extraction (FILTER_SIDEBAR_WIDTH, SEASONS, ASSOCIATIONS) prevented hardcoding

## What Was Challenging

- Collapsible accordion tracking expanded state + active filter count simultaneously
- Spec stated collapsible FilterSection but implementation removed it without documented justification
- Dual-slot pattern (children + primaryFilters/secondaryFilters) created confusion about usage
- Mobile icon sizing counterintuitively larger than desktop
- FilterSection accepts defaultExpanded/activeCount props but ignores them - misleading API

## Key Learnings

- Stub implementations (non-collapsible FilterSection) introduce technical debt - need clear TODOs
- Set<T> state with single handleResetAll scales well for multiple filter types with AND logic
- Responsive layout better as distinct component renders rather than single conditional component
- useFilterI18nData sequential queries left performance optimization on table (use useSuspenseQueries)
- Accessibility props (aria-controls/aria-expanded) should be added even if not interactive yet

## Spec-Driven Process Feedback

- Research.md accurately mapped requirements with minimal ambiguities
- Plan.md bottom-up order worked until Phase 4 revealed FilterSection incompleteness
- Status.md drift: reported "pre-existed with Collapsible" but lacks actual behavior
- Instructions.md comprehensive but collapsible requirement removed mid-implementation without approval

## Pattern Recommendations

- Add "FilterSection with collapsible state + active count badge" pattern to skill docs
- Document anti-pattern: props silently ignored (defaultExpanded, activeCount) - prefer explicit errors
- Extract generic MultiSelectDropdown<T> from Season/Association for reuse
- Document responsive sidebar pattern: desktop sticky + mobile Sheet with shared content
- Add keyboard navigation pattern: Tab headers, Space/Enter toggle, Arrow keys navigate

## Next Time

- Enforce spec deviations documented in review before marking complete
- Create explicit TODO comments with acceptance criteria for stub features
- Use useSuspenseQueries from start for parallel i18n loading
- Single-slot pattern for FilterSidebar (CSS-based mobile visibility) simpler than dual-slot
- Verify all feature checklist items (F1-F12, E1-E2) actually tested before marking complete
