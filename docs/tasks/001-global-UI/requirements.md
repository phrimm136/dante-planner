# Epic: Global UI Framework

## Description
The global UI defines the app-wide layout and styling rules. It includes grid or flex layout, theme variables, font hierarchy, and shared component styles. All user stories (such as lists, dashboards, and Mirror Dungeon planners) will be displayed within this global layout.

## Acceptance Criteria
- A base layout component wraps all pages (header, content, footer regions defined).
- A consistent color theme, spacing system, and typography scale are implemented.
- Global styles are loaded once and applied app-wide.
- Responsive design works across major screen sizes (mobile, tablet, desktop).
- Page components render correctly inside the global container.

## Dependencies
- None (this is foundational).
- Must be completed before implementing any feature that requires visual layout consistency (e.g., TopBar, SearchableList, Dashboard)