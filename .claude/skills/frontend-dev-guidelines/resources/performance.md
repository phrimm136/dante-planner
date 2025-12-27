# Performance Optimization

Performance patterns for React applications using React Compiler (automatic optimization), code splitting, and best practices.

---

## React Compiler (Automatic Optimization)

This project uses **React Compiler** which automatically handles memoization. Manual `memo`, `useMemo`, and `useCallback` are largely unnecessary.

### What React Compiler Does

- **Automatic memoization**: Components, values, and callbacks optimized at build time
- **Preserves semantics**: Code works the same, just faster
- **30-60% reduction** in unnecessary re-renders for most apps

### Verification

Open React DevTools and look for "Memo ✨" badge next to component names.

### When Manual Optimization Is Still Needed

React Compiler handles most cases, but consider manual optimization for:

1. **Extremely expensive computations** (complex algorithms, large data processing)
2. **Components using refs with external dependencies**
3. **Direct DOM manipulation**
4. **Code that violates Rules of React** (impure renders, mutating props/state)

### Opt Out (if needed)

```typescript
function ProblematicComponent() {
  "use no memo"
  // Component code that shouldn't be optimized
}
```

---

## Code Splitting with Lazy Loading

### Route-Level Splitting

```typescript
import { lazy, Suspense } from 'react'
import { createFileRoute } from '@tanstack/react-router'
import { LoadingState } from '@/components/common/LoadingState'

const UserProfile = lazy(() =>
  import('@/features/users/components/UserProfile').then((module) => ({
    default: module.UserProfile,
  }))
)

export const Route = createFileRoute('/users/$userId')({
  component: UserProfilePage,
})

function UserProfilePage() {
  const { userId } = Route.useParams()

  return (
    <Suspense fallback={<LoadingState />}>
      <UserProfile userId={userId} />
    </Suspense>
  )
}
```

### Component-Level Splitting

```typescript
import { lazy, Suspense } from 'react'
import { LoadingState } from '@/components/common/LoadingState'

// Lazy load heavy components
const HeavyChart = lazy(() => import('./HeavyChart'))
const DataGrid = lazy(() => import('./DataGrid'))

function Dashboard() {
  return (
    <div className="space-y-4">
      <Header />

      <Suspense fallback={<LoadingState />}>
        <HeavyChart />
      </Suspense>

      <Suspense fallback={<LoadingState />}>
        <DataGrid />
      </Suspense>
    </div>
  )
}
```

### When to Lazy Load

| Lazy Load | Don't Lazy Load |
|-----------|-----------------|
| Route components | Small, frequently used components |
| Heavy charts/visualizations | UI primitives (Button, Card) |
| Modal/dialog content | Components in initial viewport |
| Below-the-fold content | Critical path components |
| Large form wizards | Simple presentational components |

---

## Dynamic Import for Heavy Libraries

```typescript
// ❌ AVOID - Loaded immediately, increases bundle size
import jsPDF from 'jspdf'
import * as XLSX from 'xlsx'

// ✅ CORRECT - Load when needed
const handleExportPDF = async () => {
  const { jsPDF } = await import('jspdf')
  const doc = new jsPDF()
  // Use it
}

const handleExportExcel = async () => {
  const XLSX = await import('xlsx')
  const workbook = XLSX.utils.book_new()
  // Use it
}
```

---

## TanStack Query Optimization

### Parallel Queries with useSuspenseQueries

```typescript
import { useSuspenseQueries } from '@tanstack/react-query'

function Dashboard() {
  // All queries run in parallel, not sequentially
  const [statsQuery, usersQuery, activityQuery] = useSuspenseQueries({
    queries: [
      { queryKey: ['stats'], queryFn: fetchStats },
      { queryKey: ['users'], queryFn: fetchUsers },
      { queryKey: ['activity'], queryFn: fetchActivity },
    ],
  })

  return (
    <div>
      <Stats data={statsQuery.data} />
      <UserList users={usersQuery.data} />
      <Activity items={activityQuery.data} />
    </div>
  )
}
```

### Prevent Suspense Fallback on Key Changes

Use `useDeferredValue` to prevent UI flash when query keys change:

```typescript
import { useState, useDeferredValue } from 'react'
import { useSuspenseQuery } from '@tanstack/react-query'

function SearchResults() {
  const [searchTerm, setSearchTerm] = useState('')
  const deferredSearch = useDeferredValue(searchTerm)

  const { data } = useSuspenseQuery({
    queryKey: ['search', deferredSearch],
    queryFn: () => searchApi(deferredSearch),
  })

  const isStale = searchTerm !== deferredSearch

  return (
    <div>
      <input
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
      />
      <div className={isStale ? 'opacity-70' : ''}>
        {data.map(item => <Item key={item.id} item={item} />)}
      </div>
    </div>
  )
}
```

### Appropriate staleTime

