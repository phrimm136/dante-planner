---
paths:
  - "frontend/src/routes/**/*.tsx"
  - "frontend/src/routes/**/*.ts"
---

# Routing Core Patterns

## Mandatory Rules

- **Code-based routes** - No file-based routing
- **Validate params early** - Before calling data hooks
- **Suspense at page level** - Wrap content components

## Route Definition

```typescript
import { createRouter, createRoute, createRootRoute } from '@tanstack/react-router'

const rootRoute = createRootRoute({ component: RootLayout })

const identityDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/identity/$id',
  component: IdentityDetailPage,
})

export const routeTree = rootRoute.addChildren([identityDetailRoute])
export const router = createRouter({ routeTree })
```

## Page Template

```typescript
import { useParams } from '@tanstack/react-router'

export function IdentityDetailPage() {
  const { id } = useParams({ strict: false })

  if (!id) return <ErrorState message="No ID" />

  return (
    <ErrorBoundary fallback={<ErrorState />}>
      <Suspense fallback={<LoadingState />}>
        <IdentityDetailContent id={id} />
      </Suspense>
    </ErrorBoundary>
  )
}
```

**Reference:** `IdentityDetailPage.tsx`, `EGODetailPage.tsx`
