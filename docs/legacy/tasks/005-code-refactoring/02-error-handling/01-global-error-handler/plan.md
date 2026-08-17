# Implementation Plan: Global Error Handler

## Clarifications Resolved

- Error boundaries catch only unexpected render and logic errors (component crashes, null references)
- Query errors remain handled via QueryCache onError toast plus page-level ErrorState components
- Error boundary displays full error page with actual error message and reset button
- Root-level error boundary wraps entire application as last-resort catch-all
- Query errors show toast notification AND page-level ErrorState without triggering error boundary

## Task Overview

Implement global error handling infrastructure using error boundaries to catch unexpected render and logic errors. Configure toast notifications to appear in upper-right area. Standardize error UI by ensuring all list pages use ErrorState component consistently matching detail pages. Maintain existing QueryCache onError toast pattern for query failures.

## Steps to Implementation

1. **Install react-error-boundary package**: Add dependency to package.json and install via npm to enable error boundary functionality in React application.

2. **Configure Toaster positioning**: Update Toaster component in main.tsx with position prop set to top-right to match requirement for upper-right toast placement.

3. **Create ErrorBoundary wrapper component**: Build reusable error boundary component wrapping react-error-boundary with custom fallback UI showing full error page with actual error message and reset button.

4. **Wrap application with root-level error boundary**: Add ErrorBoundary wrapper around RouterProvider in main.tsx to catch unexpected render and logic errors as last-resort protection.

5. **Standardize list page error UI**: Update IdentityPage, EGOPage, and EGOGiftPage to replace plain text error messages with ErrorState component matching detail page patterns.

6. **Update LoadingState usage**: Ensure list pages use LoadingState component instead of plain text for consistent pending state UI across all pages.

7. **Test error scenarios**: Verify error boundaries catch render errors, query errors show toast plus ErrorState, toast notifications appear in upper-right corner, and error UI consistent across pages.

## Success Criteria

- react-error-boundary installed and imported successfully in application
- Toaster component configured with position="top-right" rendering toast notifications in upper-right corner
- Root-level error boundary wraps RouterProvider catching unexpected render and logic errors without crashing entire application
- Error boundary fallback UI displays full error page with actual error message and reset button allowing recovery without browser refresh
- List pages use ErrorState and LoadingState components consistently matching detail page patterns
- Query errors continue showing toast notifications via QueryCache onError plus page-level ErrorState rendering
- All error scenarios tested including render errors, query failures, and dynamic import failures
- No regressions in existing error handling behavior for detail pages or API 401 handling

## Assumptions Made

- Error boundaries catch only unexpected render and logic errors (component crashes, null references) not query errors
- Hooks continue returning error states as isError flag rather than throwing errors avoiding refactor of all consuming pages
- Root-level error boundary wraps entire application as last-resort protection without page-level granularity
- Error boundary fallback UI uses full page replacement displaying actual error message and reset button
- Query errors handled via dual pattern: QueryCache onError toast notification plus page-level ErrorState component rendering
- List page standardization replaces plain text error divs with proper ErrorState and LoadingState component calls
- Error recovery mechanism uses react-error-boundary resetErrorBoundary function clearing error state and re-rendering
