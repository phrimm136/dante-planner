# Routing Guide

TanStack Router with code-based routing for LimbusPlanner.

---

## Routing Architecture

This project uses **code-based routing** (not file-based). Routes are defined in a central router file, and page components are regular React components.

```
frontend/src/
├── lib/router.tsx          # Route definitions
└── routes/                  # Page components
    ├── __root.tsx          # Root route export
    ├── HomePage.tsx
    ├── IdentityPage.tsx
    ├── IdentityDetailPage.tsx
    └── ...
```

---

## Route Definition

### Central Router File

All routes are defined in `lib/router.tsx`:

```typescript
import { createRouter, createRootRoute, createRoute } from '@tanstack/react-router'
import { Outlet } from '@tanstack/react-router'
import { GlobalLayout } from '@/components/GlobalLayout'
import HomePage from '@/routes/HomePage'
import IdentityPage from '@/routes/IdentityPage'
import IdentityDetailPage from '@/routes/IdentityDetailPage'

// Root route with layout
const rootRoute = createRootRoute({
  component: () => (
    <GlobalLayout>
      <Outlet />
    </GlobalLayout>
  ),
})

// Static route
const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: HomePage,
})

// List route
const identityRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/identity',
  component: IdentityPage,
})

// Dynamic route with parameter
const identityDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/identity/$id',
  component: IdentityDetailPage,
})

// Create route tree
const routeTree = rootRoute.addChildren([
  indexRoute,
  identityRoute,
  identityDetailRoute,
])

// Export router
export const router = createRouter({ routeTree })

// Type registration
declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
```

---

## Page Components

Page components are regular React components in the `routes/` directory:

```typescript
// routes/IdentityDetailPage.tsx
import { useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { LoadingState } from '@/components/common/LoadingState'
import { ErrorState } from '@/components/common/ErrorState'
import { useEntityDetailData } from '@/hooks/useEntityDetailData'

export default function IdentityDetailPage() {
  const { id } = useParams({ strict: false })
  const { t } = useTranslation()

  // Validate id exists - show error page if invalid
  if (!id) {
    return (
      <ErrorState
        title={t('errors.invalidUrl')}
        message={t('errors.noIdProvided')}
      />
    )
  }

  const { data, i18n, isPending, isError } = useEntityDetailData('identity', id)

  if (isPending) return <LoadingState />
  if (isError || !data) {
    return (
      <ErrorState
        title={t('errors.notFound')}
        message={t('errors.loadFailed')}
      />
    )
  }

  return (
    <div>
      <h1>{i18n?.name}</h1>
      {/* Page content */}
    </div>
  )
}
```

---

## i18n in Pages

### Using useTranslation

```typescript
import { useTranslation } from 'react-i18next'

export default function CommunityPage() {
  const { t } = useTranslation()

  return (
    <div className="container mx-auto p-8">
      <h1 className="text-3xl font-bold mb-4">
        {t('pages.community.title')}
      </h1>
      <p className="text-muted-foreground mb-6">
        {t('pages.community.description')}
      </p>
    </div>
  )
}
```

### Translation Key Structure

```json
// static/i18n/EN/common.json
{
  "pages": {
    "community": {
      "title": "Community",
      "description": "Join our community",
      "backHome": "Back to Home"
    }
  },
  "errors": {
    "invalidUrl": "Invalid URL",
    "noIdProvided": "No ID provided",
    "notFound": "Not Found",
    "loadFailed": "Failed to load data"
  }
}
```

---

## Route Patterns

### Static Routes

```typescript
const aboutRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/about',
  component: AboutPage,
})
```

### Dynamic Routes

Use `$param` for URL parameters:

```typescript
const identityDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/identity/$id',
  component: IdentityDetailPage,
})
```

### Nested Routes

```typescript
const plannerMDNewRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/planner/md/new',
  component: PlannerMDNewPage,
})
```

---

## Accessing Route Parameters

### useParams Hook

```typescript
import { useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

function IdentityDetailPage() {
  const { id } = useParams({ strict: false })
  const { t } = useTranslation()

  // Show error page if ID is missing
  if (!id) {
    return (
      <ErrorState
        title={t('errors.invalidUrl')}
        message={t('errors.noIdProvided')}
      />
    )
  }

  // Use id...
}
```

---

## Navigation

### Link Component

```typescript
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

function NavLinks() {
  const { t } = useTranslation()

  return (
    <>
      <Link to="/identity" className="text-primary hover:underline">
        {t('nav.identity')}
      </Link>

      {/* With parameters */}
      <Link
        to="/identity/$id"
        params={{ id: '10101' }}
        className="text-primary"
      >
        {t('nav.viewDetail')}
      </Link>
    </>
  )
}
```

### Programmatic Navigation

```typescript
import { useNavigate } from '@tanstack/react-router'

function MyComponent() {
  const navigate = useNavigate()

  const handleClick = () => {
    navigate({ to: '/identity' })
  }

  // With parameters
  const goToDetail = (id: string) => {
    navigate({
      to: '/identity/$id',
      params: { id },
    })
  }
}
```

---

## Adding New Routes

### Checklist

1. Create page component in `routes/`:
   ```typescript
   // routes/NewFeaturePage.tsx
   import { useTranslation } from 'react-i18next'

   export default function NewFeaturePage() {
     const { t } = useTranslation()

     return (
       <div className="container mx-auto p-8">
         <h1>{t('pages.newFeature.title')}</h1>
       </div>
     )
   }
   ```

2. Add translation keys in `static/i18n/{lang}/common.json`

3. Import and define route in `lib/router.tsx`:
   ```typescript
   import NewFeaturePage from '@/routes/NewFeaturePage'

   const newFeatureRoute = createRoute({
     getParentRoute: () => rootRoute,
     path: '/new-feature',
     component: NewFeaturePage,
   })
   ```

4. Add to route tree:
   ```typescript
   const routeTree = rootRoute.addChildren([
     // ... existing routes
     newFeatureRoute,
   ])
   ```

---

## Page Naming Convention

| Page Type | Naming | Example |
|-----------|--------|---------|
| List page | `{Entity}Page.tsx` | `IdentityPage.tsx` |
| Detail page | `{Entity}DetailPage.tsx` | `IdentityDetailPage.tsx` |
| Action page | `{Entity}{Action}Page.tsx` | `PlannerMDNewPage.tsx` |

---

## Summary

| Pattern | Implementation |
|---------|---------------|
| Routing style | Code-based (not file-based) |
| Route definition | `createRoute()` in `lib/router.tsx` |
| Page components | Regular components in `routes/` |
| Parameters | `$param` in path, `useParams()` to access |
| Navigation | `<Link>` component or `useNavigate()` |
| i18n | `useTranslation()` + `t()` from react-i18next |
| Invalid params | Show `<ErrorState>` with translated messages |

**See Also:**
- [component-patterns.md](component-patterns.md) - Component structure
- [data-fetching.md](data-fetching.md) - Loading data in pages
