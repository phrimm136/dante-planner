# Routing Guide

TanStack Router implementation with code-based routing, lazy loading, and Suspense patterns. Uses shadcn/ui + Tailwind for styling.

---

## TanStack Router Overview

**TanStack Router** with code-based routing:
- Type-safe routing
- Lazy loading for code splitting
- Suspense integration
- Search params validation

---

## Basic Route Pattern

### Route Definition

```typescript
import { createFileRoute } from '@tanstack/react-router'
import { lazy, Suspense } from 'react'
import { LoadingState } from '@/components/common/LoadingState'

// Lazy load the page component
const IdentityList = lazy(() =>
  import('@/features/identity/components/IdentityList').then((module) => ({
    default: module.IdentityList,
  }))
)

export const Route = createFileRoute('/identity/')({
  component: IdentityPage,
})

function IdentityPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <IdentityList />
    </Suspense>
  )
}

export default IdentityPage
```

**Key Points:**
- Lazy load heavy components
- `createFileRoute` with route path
- Wrap in Suspense for loading states
- Export both Route and component

---

## Lazy Loading Routes

### Named Export Pattern

```typescript
import { lazy } from 'react'

// For named exports, use .then() to map to default
const MyPage = lazy(() =>
  import('@/features/my-feature/components/MyPage').then((module) => ({
    default: module.MyPage,
  }))
)
```

### Default Export Pattern

```typescript
import { lazy } from 'react'

// For default exports, simpler syntax
const MyPage = lazy(() => import('@/features/my-feature/components/MyPage'))
```

### Why Lazy Load Routes?

- Code splitting - smaller initial bundle
- Faster initial page load
- Load route code only when navigated to
- Better performance

---

## createFileRoute

### Basic Configuration

```typescript
export const Route = createFileRoute('/my-route/')({
  component: MyRoutePage,
})

function MyRoutePage() {
  return <div className="p-4">My Route Content</div>
}
```

### With Loader

```typescript
export const Route = createFileRoute('/my-route/')({
  component: MyRoutePage,
  loader: async () => {
    // Can prefetch data here
    const data = await api.getData()
    return { data }
  },
})

function MyRoutePage() {
  const { data } = Route.useLoaderData()
  return <div className="p-4">{data.title}</div>
}
```

### With Search Params

```typescript
import { z } from 'zod'

const searchSchema = z.object({
  query: z.string().optional(),
  page: z.number().optional().default(1),
  filter: z.enum(['all', 'active', 'inactive']).optional().default('all'),
})

export const Route = createFileRoute('/search/')({
  component: SearchPage,
  validateSearch: (search) => searchSchema.parse(search),
})

function SearchPage() {
  const { query, page, filter } = Route.useSearch()

  return (
    <div className="p-4">
      <p>Query: {query}</p>
      <p>Page: {page}</p>
      <p>Filter: {filter}</p>
    </div>
  )
}
```

---

## Dynamic Routes

### Parameter Routes

```typescript
// routes/identity/$identityId.tsx

export const Route = createFileRoute('/identity/$identityId')({
  component: IdentityDetailPage,
})

function IdentityDetailPage() {
  const { identityId } = Route.useParams()

  return (
    <Suspense fallback={<LoadingState />}>
      <IdentityDetail identityId={identityId} />
    </Suspense>
  )
}
```

### Multiple Parameters

```typescript
// routes/sinner/$sinnerId/identity/$identityId.tsx

export const Route = createFileRoute('/sinner/$sinnerId/identity/$identityId')({
  component: SinnerIdentityPage,
})

function SinnerIdentityPage() {
  const { sinnerId, identityId } = Route.useParams()

  return (
    <Suspense fallback={<LoadingState />}>
      <SinnerIdentityDetail sinnerId={sinnerId} identityId={identityId} />
    </Suspense>
  )
}
```

---

## Navigation

### Programmatic Navigation

```typescript
import { useNavigate } from '@tanstack/react-router'
import { Button } from '@/components/ui/button'

export function MyComponent() {
  const navigate = useNavigate()

  const handleClick = () => {
    navigate({ to: '/identity' })
  }

  return <Button onClick={handleClick}>View Identities</Button>
}
```

### With Parameters

```typescript
const handleNavigate = () => {
  navigate({
    to: '/identity/$identityId',
    params: { identityId: '10101' },
  })
}
```

### With Search Params

```typescript
const handleSearch = () => {
  navigate({
    to: '/search',
    search: { query: 'test', page: 1, filter: 'active' },
  })
}
```

### Link Component

```typescript
import { Link } from '@tanstack/react-router'

<Link
  to="/identity/$identityId"
  params={{ identityId: '10101' }}
  className="text-primary hover:underline"
>
  View Identity
</Link>
```

---

## Route Layout Pattern

### Root Layout

```typescript
// routes/__root.tsx
import { createRootRoute, Outlet } from '@tanstack/react-router'
import { AppLayout } from '@/components/layout/AppLayout'

export const Route = createRootRoute({
  component: RootLayout,
})

function RootLayout() {
  return (
    <AppLayout>
      <Outlet />
    </AppLayout>
  )
}
```

### AppLayout Component

