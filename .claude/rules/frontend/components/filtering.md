---
paths:
  - "frontend/src/components/**/*.tsx"
  - "frontend/src/routes/**/*.tsx"
---

# Filtering & Search Patterns

## CSS Hidden (Performance)

**Use CSS `hidden` instead of conditional rendering for filtered lists** - prevents re-mounting components.

```typescript
// GOOD: CSS hidden (no re-mount, better performance)
{items.map(item => (
  <ItemCard
    key={item.id}
    item={item}
    className={cn(!matchesFilter(item, filter) && 'hidden')}
  />
))}

// BAD: Conditional rendering (re-mounts on filter change)
{items
  .filter(item => matchesFilter(item, filter))
  .map(item => <ItemCard key={item.id} item={item} />)}
```

## Search with Debounce

```typescript
import { useState } from 'react'
import { useDebounce } from 'use-debounce'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'

function FilterableList({ items }: { items: Item[] }) {
  const [search, setSearch] = useState('')
  const [debouncedSearch] = useDebounce(search, 300)

  const matchesSearch = (item: Item) => {
    if (!debouncedSearch) return true
    return item.name.toLowerCase().includes(debouncedSearch.toLowerCase())
  }

  return (
    <div className="space-y-4">
      <Input
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Search..."
      />

      <div className="grid grid-cols-4 gap-4">
        {items.map(item => (
          <ItemCard
            key={item.id}
            item={item}
            className={cn(!matchesSearch(item) && 'hidden')}
          />
        ))}
      </div>
    </div>
  )
}
```

## Multi-Filter Pattern

```typescript
interface Filters {
  search: string
  category: string | null
  rarity: number | null
}

function matchesFilters(item: Item, filters: Filters): boolean {
  if (filters.search && !item.name.toLowerCase().includes(filters.search.toLowerCase())) {
    return false
  }
  if (filters.category && item.category !== filters.category) {
    return false
  }
  if (filters.rarity && item.rarity !== filters.rarity) {
    return false
  }
  return true
}

// Usage
<ItemCard
  key={item.id}
  item={item}
  className={cn(!matchesFilters(item, filters) && 'hidden')}
/>
```

## Why CSS Hidden?

| Approach | Mounts | Performance | Animation |
|----------|--------|-------------|-----------|
| CSS hidden | Once | Fast (no re-render) | Smooth |
| Conditional render | Every filter | Slow (re-mount) | Janky |

**When to use conditional:**
- Small lists (< 50 items)
- Complex components with heavy state
- Items with expensive initialization
