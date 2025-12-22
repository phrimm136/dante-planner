# Implementation Plan: Create Planner Page

## Clarifications Needed

No clarifications needed - requirements are clear.

## Task Overview

Create a new page at `/planner/md/new` with three core UI elements: a category selector (5F/10F/15F, default 5F), a multi-select keyword filter with icons (all 17 keywords selectable, no default selection), and a text input for plan name (max 256 bytes UTF-8). This serves as the foundation page for the planner creation feature.

## Steps to Implementation

1. **Add route definition**: Register `/planner/md/new` route in router.tsx using createRoute()
2. **Create page component**: Add PlannerMDNewPage.tsx in routes folder with standard page layout
3. **Add category constant**: Define MD_CATEGORIES constant (5F, 10F, 15F) in constants.ts
4. **Create combined keyword constant**: Define PLANNER_KEYWORDS by merging KEYWORD_ORDER + SINS
5. **Add custom icon path function**: Create getPlannerKeywordIconPath() that routes to correct icon directory
6. **Build category selector**: Use Button group for 5F/10F/15F selection, default to 5F
7. **Implement keyword multi-selector**: Use IconFilter with combined keywords, empty Set default
8. **Add name input field**: Create controlled text input with 256-byte UTF-8 validation
9. **Add i18n translations**: Add page title, description, and labels to all 4 language files
10. **Wire up state management**: Use useState for category ('5F'), keywords (Set), and name

## Success Criteria

- Page accessible at /planner/md/new route
- Category selector displays 5F, 10F, 15F with 5F selected by default
- All 17 keyword icons render correctly with no pre-selection
- Keyword selection allows all to be selected simultaneously
- Name input enforces 256-byte UTF-8 limit with validation feedback
- Page follows existing layout patterns (container, card, spacing)
- All UI text uses i18n translations
- No TypeScript or console errors

## Assumptions Made

- Using IconFilter component for keyword selection (matches existing patterns)
- Category selector will be a Button group (simpler than dropdown for 3 options)
- Icons exist for all keywords in statusEffect and sin directories
- Name input shows remaining bytes or error when limit exceeded
- No data persistence needed for this task (state management only)
