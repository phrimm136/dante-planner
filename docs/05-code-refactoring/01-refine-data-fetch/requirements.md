# User Story: Refine Data Fetch

## Description
As a developer, I want consistent and fast data fetch so that I can see the data without error, immediately, when I open a data page.

## Acceptance Criteria
- Refactor `EGODetailPage.tsx` to use `useEntityDetailData`
- Refine the data fetch logic for egoGiftDetailPage so that each gift page loads the corresponding data, not the entire one
- Refine the data fetch logic to remove if-else chain
- Investigate where the undefined id comes and refine the data fetch logic to disable query with undefined id
- Create another {spec|i18n} list data fetch function for id, ego, and ego-gift and rewrite the list page functions to use them