```typescript
// Static data - long staleTime
const { data } = useSuspenseQuery({
  queryKey: ['config'],
  queryFn: fetchConfig,
  staleTime: Infinity, // Never refetch
})

// Game data - moderate staleTime
const { data } = useSuspenseQuery({
  queryKey: ['identity', id],
  queryFn: () => fetchIdentity(id),
  staleTime: 5 * 60 * 1000, // 5 minutes
})

// i18n data - long staleTime
const { data } = useSuspenseQuery({
  queryKey: ['i18n', id, language],
  queryFn: () => fetchI18n(id, language),
  staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
})
```

---

## List Rendering

### Stable Keys

```typescript
// ✅ CORRECT - Stable unique keys
{items.map(item => (
  <ListItem key={item.id} item={item} />
))}

// ❌ AVOID - Index as key (unstable if list changes)
{items.map((item, index) => (
  <ListItem key={index} item={item} />
))}
```

### Virtualization for Large Lists

For lists with 100+ items, consider virtualization:

```typescript
import { useVirtualizer } from '@tanstack/react-virtual'

function VirtualList({ items }: { items: Item[] }) {
  const parentRef = useRef<HTMLDivElement>(null)

  const virtualizer = useVirtualizer({
    count: items.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 50,
  })

  return (
    <div ref={parentRef} className="h-[400px] overflow-auto">
      <div
        style={{ height: `${virtualizer.getTotalSize()}px`, position: 'relative' }}
      >
        {virtualizer.getVirtualItems().map((virtualItem) => (
          <div
            key={virtualItem.key}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: '100%',
              transform: `translateY(${virtualItem.start}px)`,
            }}
          >
            <ItemRow item={items[virtualItem.index]} />
          </div>
        ))}
      </div>
    </div>
  )
}
```

---

## Memory Leak Prevention

### Cleanup in useEffect

```typescript
import { useEffect, useState } from 'react'

function MyComponent() {
  const [count, setCount] = useState(0)

  useEffect(() => {
    // Cleanup interval
    const intervalId = setInterval(() => {
      setCount((c) => c + 1)
    }, 1000)

    return () => clearInterval(intervalId)
  }, [])

  useEffect(() => {
    // Cleanup timeout
    const timeoutId = setTimeout(() => {
      console.log('Delayed action')
    }, 5000)

    return () => clearTimeout(timeoutId)
  }, [])

  useEffect(() => {
    // Cleanup event listener
    const handleResize = () => console.log('Resized')
    window.addEventListener('resize', handleResize)

    return () => window.removeEventListener('resize', handleResize)
  }, [])

  return <div>{count}</div>
}
```

**Note**: TanStack Query handles fetch cleanup automatically.

---

## Form Performance

### Watch Specific Fields

```typescript
import { useForm } from 'react-hook-form'

function MyForm() {
  const { register, watch } = useForm()

  // ❌ AVOID - Watches all fields, re-renders on any change
  const formValues = watch()

  // ✅ CORRECT - Watch only what you need
  const email = watch('email')

  // ✅ Or multiple specific fields
  const [email, username] = watch(['email', 'username'])

  return (
    <form>
      <input {...register('email')} />
      <input {...register('username')} />
      <input {...register('password')} />
    </form>
  )
}
```

---

## Image Optimization

### Lazy Loading Images

```typescript
// Native lazy loading
<img src={imagePath} alt="Description" loading="lazy" />

// With dimensions to prevent layout shift
<img
  src={imagePath}
  alt="Description"
  loading="lazy"
  width={200}
  height={150}
  className="object-cover"
/>
```

### Responsive Images

```typescript
<picture>
  <source media="(min-width: 1024px)" srcSet={largeImage} />
  <source media="(min-width: 640px)" srcSet={mediumImage} />
  <img src={smallImage} alt="Description" loading="lazy" />
</picture>
```

---

## What React Compiler Handles (No Manual Work Needed)

| Previously Manual | Now Automatic |
|------------------|---------------|
| `React.memo(Component)` | Automatic component memoization |
| `useMemo(() => value, [deps])` | Automatic value memoization |
| `useCallback(() => fn, [deps])` | Automatic callback stability |
| Prop reference equality | Automatic prop optimization |

### Write Simple Code

```typescript
// ✅ Just write normal code - React Compiler optimizes it
function UserCard({ user, onSelect }: UserCardProps) {
  const fullName = `${user.firstName} ${user.lastName}`

  const handleClick = () => onSelect(user)

  return (
    <Card onClick={handleClick}>
      <p>{fullName}</p>
    </Card>
  )
}
```

---

## Summary

| Aspect | Approach |
|--------|----------|
| Memoization | React Compiler (automatic) |
| Code splitting | `React.lazy()` + Suspense |
| Heavy libraries | Dynamic `import()` |
| Query optimization | useSuspenseQueries, staleTime |
| Large lists | Virtualization |
| Search/filter | `useDeferredValue` |
| Cleanup | useEffect return function |

**See Also:**
- [component-patterns.md](component-patterns.md) - Lazy loading patterns
- [data-fetching.md](data-fetching.md) - TanStack Query optimization
