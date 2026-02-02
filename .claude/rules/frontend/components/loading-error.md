---
paths:
  - "frontend/src/routes/**/*.tsx"
  - "frontend/src/components/**/*.tsx"
---

# Loading & Error State Patterns

## Complete Pattern

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

  // 1. Validate params
  if (!id) {
    return <ErrorState title={t('errors.invalidUrl')} message={t('errors.noIdProvided')} />
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
```

## Multiple Suspense Boundaries (Progressive Rendering)

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
- Each section loads independently (progressive rendering)
- User sees partial content immediately
- Better perceived performance
- Failed sections don't block others

## Error Boundary with Retry

```typescript
import { ErrorBoundary } from 'react-error-boundary'
import { Button } from '@/components/ui/button'

function ErrorFallback({ error, resetErrorBoundary }) {
  const { t } = useTranslation()

  return (
    <div className="p-8">
      <ErrorState title={t('errors.renderError')} message={error.message} />
      <Button onClick={resetErrorBoundary} className="mt-4">
        {t('common.tryAgain')}
      </Button>
    </div>
  )
}

<ErrorBoundary FallbackComponent={ErrorFallback}>
  <MyContent />
</ErrorBoundary>
```
