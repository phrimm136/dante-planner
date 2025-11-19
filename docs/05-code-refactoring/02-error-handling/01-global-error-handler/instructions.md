# Task: Global Error Handler

## Description
Install a global error handler that catches every uncaught error messages and shows them via toast. Implement error boundaries around all page components.

## Research
- There is error handling code in `queryClient.ts`, using `toast.error()`. Can I separate throwing errors and catching/notifying them?
- The toast messages should be popped up in upper-right area.

## Scope
- `/frontend/src/main.tsx`
- `/frontend/src/lib/queryClient.ts`

## Target Code Area
- `/frontend/src/lib/`
- `/frontend/src/main.tsx`
- `/frontend/src/lib/queryClient.ts`
- `/frontend/src/hooks/useEntityDetailData.ts`
- `/frontend/src/components/`

## Testing Guidelines