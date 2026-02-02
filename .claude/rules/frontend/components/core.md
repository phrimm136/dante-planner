---
paths:
  - "frontend/src/components/**/*.tsx"
  - "frontend/src/components/**/*.ts"
---

# Component Core Patterns

## Forbidden Patterns

| Forbidden | Use Instead |
|-----------|-------------|
| `React.FC<Props>` | `function Component(props: Props)` |
| `memo()`, `useCallback()`, `useMemo()` without profiling | Let React Compiler optimize first; use manual optimization ONLY with explicit intent, user approval, and measured performance benefit |
| `if (loading) return <Spinner />` | `<Suspense fallback={...}>` |

## Template

```typescript
interface CardProps {
  item: Item
  isSelected?: boolean
  onSelect?: (item: Item) => void
}

export function Card({ item, isSelected = false, onSelect }: CardProps) {
  return (
    <div
      className="selectable p-4 rounded-lg border"
      data-selected={isSelected}
      onClick={() => onSelect?.(item)}
    >
      {item.name}
    </div>
  )
}
```

## Suspense Pattern

```typescript
export function DetailPage() {
  const { id } = useParams()
  if (!id) return <ErrorState />
  return (
    <Suspense fallback={<LoadingState />}>
      <DetailContent id={id} />
    </Suspense>
  )
}
```

**Reference:** `IdentityCard.tsx`, `EGOGiftCard.tsx`
