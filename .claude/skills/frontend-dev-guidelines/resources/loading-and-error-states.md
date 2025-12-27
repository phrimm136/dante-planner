# Loading & Error States

Proper loading and error state handling prevents layout shift and provides better user experience. Uses Suspense boundaries with TanStack Query, shadcn/ui components, and sonner for toast notifications.

---

## Suspense-First Approach (PREFERRED)

### The Pattern

With `useSuspenseQuery`, data is guaranteed to be defined - no loading checks needed:

```typescript
import { Suspense } from 'react'
import { useSuspenseQuery } from '@tanstack/react-query'
import { LoadingState } from '@/components/common/LoadingState'

// Inner component - data is always defined
function UserContent({ userId }: { userId: string }) {
  const { data } = useSuspenseQuery({
    queryKey: ['user', userId],
    queryFn: () => fetchUser(userId),
  })

  // No undefined check needed!
  return (
    <div className="p-4">
      <h1 className="text-xl font-bold">{data.name}</h1>
      <p className="text-muted-foreground">{data.email}</p>
    </div>
  )
}

// Outer component - provides Suspense boundary
export function UserProfile({ userId }: { userId: string }) {
  return (
    <Suspense fallback={<LoadingState />}>
      <UserContent userId={userId} />
    </Suspense>
  )
}
```

### Why Suspense?

| Benefit | Description |
|---------|-------------|
| Simpler code | No `isLoading` checks or conditional rendering |
| Guaranteed data | Data is always defined inside the component |
| Declarative loading | Parent controls loading UI, child focuses on content |
| Better composition | Easy to combine multiple async components |

---

## LoadingState Component

### Basic Usage

```typescript
import { LoadingState } from '@/components/common/LoadingState'

// Full page loading
<LoadingState />

// Inline loading
<LoadingState className="h-32" />
```

### Implementation

```typescript
import { Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

interface LoadingStateProps {
  className?: string
  message?: string
}

export function LoadingState({ className, message }: LoadingStateProps) {
  return (
    <div className={cn(
      'flex flex-col items-center justify-center p-8',
      className
    )}>
      <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      {message && (
        <p className="mt-2 text-sm text-muted-foreground">{message}</p>
      )}
    </div>
  )
}
```

---

## Multiple Suspense Boundaries

### Independent Loading Sections

```typescript
export function Dashboard() {
  return (
    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
      <Suspense fallback={<LoadingState className="h-48" />}>
        <StatsCard />
      </Suspense>

      <Suspense fallback={<LoadingState className="h-48" />}>
        <RecentActivity />
      </Suspense>

      <Suspense fallback={<LoadingState className="h-48" />}>
        <QuickActions />
      </Suspense>
    </div>
  )
}
```

**Benefits:**
- Each section loads independently
- User sees partial content sooner
- Better perceived performance

### Nested Suspense

```typescript
export function ParentComponent() {
  return (
    <Suspense fallback={<LoadingState />}>
      <ParentContent>
        <Suspense fallback={<LoadingState className="h-32" />}>
          <ChildComponent />
        </Suspense>
      </ParentContent>
    </Suspense>
  )
}
```

---

## Error Handling with sonner

### Basic Toast Usage

```typescript
import { toast } from 'sonner'

// Success notification
toast.success('Profile updated successfully')

// Error notification
toast.error('Failed to save changes')

// Info notification
toast.info('Processing your request...')

// Warning notification
toast.warning('This action cannot be undone')
```

### With TanStack Query Mutations

```typescript
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

export function useUpdateUser() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: UpdateUserData) => updateUser(data),

    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user'] })
      toast.success('User updated successfully')
    },

    onError: (error) => {
      console.error('Update failed:', error)
      toast.error('Failed to update user')
    },
  })
}
```

### Custom Toast Options

```typescript
// With description
toast.success('Changes saved', {
  description: 'Your profile has been updated',
})

// With action
toast.error('Failed to save', {
  action: {
    label: 'Retry',
    onClick: () => handleRetry(),
  },
})

// With duration
toast.info('Processing...', {
  duration: 5000, // 5 seconds
})

// Promise toast
toast.promise(saveData(), {
  loading: 'Saving...',
  success: 'Data saved!',
  error: 'Failed to save',
})
```

---

## Error Boundaries

### Basic Error Boundary

```typescript
import { ErrorBoundary } from 'react-error-boundary'
import { Button } from '@/components/ui/button'
import { AlertCircle } from 'lucide-react'

interface ErrorFallbackProps {
  error: Error
  resetErrorBoundary: () => void
}

function ErrorFallback({ error, resetErrorBoundary }: ErrorFallbackProps) {
  return (
    <div className="flex flex-col items-center justify-center p-8 text-center">
      <AlertCircle className="h-12 w-12 text-destructive mb-4" />
      <h2 className="text-lg font-semibold">Something went wrong</h2>
      <p className="text-sm text-muted-foreground mt-2 max-w-md">
        {error.message}
      </p>
      <Button onClick={resetErrorBoundary} className="mt-4">
        Try Again
      </Button>
    </div>
  )
}

export function MyPage() {
  return (
    <ErrorBoundary
      FallbackComponent={ErrorFallback}
      onError={(error) => console.error('Boundary caught:', error)}
    >
      <Suspense fallback={<LoadingState />}>
        <PageContent />
      </Suspense>
    </ErrorBoundary>
  )
}
```

