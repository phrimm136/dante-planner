# Research: Global Error Handler

## Overview of Codebase

- QueryCache onError callback in queryClient.ts automatically shows toast.error for all query failures globally
- Sonner v2.0.7 library provides toast notifications with default configuration and no custom positioning
- Toaster component rendered at QueryClientProvider level in main.tsx with no props or styling
- ErrorState component exists at components/common/ErrorState.tsx accepting title and message props with centered destructive styling
- LoadingState component also available for consistent pending state UI across pages
- Detail pages consistently use ErrorState component when hooks return isError flag
- List pages use plain text error messages without proper ErrorState component creating inconsistent UX
- Hooks return error states as properties including isError boolean, error object, isPending boolean, and data undefined on error
- Hooks do not throw errors allowing pages to manually check isError and render ErrorState or fallback text
- No error boundaries exist anywhere in application meaning unhandled errors crash entire app
- No separation between error occurrence and error notification as queryClient immediately toasts all query errors
- ApiClient in lib/api.ts throws errors with HTTP status codes and handles 401 by clearing tokens and redirecting
- React-error-boundary library not installed requiring manual installation if using error boundary pattern
- Dynamic imports in query functions can throw module not found errors caught by React Query system
- Dependent queries in hooks mean i18n query depends on data query success allowing independent failure modes
- TanStack Router used for routing with GlobalLayout wrapping all routes via Outlet component
- Component hierarchy flows QueryClientProvider wrapping RouterProvider wrapping ThemeProvider wrapping AuthProvider wrapping GlobalLayout

## Codebase Structure

- Main entry point at frontend/src/main.tsx renders provider hierarchy with Toaster component at top level
- Query client configuration centralized in frontend/src/lib/queryClient.ts with QueryCache and defaultOptions
- Error UI components located at frontend/src/components/common/ including ErrorState.tsx and LoadingState.tsx
- Router configuration at frontend/src/lib/router.tsx defines GlobalLayout and route structure with Outlet
- Page components in frontend/src/routes/ including IdentityPage, EGOPage, EGOGiftPage with detail and list views
- Hooks in frontend/src/hooks/ including useEntityDetailData.ts and useEntityListData.ts for data fetching
- Detail pages like IdentityDetailPage located at frontend/src/routes/identity/$identityId.tsx using file-based routing
- ApiClient utility at frontend/src/lib/api.ts handles HTTP requests with error throwing and 401 handling
- GlobalLayout component wraps all routes providing shared layout structure including navigation and Outlet
- Data types centralized in frontend/src/types/ including IdentityTypes.ts, EGOTypes.ts, EGOGiftTypes.ts
- Static data files at frontend/src/static/data/ loaded via dynamic imports in query functions
- I18n translation files at frontend/src/static/i18n/ with language-specific name lists

## Gotchas and Pitfalls

- QueryCache onError fires for every query error preventing selective error notification or handling strategies
- No way to distinguish between critical errors requiring toast versus expected errors handled silently
- Error boundaries require hooks to throw errors but current hooks return error states requiring refactor
- Wrapping entire app in error boundary would catch all errors but prevent granular recovery per page
- Toaster default positioning may not match desired upper-right area requiring position prop configuration
- List pages inconsistently use plain text instead of ErrorState component requiring standardization
- Installing react-error-boundary adds new dependency requiring package.json update and team approval
- Error boundary fallback UI needs design for consistent branding with existing ErrorState component
- Separating error throwing from notification in queryClient breaks existing automatic toast behavior
- Pages expect hooks to return isError flag so changing to throw errors requires updating all consumers
- Dynamic import errors from missing data files surface as query errors triggering unnecessary toasts
- 401 errors already handled specially in ApiClient potentially conflicting with global error handler
- Error boundary cannot catch errors in event handlers or async code outside React render lifecycle
- Multiple error boundaries at different levels create nested fallback UI requiring careful placement strategy
- No error recovery mechanism exists so users cannot retry failed operations without full page reload
