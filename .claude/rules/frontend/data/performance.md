---
paths:
  - "frontend/src/hooks/**/*.ts"
  - "frontend/src/components/**/*.tsx"
---

# Performance Patterns

## React Compiler

**This project uses React Compiler** - automatic memoization for components, callbacks, and computed values.

**Manual optimization ONLY with:**
1. Explicit performance intent
2. User approval
3. Profiling evidence showing benefit

## When Manual Optimization IS Needed

### Debounced Search (Required)

```typescript
import { useState } from 'react'
import { useDebounce } from 'use-debounce'

function SearchComponent() {
  const [searchTerm, setSearchTerm] = useState('')
  const [debouncedSearch] = useDebounce(searchTerm, 300)

  const { data } = useQuery({
    queryKey: ['search', debouncedSearch],
    queryFn: () => searchApi(debouncedSearch),
  })

  return <Input value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} />
}
```

**Debounce timing:**
- 300ms: Search/filter
- 1000ms: Auto-save
- 100-200ms: Real-time validation

### Large List Filtering (If Needed)

```typescript
import { useMemo } from 'react'

const filteredItems = useMemo(() => {
  return items
    .filter(item => item.name.includes(searchTerm))
    .sort((a, b) => a.name.localeCompare(b.name))
}, [items, searchTerm])
```

## Memory Leak Prevention

### Cleanup Timeouts/Intervals

```typescript
useEffect(() => {
  const intervalId = setInterval(() => setCount(c => c + 1), 1000)
  return () => clearInterval(intervalId)
}, [])

useEffect(() => {
  const timeoutId = setTimeout(() => console.log('Done'), 5000)
  return () => clearTimeout(timeoutId)
}, [])
```

### Cleanup Event Listeners

```typescript
useEffect(() => {
  const handleResize = () => console.log('Resized')
  window.addEventListener('resize', handleResize)
  return () => window.removeEventListener('resize', handleResize)
}, [])
```

## List Rendering

```typescript
// GOOD: Stable unique key
{items.map(item => <ItemCard key={item.id} item={item} />)}

// BAD: Index as key (unstable if list reorders)
{items.map((item, index) => <ItemCard key={index} item={item} />)}
```

## Lazy Loading

### Heavy Components
```typescript
import { lazy, Suspense } from 'react'

const HeavyChart = lazy(() => import('./HeavyChart'))

<Suspense fallback={<LoadingState />}>
  <HeavyChart />
</Suspense>
```

### Heavy Libraries
```typescript
const handleExportPDF = async () => {
  const { jsPDF } = await import('jspdf')
  const doc = new jsPDF()
  // Use it
}
```

## Image Optimization

```typescript
<img
  src={getIdentityInfoImagePath(identity.id)}
  alt={identity.name}
  loading="lazy"  // Lazy load below-the-fold images
/>
```
