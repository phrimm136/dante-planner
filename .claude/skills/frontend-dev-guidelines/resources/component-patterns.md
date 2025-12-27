# Component Patterns

React component architecture for LimbusPlanner. Uses React Compiler for automatic memoization, explicit TypeScript typing (not React.FC), Suspense boundaries, and shadcn/ui + Tailwind for styling.

---

## React Compiler

This project uses **React Compiler** for automatic memoization optimization. Manual `memo`, `useMemo`, and `useCallback` are no longer required in most cases.

### Setup (Vite)

```js
// vite.config.ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [
    react({
      babel: {
        plugins: ['babel-plugin-react-compiler'],
      },
    }),
  ],
})
```

### Installation

```bash
yarn install -D babel-plugin-react-compiler@latest
```

### What React Compiler Does

- **Automatic memoization**: No need for manual `React.memo`, `useMemo`, `useCallback`
- **Build-time optimization**: Analyzes and transforms code at build time
- **Preserves semantics**: Your code works the same, just faster

### Verification

Open React DevTools and look for "Memo ✨" badge next to component names.

### Opt Out (if needed)

```typescript
function ProblematicComponent() {
  "use no memo"
  // Component code that shouldn't be optimized
}
```

**Reference:** [React Compiler Installation](https://react.dev/learn/react-compiler/installation)

---

## Component Declaration Pattern

### Explicit Typing (RECOMMENDED)

Use explicit props typing instead of `React.FC`:

```typescript
interface IdentityCardProps {
  identity: Identity
  isSelected?: boolean
  onSelect?: (identity: Identity) => void
}

// Simple function - React Compiler handles optimization
export function IdentityCard({
  identity,
  isSelected = false,
  onSelect,
}: IdentityCardProps) {
  return (
    <Card className={cn('cursor-pointer', isSelected && 'ring-2 ring-primary')}>
      <CardContent>
        <img src={getIdentityImagePath(identity.id)} alt={identity.name} />
        <p>{identity.name}</p>
      </CardContent>
    </Card>
  )
}
```

### Why Not React.FC

| Issue | Explanation |
|-------|-------------|
| Implicit children | Pre-React 18, FC included children implicitly |
| Syntax inflexibility | Hard to apply to named functions |
| No significant benefit | With TypeScript 5.1+, explicit typing is equally powerful |

**Reference:** [TypeScript React Cheatsheets](https://react-typescript-cheatsheet.netlify.app/docs/basic/getting-started/function_components/)

---

## No Manual Memoization Needed

With React Compiler, you don't need:

```typescript
// ❌ Not needed with React Compiler
export const IdentityCard = memo(function IdentityCard(props) { ... })

const handleClick = useCallback(() => { ... }, [deps])

const expensiveValue = useMemo(() => { ... }, [deps])
```

Just write normal code:

```typescript
// ✅ React Compiler optimizes automatically
export function IdentityCard({ identity, onSelect }: IdentityCardProps) {
  const handleClick = () => onSelect?.(identity)

  return (
    <Card onClick={handleClick}>
      {/* content */}
    </Card>
  )
}
```

---

## Lazy Loading

### When to Lazy Load

- Heavy components (charts, complex forms, grids)
- Route-level components
- Modal/dialog content
- Below-the-fold content

### Pattern

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

---

## Suspense Boundaries

### With useSuspenseQuery

When using `useSuspenseQuery`, wrap the component in Suspense:

```typescript
// Page component - handles Suspense boundary
export function IdentityDetailPage() {
  const { id } = useParams({ strict: false })

  if (!id) return <ErrorState message="No ID provided" />

  return (
    <Suspense fallback={<LoadingState />}>
      <IdentityDetailContent id={id} />
    </Suspense>
  )
}

// Content component - data is guaranteed defined
function IdentityDetailContent({ id }: { id: string }) {
  const { data, i18n } = useEntityDetailData('identity', id)

  // No undefined check needed - Suspense handles loading
  return (
    <DetailPageLayout>
      <h1>{i18n.name}</h1>
      <p>HP: {data.HP}</p>
    </DetailPageLayout>
  )
}
```

### Error Boundary + Suspense

```typescript
import { ErrorBoundary } from 'react-error-boundary'

<ErrorBoundary fallback={<ErrorState />}>
  <Suspense fallback={<LoadingState />}>
    <DataComponent />
  </Suspense>
</ErrorBoundary>
```

---

## Component Structure Order

```typescript
// 1. Imports
import { useState } from 'react'
import { useSuspenseQuery } from '@tanstack/react-query'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'
import type { Identity } from '@/types/IdentityTypes'
import { toast } from 'sonner'

// 2. Props interface
interface IdentityCardProps {
  identity: Identity
  isSelected?: boolean
  onSelect?: (identity: Identity) => void
}

// 3. Component
export function IdentityCard({
  identity,
  isSelected = false,
  onSelect,
}: IdentityCardProps) {
  // Hooks (order: context, query, state, effects)
  const { t } = useTranslation()

  // Event handlers - no useCallback needed
  const handleClick = () => onSelect?.(identity)

  // Render
  return (
    <Card
      className={cn('cursor-pointer', isSelected && 'ring-2 ring-primary')}
      onClick={handleClick}
    >
      <CardContent>
        <img src={getIdentityImagePath(identity.id)} alt={identity.name} />
      </CardContent>
    </Card>
  )
}
```

---

## Component Separation

### When to Split

| Split When | Keep Together When |
|------------|-------------------|
| > 300 lines | < 200 lines |
| Multiple responsibilities | Tightly coupled logic |
| Reusable sections | Not reusable elsewhere |
| Complex nested JSX | Simple presentation |

### Example

```typescript
// ❌ Monolithic
function IdentityPage() {
  // 500+ lines: filters, search, list, modals
}

// ✅ Modular
function IdentityPage() {
  const [filters, setFilters] = useState(...)

  return (
    <div>
      <IdentityFilters filters={filters} onChange={setFilters} />
      <Suspense fallback={<LoadingState />}>
        <IdentityList filters={filters} />
      </Suspense>
    </div>
  )
}
```

---

## Styling with Tailwind + cn()

### Conditional Classes

```typescript
import { cn } from '@/lib/utils'

<Card className={cn(
  'p-4 transition-all',
  isSelected && 'ring-2 ring-primary',
  isDisabled && 'opacity-50 pointer-events-none'
)}>
```

### Responsive Grid

```typescript
<div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-4">
  {items.map(item => <ItemCard key={item.id} item={item} />)}
</div>
```

---

## Props Typing Best Practices

### Explicit Children

```typescript
// If component accepts children, declare explicitly
interface ContainerProps {
  title: string
  children: React.ReactNode  // explicit
}

// If no children expected, omit it
interface ButtonProps {
  label: string
  onClick: () => void
  // no children property
}
```

### Optional Props with Defaults

```typescript
interface CardProps {
  title: string
  variant?: 'default' | 'outline'
  isSelected?: boolean
}

function Card({ title, variant = 'default', isSelected = false }: CardProps) {
  // defaults in destructuring
}
```

---

## Toast Notifications

Use `sonner` for toasts:

```typescript
import { toast } from 'sonner'

toast.success('Saved successfully')
toast.error('Failed to save')
toast.info('Processing...', { description: 'Please wait' })
```

---

## Summary

| Pattern | Recommendation |
|---------|---------------|
| Memoization | React Compiler (automatic) |
| Component typing | Explicit props interface (not React.FC) |
| Data fetching | useSuspenseQuery + Suspense boundary |
| Styling | Tailwind + cn() utility |
| Toasts | sonner |

**See Also:**
- [data-fetching.md](data-fetching.md) - Query patterns
- [styling-guide.md](styling-guide.md) - Tailwind + shadcn/ui
- [file-organization.md](file-organization.md) - Directory structure
