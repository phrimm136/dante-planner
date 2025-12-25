# TypeScript Standards

TypeScript best practices for LimbusPlanner frontend.

---

## Strict Mode

TypeScript strict mode is enabled:

```json
// tsconfig.json
{
  "compilerOptions": {
    "strict": true,
    "noImplicitAny": true,
    "strictNullChecks": true
  }
}
```

---

## No `any` Type

```typescript
// Bad - loses type safety
function handleData(data: any) {
  return data.something
}

// Good - explicit type
interface MyData {
  something: string
}

function handleData(data: MyData) {
  return data.something
}

// Good - use unknown if truly unknown
function handleUnknown(data: unknown) {
  if (typeof data === 'object' && data !== null && 'something' in data) {
    return (data as MyData).something
  }
}
```

---

## Explicit Return Types

```typescript
// Good - explicit return type
function getUser(id: string): Promise<User> {
  return fetch(`/api/users/${id}`).then(r => r.json())
}

function calculateTotal(items: Item[]): number {
  return items.reduce((sum, item) => sum + item.price, 0)
}

// Custom hooks - explicit return type
function useMyData(id: string): { data: Data | undefined; isPending: boolean } {
  const [data, setData] = useState<Data>()
  const [isPending, setIsPending] = useState(true)
  return { data, isPending }
}
```

---

## Type Imports

Use `type` keyword for type-only imports:

```typescript
// Good - explicitly mark as type import
import type { Identity, IdentityData } from '@/types/IdentityTypes'
import type { EntityType } from '@/types/common'

// Also good - mixed import
import { validateData } from '@/lib/validation'
import type { ValidationContext } from '@/lib/validation'
```

**Benefits:**
- Clearer separation of types vs values
- Better tree-shaking
- Prevents circular dependencies

---

## Component Props

### Interface Pattern

```typescript
interface IdentityCardProps {
  identity: Identity
  isSelected?: boolean
  onSelect?: (identity: Identity) => void
}

export function IdentityCard({
  identity,
  isSelected = false,
  onSelect,
}: IdentityCardProps) {
  return (...)
}
```

### With Children

```typescript
interface ContainerProps {
  children: React.ReactNode
  title: string
}

export function Container({ children, title }: ContainerProps) {
  return (
    <div>
      <h2>{title}</h2>
      {children}
    </div>
  )
}
```

---

## Utility Types

### Partial<T>

```typescript
// Make all properties optional
type IdentityUpdate = Partial<Identity>

function updateIdentity(id: string, updates: Partial<Identity>) {
  // updates can have any subset of Identity properties
}
```

### Pick<T, K>

```typescript
// Select specific properties
type IdentityPreview = Pick<Identity, 'id' | 'name'>

const preview: IdentityPreview = {
  id: '10101',
  name: 'Yi Sang',
}
```

### Omit<T, K>

```typescript
// Exclude specific properties
type IdentityWithoutId = Omit<Identity, 'id'>
```

### Record<K, V>

```typescript
// Type-safe object/map
const identityMap: Record<string, Identity> = {
  '10101': { id: '10101', name: 'Yi Sang', ... },
  '10201': { id: '10201', name: 'Faust', ... },
}
```

---

## Literal Types and Constants

### Derive Types from Constants

```typescript
// constants.ts
export const SINNERS = [
  'YiSang', 'Faust', 'DonQuixote', 'Ryoshu',
  'Meursault', 'HongLu', 'Heathcliff', 'Ishmael',
  'Rodion', 'Sinclair', 'Outis', 'Gregor',
] as const

export type Sinner = typeof SINNERS[number]
// Result: 'YiSang' | 'Faust' | 'DonQuixote' | ...

export const AFFINITIES = ['CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK', 'AZURE', 'INDIGO', 'VIOLET'] as const

export type Affinity = typeof AFFINITIES[number]
```

### Use in Schemas

```typescript
// schemas/SharedSchemas.ts
import { z } from 'zod'

export const SinnerSchema = z.enum([
  'YiSang', 'Faust', 'DonQuixote', 'Ryoshu',
  'Meursault', 'HongLu', 'Heathcliff', 'Ishmael',
  'Rodion', 'Sinclair', 'Outis', 'Gregor',
])

export const AffinitySchema = z.enum([
  'CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK', 'AZURE', 'INDIGO', 'VIOLET',
])
```

---

## Type Guards

### Basic Type Guard

```typescript
function isIdentity(data: unknown): data is Identity {
  return (
    typeof data === 'object' &&
    data !== null &&
    'id' in data &&
    'name' in data
  )
}

// Usage
if (isIdentity(response)) {
  console.log(response.name)  // TypeScript knows it's Identity
}
```

### Discriminated Unions

```typescript
type QueryState<T> =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: Error }

function handleState<T>(state: QueryState<T>) {
  switch (state.status) {
    case 'success':
      return state.data  // TypeScript knows data exists
    case 'error':
      return state.error  // TypeScript knows error exists
    default:
      return null
  }
}
```

---

## Generics

### Generic Functions

```typescript
function getById<T extends { id: string }>(items: T[], id: string): T | undefined {
  return items.find(item => item.id === id)
}

// Usage
const identity = getById(identities, '10101')  // Type: Identity | undefined
```

### Generic Components

```typescript
interface ListProps<T> {
  items: T[]
  renderItem: (item: T) => React.ReactNode
  keyExtractor: (item: T) => string
}

export function List<T>({ items, renderItem, keyExtractor }: ListProps<T>) {
  return (
    <div>
      {items.map(item => (
        <div key={keyExtractor(item)}>{renderItem(item)}</div>
      ))}
    </div>
  )
}

// Usage
<List<Identity>
  items={identities}
  renderItem={(identity) => <IdentityCard identity={identity} />}
  keyExtractor={(identity) => identity.id}
/>
```

---

## Null/Undefined Handling

### Optional Chaining

```typescript
const name = user?.profile?.name
const firstSkill = identity?.skills?.skill1?.[0]
```

### Nullish Coalescing

```typescript
const displayName = user?.name ?? 'Anonymous'

// Different from || which triggers on '', 0, false
const count = data?.count ?? 0  // Uses 0 only if null/undefined
```

### Non-Null Assertion (Use Sparingly)

```typescript
// Only use when you're certain value exists
const data = queryClient.getQueryData<Data>(['data'])!

// Better: explicit check
const data = queryClient.getQueryData<Data>(['data'])
if (data) {
  // Use data
}
```

---

## Type Assertions

### When Appropriate

```typescript
// After Zod validation - data is known to be valid
const validatedData = schema.parse(rawData) as IdentityData

// DOM elements
const input = document.getElementById('search') as HTMLInputElement
```

### When to Avoid

```typescript
// Bad - circumventing type safety
const data = getData() as any

// Bad - unsafe assertion
const value = unknownValue as string  // Might not be string
```

---

## Summary

| Pattern | Recommendation |
|---------|---------------|
| `any` type | Never use - use `unknown` if needed |
| Return types | Explicit on functions and hooks |
| Type imports | Use `import type` for types |
| Props | Interface with explicit types |
| Constants | Use `as const` and derive types |
| Schemas | Match types with Zod enums |
| Type guards | Use for runtime type checking |
| Null handling | Optional chaining + nullish coalescing |

**See Also:**
- [schemas-and-validation.md](schemas-and-validation.md) - Zod type inference
- [component-patterns.md](component-patterns.md) - Props typing
