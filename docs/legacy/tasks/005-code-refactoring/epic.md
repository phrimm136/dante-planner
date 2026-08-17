# Epic: Code Refactoring

## Description
Refactor the code so that the project becomes fail-safe, robust, modular, and performant.

## Acceptance Criteria
- Hardcoded values must be extracted to configuration data.
- Build error boundary with comprehensive error handling using toast UI to inform the users/developers the errors and prevent crash
- Implement ImageWithFallback component to alert the image loading error
- Add runtime validation across all JSON loading to prevent silent data loading failure
- Refine the data fetch logic for egoGiftDetailPage so that each gift page loads the corresponding data, not the entire one
- Refine the data fetch logic to remove if-else chain
- Investigate where the undefined id comes and refine the data fetch logic to disable query with undefined id
- Rewrite the EGODetailPage.tsx to use `useEntityDetailData`
- Create another {spec|i18n} list data fetch function for id, ego, and ego-gift and rewrite the list page functions to use them
- Add performance monitor for asset cache and large imports
- Add SSR support