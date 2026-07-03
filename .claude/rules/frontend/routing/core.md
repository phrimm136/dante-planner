---
paths:
  - "frontend/src/lib/router.tsx"
  - "frontend/src/pages/**/*.tsx"
---

# Routing Core Patterns

## Mandatory Rules

- **Code-based routes** — no file-based routing; the whole route tree is defined programmatically in `src/lib/router.tsx` (no `src/routes/` dir, no generated route tree)
- **Validate params early** — before calling data hooks
- **Use `useSuspenseQuery`** in route components — loader guarantees data exists
- **Define `queryOptions` factories** alongside their hooks

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

## Data Loading Decision Tree

```
Route needs data?
  YES → Does data block the page render?
    YES → ensureQueryData in loader (blocks navigation until ready)
    NO  → prefetchQuery in loader (non-blocking, shows fallback)
  NO → No loader needed

Component reads data?
  Loader prefetched → useSuspenseQuery (no loading flash)
  No loader         → useQuery (shows loading state)
  Speculative        → prefetchQuery on hover/focus
```

- `ensureQueryData` respects `staleTime` — won't refetch if cache is fresh
- `prefetchQuery` fires and forgets — good for secondary data
- `defer` + `Await` — slow secondary data that should not block render

## Code Splitting

- TanStack Router `autoCodeSplitting` splits `component` automatically
- Do NOT re-export the component from the route file — breaks splitting
- Lazy load heavy sub-components with `React.lazy()` inside route components

## Layout Patterns

```
Route grouping:
  (auth)/     → pathless group, shared layout, no URL segment
  _layout.tsx → shared layout for a URL segment
  __root.tsx  → root layout, global providers
```

- Prefer flat routes with `()` grouping over deep nesting for unrelated sections
- Co-locate layout with its route group

## Forbidden Patterns

| Forbidden | Use Instead |
|---|---|
| Fetch data in `useEffect` | Loaders or query hooks |
| Duplicate loader logic in component | Loader prefetches, component reads cache |
| Plain `useQuery` after prefetch | `useSuspenseQuery` (avoids unnecessary loading) |
| Deep route nesting for unrelated sections | Flat routes with `()` grouping |
