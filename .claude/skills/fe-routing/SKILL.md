---
name: fe-routing
description: TanStack Router patterns. Code-based routing, route params, navigation.
---

# Frontend Routing Patterns

## Rules

- **Code-based routes** - No file-based routing
- **Validate params early** - Before calling data hooks
- **Suspense at page level** - Wrap content components
- **Use `Link` component** - Not `<a href>`

## File Structure

```
src/routes/
├── index.tsx           # Route tree definition
├── HomePage.tsx
├── IdentityPage.tsx
├── IdentityDetailPage.tsx
└── EGOPage.tsx
```

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

## Navigation

```typescript
import { Link, useNavigate } from '@tanstack/react-router'

// Declarative
<Link to="/identity/$id" params={{ id: '10101' }}>View</Link>

// Programmatic
const navigate = useNavigate()
navigate({ to: '/identity/$id', params: { id } })
```

## Reference

- Pattern: `IdentityDetailPage.tsx`, `EGODetailPage.tsx`
- Router: `@tanstack/react-router`
- Why: `docs/learning/frontend-patterns.md`
