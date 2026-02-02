---
paths:
  - "frontend/src/components/**/*.tsx"
---

# cn() Utility for Conditional Classes

## Import
```typescript
import { cn } from '@/lib/utils'
```

## Basic Usage
```typescript
// Merge classes
<div className={cn('p-4', 'bg-white')} />

// Conditional classes
<Card className={cn(
  'p-4 transition-all',
  isSelected && 'ring-2 ring-primary',
  isDisabled && 'opacity-50 pointer-events-none'
)} />

// Dynamic values
<div className={cn(
  'flex gap-2',
  direction === 'vertical' ? 'flex-col' : 'flex-row'
)} />
```

## Common Patterns

### Selectable Card
```typescript
<Card className={cn(
  'cursor-pointer transition-all',
  'hover:shadow-md hover:scale-[1.02]',
  isSelected && 'ring-2 ring-primary'
)}>
```

### Disabled State
```typescript
<Button className={cn(
  'w-full',
  isDisabled && 'opacity-50 pointer-events-none'
)}>
```

### Size Variants
```typescript
<div className={cn(
  'rounded-lg border',
  size === 'sm' && 'p-2 text-sm',
  size === 'md' && 'p-4',
  size === 'lg' && 'p-6 text-lg'
)}>
```
