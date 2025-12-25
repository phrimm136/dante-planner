# Loading & Error States

Loading and error handling patterns for LimbusPlanner.

---

## Loading States

### LoadingState Component

Use `LoadingState` for full-page or section loading:

```typescript
import { LoadingState } from '@/components/common/LoadingState'

function MyPage() {
  const { data, isPending } = useQuery(...)

  if (isPending) {
    return <LoadingState message="Loading data..." />
  }

  return <Content data={data} />
}
```

### With Suspense (Recommended for New Code)

For `useSuspenseQuery`, wrap in Suspense boundary:

```typescript
import { Suspense } from 'react'
import { LoadingState } from '@/components/common/LoadingState'

function MyPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <MyContent />
    </Suspense>
  )
}

function MyContent() {
  // Data is always defined with useSuspenseQuery
  const { data } = useSuspenseQuery(...)
  return <Display data={data} />
}
```

### Multiple Suspense Boundaries

For independent sections:

```typescript
function Dashboard() {
  return (
    <div className="space-y-4">
      <Suspense fallback={<LoadingState />}>
        <Header />
      </Suspense>

      <Suspense fallback={<LoadingState />}>
        <MainContent />
      </Suspense>

      <Suspense fallback={<LoadingState />}>
        <Sidebar />
      </Suspense>
    </div>
  )
}
```

**Benefits:**
- Each section loads independently
- User sees partial content sooner
- Better perceived performance

---

## Error States

### ErrorState Component

Use `ErrorState` for full-page error display:

```typescript
import { ErrorState } from '@/components/common/ErrorState'
import { useTranslation } from 'react-i18next'

function MyPage() {
  const { t } = useTranslation()
  const { data, isError } = useQuery(...)

  if (isError) {
    return (
      <ErrorState
        title={t('errors.loadFailed')}
        message={t('errors.tryAgain')}
      />
    )
  }

  return <Content data={data} />
}
```

### Parameter Validation

Validate route parameters before fetching:

```typescript
import { useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ErrorState } from '@/components/common/ErrorState'

function DetailPage() {
  const { id } = useParams({ strict: false })
  const { t } = useTranslation()

  // Validate id before making queries
  if (!id) {
    return (
      <ErrorState
        title={t('errors.invalidUrl')}
        message={t('errors.noIdProvided')}
      />
    )
  }

  // Now safe to fetch data
  const { data, isPending, isError } = useEntityDetailData('identity', id)

  // ...
}
```

---

## Toast Notifications (sonner)

Use `sonner` for transient notifications:

```typescript
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'

function MyComponent() {
  const { t } = useTranslation()

  const handleSave = async () => {
    try {
      await saveData()
      toast.success(t('common.saveSuccess'))
    } catch {
      toast.error(t('common.saveError'))
    }
  }
}
```

### Toast Methods

```typescript
// Success
toast.success('Operation completed')

// Error
toast.error('Something went wrong')

// Info with description
toast.info('Processing...', {
  description: 'Please wait',
})

// Warning
toast.warning('Are you sure?')

// Promise-based
toast.promise(saveData(), {
  loading: 'Saving...',
  success: 'Saved!',
  error: 'Failed to save',
})
```

---

## Error Boundaries

For catching render errors:

```typescript
import { ErrorBoundary } from 'react-error-boundary'
import { ErrorState } from '@/components/common/ErrorState'

function ErrorFallback({ error, resetErrorBoundary }) {
  const { t } = useTranslation()

  return (
    <div className="p-8">
      <ErrorState
        title={t('errors.renderError')}
        message={error.message}
      />
      <Button onClick={resetErrorBoundary} className="mt-4">
        {t('common.tryAgain')}
      </Button>
    </div>
  )
}

function MyPage() {
  return (
    <ErrorBoundary
      FallbackComponent={ErrorFallback}
      onError={(error) => console.error('Error:', error)}
    >
      <Suspense fallback={<LoadingState />}>
        <MyContent />
      </Suspense>
    </ErrorBoundary>
  )
}
```

---

## TanStack Query Error Handling

### In Mutations

```typescript
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { useTranslation } from 'react-i18next'

function useUpdateEntity() {
  const queryClient = useQueryClient()
  const { t } = useTranslation()

  return useMutation({
    mutationFn: updateEntity,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['entity'] })
      toast.success(t('entity.updateSuccess'))
    },
    onError: (error) => {
      console.error('Update failed:', error)
      toast.error(t('entity.updateError'))
    },
  })
}
```

---

## Patterns Summary

### When to Use What

| Scenario | Component/Pattern |
|----------|------------------|
| Page loading | `<LoadingState />` or Suspense |
| Data not found | `<ErrorState />` |
| Invalid URL params | `<ErrorState />` |
| Action success/failure | `toast.success/error()` |
| Render errors | `<ErrorBoundary />` |

### Complete Pattern

```typescript
import { Suspense } from 'react'
import { useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ErrorBoundary } from 'react-error-boundary'
import { LoadingState } from '@/components/common/LoadingState'
import { ErrorState } from '@/components/common/ErrorState'

export default function DetailPage() {
  const { id } = useParams({ strict: false })
  const { t } = useTranslation()

  // 1. Validate params first
  if (!id) {
    return (
      <ErrorState
        title={t('errors.invalidUrl')}
        message={t('errors.noIdProvided')}
      />
    )
  }

  // 2. Wrap in error boundary + suspense
  return (
    <ErrorBoundary fallback={<ErrorState title={t('errors.renderError')} message="" />}>
      <Suspense fallback={<LoadingState />}>
        <DetailContent id={id} />
      </Suspense>
    </ErrorBoundary>
  )
}

function DetailContent({ id }: { id: string }) {
  const { t } = useTranslation()
  const { data, i18n, isPending, isError } = useEntityDetailData('entity', id)

  // 3. Handle loading (for useQuery pattern)
  if (isPending) return <LoadingState />

  // 4. Handle errors
  if (isError || !data) {
    return (
      <ErrorState
        title={t('errors.notFound')}
        message={t('errors.loadFailed')}
      />
    )
  }

  // 5. Render content
  return <Display data={data} i18n={i18n} />
}
```

---

## Summary

| Pattern | Tool |
|---------|------|
| Full-page loading | `<LoadingState />` |
| Suspense loading | `<Suspense fallback={<LoadingState />}>` |
| Page-level errors | `<ErrorState />` |
| Toast notifications | `toast.success/error()` (sonner) |
| Render errors | `<ErrorBoundary />` |

**See Also:**
- [data-fetching.md](data-fetching.md) - Query patterns
- [component-patterns.md](component-patterns.md) - Suspense usage
- [routing-guide.md](routing-guide.md) - Parameter validation