```typescript
import { cn } from '@/lib/utils'

interface AppLayoutProps {
  children: React.ReactNode
}

export function AppLayout({ children }: AppLayoutProps) {
  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-50 border-b bg-background/95 backdrop-blur">
        <nav className="container flex h-14 items-center">
          {/* Navigation content */}
        </nav>
      </header>

      <main className="container py-6">
        {children}
      </main>
    </div>
  )
}
```

### Nested Layouts

```typescript
// routes/dashboard/index.tsx

export const Route = createFileRoute('/dashboard/')({
  component: DashboardLayout,
})

function DashboardLayout() {
  return (
    <div className="flex gap-6">
      <aside className="w-64 shrink-0">
        <DashboardSidebar />
      </aside>
      <div className="flex-1">
        <Outlet />
      </div>
    </div>
  )
}
```

---

## Complete Route Example

```typescript
/**
 * Identity detail route
 * Path: /identity/:identityId
 */
import { createFileRoute } from '@tanstack/react-router'
import { lazy, Suspense } from 'react'
import { ErrorBoundary } from 'react-error-boundary'

import { LoadingState } from '@/components/common/LoadingState'
import { ErrorFallback } from '@/components/common/ErrorFallback'

const IdentityDetail = lazy(() =>
  import('@/features/identity/components/IdentityDetail').then((module) => ({
    default: module.IdentityDetail,
  }))
)

export const Route = createFileRoute('/identity/$identityId')({
  component: IdentityDetailPage,
})

function IdentityDetailPage() {
  const { identityId } = Route.useParams()

  if (!identityId) {
    return (
      <div className="flex items-center justify-center p-8">
        <p className="text-muted-foreground">No identity ID provided</p>
      </div>
    )
  }

  return (
    <ErrorBoundary FallbackComponent={ErrorFallback}>
      <Suspense fallback={<LoadingState />}>
        <IdentityDetail identityId={identityId} />
      </Suspense>
    </ErrorBoundary>
  )
}

export default IdentityDetailPage
```

---

## Search Params Patterns

### Type-Safe Search Params

```typescript
import { z } from 'zod'
import { createFileRoute } from '@tanstack/react-router'

// Define search schema
const identitySearchSchema = z.object({
  sinner: z.string().optional(),
  rarity: z.coerce.number().optional(),
  affinity: z.string().optional(),
  sort: z.enum(['name', 'rarity', 'recent']).optional().default('name'),
})

type IdentitySearch = z.infer<typeof identitySearchSchema>

export const Route = createFileRoute('/identity/')({
  component: IdentityListPage,
  validateSearch: (search) => identitySearchSchema.parse(search),
})

function IdentityListPage() {
  const search = Route.useSearch()
  const navigate = useNavigate()

  const updateSearch = (updates: Partial<IdentitySearch>) => {
    navigate({
      to: '/identity',
      search: { ...search, ...updates },
    })
  }

  return (
    <div className="space-y-4">
      <FilterBar
        filters={search}
        onFilterChange={updateSearch}
      />
      <Suspense fallback={<LoadingState />}>
        <IdentityList filters={search} />
      </Suspense>
    </div>
  )
}
```

### Preserving Search Params

```typescript
// Navigate while preserving current search params
navigate({
  to: '/identity/$identityId',
  params: { identityId: '10101' },
  search: (prev) => prev,  // Keep all existing search params
})

// Or selectively keep some params
navigate({
  to: '/identity',
  search: (prev) => ({
    filter: prev.filter,  // Keep filter
    // page resets to default
  }),
})
```

---

## Error Handling in Routes

### Route Error Boundary

```typescript
export const Route = createFileRoute('/identity/$identityId')({
  component: IdentityDetailPage,
  errorComponent: RouteErrorComponent,
})

function RouteErrorComponent({ error }: { error: Error }) {
  return (
    <div className="flex flex-col items-center justify-center p-8 text-center">
      <h2 className="text-lg font-semibold text-destructive">
        Failed to load identity
      </h2>
      <p className="mt-2 text-sm text-muted-foreground">
        {error.message}
      </p>
      <Button
        onClick={() => window.location.reload()}
        className="mt-4"
      >
        Reload Page
      </Button>
    </div>
  )
}
```

### Not Found Route

```typescript
// routes/404.tsx or use notFoundComponent
export const Route = createFileRoute('/404')({
  component: NotFoundPage,
})

function NotFoundPage() {
  return (
    <div className="flex flex-col items-center justify-center min-h-[50vh] text-center">
      <h1 className="text-4xl font-bold">404</h1>
      <p className="mt-2 text-muted-foreground">Page not found</p>
      <Link to="/" className="mt-4 text-primary hover:underline">
        Return home
      </Link>
    </div>
  )
}
```

---

## Summary

**Routing Checklist:**
- Use `createFileRoute` with route path
- Lazy load components with `React.lazy()`
- Wrap in `Suspense` for loading states
- Use `ErrorBoundary` for error handling
- Use `Route.useParams()` for dynamic params
- Use `Route.useSearch()` for search params
- Validate search params with Zod
- Use `useNavigate()` for programmatic navigation

**See Also:**
- [component-patterns.md](component-patterns.md) - Lazy loading patterns
- [loading-and-error-states.md](loading-and-error-states.md) - Suspense usage
- [complete-examples.md](complete-examples.md) - Full route examples
