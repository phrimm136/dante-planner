# Performance Optimization

Performance patterns for LimbusPlanner. This project uses React Compiler for automatic memoization.

---

## React Compiler (Automatic Optimization)

This project uses **React Compiler**, which automatically handles:
- Component memoization (replaces `React.memo`)
- Callback memoization (replaces `useCallback`)
- Computed value memoization (replaces `useMemo`)

**You don't need manual memoization in most cases.**

See [component-patterns.md](component-patterns.md) for React Compiler setup.

---

## When Manual Optimization is Still Needed

### Debounced Search

Debouncing is not automatic - use it for search/filter inputs:

```typescript
import { useState } from 'react'
import { useDebounce } from 'use-debounce'
import { useQuery } from '@tanstack/react-query'

function SearchComponent() {
  const [searchTerm, setSearchTerm] = useState('')
  const [debouncedSearch] = useDebounce(searchTerm, 300)

  const { data } = useQuery({
    queryKey: ['search', debouncedSearch],
    queryFn: () => searchApi(debouncedSearch),
    enabled: debouncedSearch.length > 0,
  })

  return (
    <input
      value={searchTerm}
      onChange={(e) => setSearchTerm(e.target.value)}
      placeholder="Search..."
    />
  )
}
```

**Debounce timing:**
- **300-500ms**: Search/filtering
- **1000ms**: Auto-save
- **100-200ms**: Real-time validation

### Filtering Large Lists

For expensive filter/sort operations on large datasets:

```typescript
import { useMemo } from 'react'

function ItemList({ items, searchTerm }: Props) {
  // For very large lists, consider manual useMemo
  const filteredItems = useMemo(() => {
    return items
      .filter(item => item.name.toLowerCase().includes(searchTerm.toLowerCase()))
      .sort((a, b) => a.name.localeCompare(b.name))
  }, [items, searchTerm])

  return (
    <div className="grid grid-cols-4 gap-4">
      {filteredItems.map(item => (
        <ItemCard key={item.id} item={item} />
      ))}
    </div>
  )
}
```

---

## Memory Leak Prevention

### Cleanup Timeouts/Intervals

```typescript
import { useEffect, useState } from 'react'

function TimerComponent() {
  const [count, setCount] = useState(0)

  useEffect(() => {
    const intervalId = setInterval(() => {
      setCount(c => c + 1)
    }, 1000)

    // Cleanup on unmount
    return () => clearInterval(intervalId)
  }, [])

  useEffect(() => {
    const timeoutId = setTimeout(() => {
      console.log('Delayed action')
    }, 5000)

    // Cleanup on unmount
    return () => clearTimeout(timeoutId)
  }, [])

  return <div>{count}</div>
}
```

### Cleanup Event Listeners

```typescript
useEffect(() => {
  const handleResize = () => {
    console.log('Resized')
  }

  window.addEventListener('resize', handleResize)

  return () => {
    window.removeEventListener('resize', handleResize)
  }
}, [])
```

---

## List Rendering

### Key Prop

Always use stable unique keys:

```typescript
// Good - stable unique ID
{items.map(item => (
  <ItemCard key={item.id} item={item} />
))}

// Bad - index as key (unstable if list reorders)
{items.map((item, index) => (
  <ItemCard key={index} item={item} />  // Wrong if list can reorder
))}
```

---

## Lazy Loading

### Heavy Components

```typescript
import { lazy, Suspense } from 'react'
import { LoadingState } from '@/components/common/LoadingState'

const HeavyChart = lazy(() => import('./HeavyChart'))

function Dashboard() {
  return (
    <div>
      <Header />
      <Suspense fallback={<LoadingState />}>
        <HeavyChart />
      </Suspense>
    </div>
  )
}
```

### Heavy Libraries (Dynamic Import)

```typescript
// Don't import large libraries at top level
// Import dynamically when needed

const handleExportPDF = async () => {
  const { jsPDF } = await import('jspdf')
  const doc = new jsPDF()
  // Use it
}

const handleExportExcel = async () => {
  const XLSX = await import('xlsx')
  // Use it
}
```

---

## Image Optimization

### Use Appropriate Paths

```typescript
import { getIdentityImagePath } from '@/lib/assetPaths'

// Use helper functions for consistent image paths
<img
  src={getIdentityImagePath(identity.id)}
  alt={identity.name}
  loading="lazy"  // Native lazy loading
/>
```

### Loading Attribute

```typescript
// Lazy load images below the fold
<img src={imageSrc} alt={alt} loading="lazy" />

// Eager load above-the-fold images
<img src={heroImage} alt="Hero" loading="eager" />
```

---

## Query Optimization

### Appropriate staleTime

```typescript
// Data that changes rarely - longer staleTime
staleTime: 7 * 24 * 60 * 60 * 1000  // 7 days for i18n

// Data that changes occasionally
staleTime: 5 * 60 * 1000  // 5 minutes for game data

// Data that changes frequently
staleTime: 30 * 1000  // 30 seconds
```

### Parallel Queries

Use `useSuspenseQueries` for parallel data loading:

```typescript
const [dataQuery, i18nQuery] = useSuspenseQueries({
  queries: [
    { queryKey: ['entity', id], queryFn: fetchData },
    { queryKey: ['entity', id, 'i18n'], queryFn: fetchI18n },
  ],
})
```

See [data-fetching.md](data-fetching.md) for details.

---

## Component Anti-Patterns

### Avoid Defining Components Inside Components

```typescript
// Bad - component recreated every render
function Parent() {
  const ChildComponent = () => <div>Child</div>
  return <ChildComponent />
}

// Good - component defined outside
const ChildComponent = () => <div>Child</div>

function Parent() {
  return <ChildComponent />
}
```

### Avoid Prop Drilling

Use context or composition instead of passing props through many levels.

---

## Summary

| Optimization | Pattern |
|--------------|---------|
| Memoization | React Compiler (automatic) |
| Search/filter | `useDebounce` |
| Large list filtering | Manual `useMemo` if needed |
| Lazy loading | `React.lazy` + Suspense |
| Heavy libraries | Dynamic import |
| Lists | Stable unique keys |
| Cleanup | Return cleanup function in useEffect |

**See Also:**
- [component-patterns.md](component-patterns.md) - React Compiler setup
- [data-fetching.md](data-fetching.md) - Query optimization