### Error Boundary + Suspense Pattern

```typescript
import { ErrorBoundary } from 'react-error-boundary'
import { QueryErrorResetBoundary } from '@tanstack/react-query'

export function QueryBoundary({ children }: { children: React.ReactNode }) {
  return (
    <QueryErrorResetBoundary>
      {({ reset }) => (
        <ErrorBoundary onReset={reset} FallbackComponent={ErrorFallback}>
          <Suspense fallback={<LoadingState />}>
            {children}
          </Suspense>
        </ErrorBoundary>
      )}
    </QueryErrorResetBoundary>
  )
}

// Usage
<QueryBoundary>
  <ComponentWithSuspenseQuery />
</QueryBoundary>
```

---

## Skeleton Loading

### shadcn/ui Skeleton

```typescript
import { Skeleton } from '@/components/ui/skeleton'

function CardSkeleton() {
  return (
    <div className="space-y-3">
      <Skeleton className="h-4 w-3/4" />
      <Skeleton className="h-4 w-1/2" />
      <Skeleton className="h-32 w-full" />
    </div>
  )
}
```

### Custom Skeleton Pattern

```typescript
function UserCardSkeleton() {
  return (
    <div className="flex items-center gap-4 p-4 border rounded-lg">
      <Skeleton className="h-12 w-12 rounded-full" />
      <div className="space-y-2">
        <Skeleton className="h-4 w-32" />
        <Skeleton className="h-3 w-24" />
      </div>
    </div>
  )
}

// Use as Suspense fallback
<Suspense fallback={<UserCardSkeleton />}>
  <UserCard userId={userId} />
</Suspense>
```

---

## Loading State Anti-Patterns

### What NOT to Do

```typescript
// ❌ NEVER - Early return with loading
function BadComponent() {
  const { data, isLoading } = useQuery({ ... })

  if (isLoading) {
    return <LoadingState />  // Layout shift when loading completes
  }

  return <Content data={data} />
}

// ❌ NEVER - Ternary with different layouts
{isLoading ? (
  <div className="h-24"><Spinner /></div>
) : (
  <div className="h-96"><Content /></div>  // Different height!
)}
```

### What TO Do

```typescript
// ✅ BEST - Suspense with useSuspenseQuery
<Suspense fallback={<LoadingState />}>
  <ComponentWithSuspenseQuery />
</Suspense>

// ✅ GOOD - Skeleton with same layout
<div className="h-96">
  {isLoading ? (
    <Skeleton className="h-full w-full" />
  ) : (
    <Content data={data} />
  )}
</div>

// ✅ OK - Overlay pattern (preserves layout)
<div className="relative">
  <Content data={data} />
  {isLoading && (
    <div className="absolute inset-0 bg-background/50 flex items-center justify-center">
      <Loader2 className="h-6 w-6 animate-spin" />
    </div>
  )}
</div>
```

---

## Complete Example

```typescript
import { Suspense } from 'react'
import { useSuspenseQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ErrorBoundary } from 'react-error-boundary'
import { toast } from 'sonner'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { LoadingState } from '@/components/common/LoadingState'
import { ErrorFallback } from '@/components/common/ErrorFallback'

interface UserProfileContentProps {
  userId: string
}

function UserProfileContent({ userId }: UserProfileContentProps) {
  const queryClient = useQueryClient()

  const { data: user } = useSuspenseQuery({
    queryKey: ['user', userId],
    queryFn: () => fetchUser(userId),
    staleTime: 5 * 60 * 1000,
  })

  const updateMutation = useMutation({
    mutationFn: (updates: Partial<User>) => updateUser(userId, updates),

    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['user', userId] })
      toast.success('Profile updated')
    },

    onError: () => {
      toast.error('Failed to update profile')
    },
  })

  const handleUpdate = () => {
    updateMutation.mutate({ name: 'New Name' })
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{user.name}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-muted-foreground">{user.email}</p>
        <Button
          onClick={handleUpdate}
          disabled={updateMutation.isPending}
        >
          {updateMutation.isPending ? 'Updating...' : 'Update Profile'}
        </Button>
      </CardContent>
    </Card>
  )
}

export function UserProfile({ userId }: { userId: string }) {
  return (
    <ErrorBoundary FallbackComponent={ErrorFallback}>
      <Suspense fallback={<LoadingState />}>
        <UserProfileContent userId={userId} />
      </Suspense>
    </ErrorBoundary>
  )
}
```

---

## Summary

| Aspect | Approach |
|--------|----------|
| Loading UI | Suspense + `useSuspenseQuery` |
| Loading component | `LoadingState` or `Skeleton` |
| Toast notifications | sonner (`toast.success()`, `toast.error()`) |
| Error boundaries | `react-error-boundary` |
| Anti-pattern | Early returns, conditional layout changes |

**See Also:**
- [component-patterns.md](component-patterns.md) - Suspense integration
- [data-fetching.md](data-fetching.md) - useSuspenseQuery details
- [complete-examples.md](complete-examples.md) - Full examples
