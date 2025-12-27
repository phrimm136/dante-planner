# TypeScript Standards

TypeScript best practices for type safety and maintainability in React frontend code. Uses explicit props typing (not React.FC), strict mode, and modern patterns. Prefer complex, precise types over simple, loose types for better type safety.

---

## Strict Mode

### Configuration

TypeScript strict mode is **enabled** in the project:

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

**This means:**
- No implicit `any` types
- Null/undefined must be handled explicitly
- Type safety enforced

---

## No `any` Type

### The Rule

```typescript
// ❌ NEVER use any
function handleData(data: any) {
  return data.something
}

// ✅ Use specific types
interface MyData {
  something: string
}

function handleData(data: MyData) {
  return data.something
}

// ✅ Or use unknown for truly unknown data
function handleUnknown(data: unknown) {
  if (typeof data === 'object' && data !== null && 'something' in data) {
    return (data as MyData).something
  }
}
```

**If you truly don't know the type:**
- Use `unknown` (forces type checking)
- Use type guards to narrow
- Document why type is unknown

---

## Explicit Props Interface (Not React.FC)

### Recommended Pattern

```typescript
// ✅ CORRECT - Explicit props interface
interface UserCardProps {
  user: User
  isSelected?: boolean
  onSelect?: (user: User) => void
}

export function UserCard({
  user,
  isSelected = false,
  onSelect,
}: UserCardProps) {
  const handleClick = () => onSelect?.(user)

  return (
    <Card onClick={handleClick}>
      <p>{user.name}</p>
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

```typescript
// ❌ AVOID - React.FC pattern
const UserCard: React.FC<UserCardProps> = ({ user }) => {
  return <div>{user.name}</div>
}

// ✅ PREFERRED - Explicit props
function UserCard({ user }: UserCardProps) {
  return <div>{user.name}</div>
}
```

**Reference:** [TypeScript React Cheatsheets](https://react-typescript-cheatsheet.netlify.app/docs/basic/getting-started/function_components/)

---

## Explicit Return Types

### Function Return Types

```typescript
// ✅ CORRECT - Explicit return type
function getUser(id: string): Promise<User> {
  return apiClient.get(`/users/${id}`)
}

function calculateTotal(items: Item[]): number {
  return items.reduce((sum, item) => sum + item.price, 0)
}

// ❌ AVOID - Implicit return type (less clear)
function getUser(id: string) {
  return apiClient.get(`/users/${id}`)
}
```

### Custom Hook Return Types

```typescript
interface UseUserDataReturn {
  user: User | undefined
  isLoading: boolean
  error: Error | null
}

function useUserData(userId: string): UseUserDataReturn {
  const { data, isLoading, error } = useQuery({
    queryKey: ['user', userId],
    queryFn: () => fetchUser(userId),
  })

  return { user: data, isLoading, error }
}
```

---

## Type Imports

### Use 'type' Keyword

```typescript
// ✅ CORRECT - Explicitly mark as type import
import type { User } from '@/types/UserTypes'
import type { Identity } from '@/types/IdentityTypes'

// ❌ AVOID - Mixed value and type imports
import { User } from '@/types/user'  // Unclear if type or value
```

**Benefits:**
- Clearly separates types from values
- Better tree-shaking
- Prevents circular dependencies
- TypeScript compiler optimization

---

## Complex Type Definitions

### Prefer Precise Types Over Loose Types

Always prefer complex, precise types that capture the exact shape of your data:

```typescript
// ❌ AVOID - Too loose, allows invalid states
interface Identity {
  id: number
  rarity: number  // Could be any number
  affinities: string[]  // Could be any strings
}

// ✅ CORRECT - Precise types that capture domain constraints
type IdentityRarity = 0 | 1 | 2 | 3  // O, OO, OOO, OOOO
type AffinityType = 'slash' | 'pierce' | 'blunt' | 'burn' | 'bleed' | 'tremor' | 'rupture' | 'sinking' | 'poise'
type SinType = 'wrath' | 'lust' | 'sloth' | 'gluttony' | 'gloom' | 'pride' | 'envy'

interface Identity {
  id: number
  rarity: IdentityRarity
  affinities: AffinityType[]
  sin: SinType
}
```

### Conditional Types

```typescript
// Type that varies based on a condition
type EntityData<T extends 'identity' | 'ego' | 'egoGift'> =
  T extends 'identity' ? Identity :
  T extends 'ego' ? EGO :
  T extends 'egoGift' ? EGOGift :
  never

// Usage - TypeScript infers the correct return type
function getEntityData<T extends 'identity' | 'ego' | 'egoGift'>(
  type: T,
  id: number
): EntityData<T> {
  // implementation
}

const identity = getEntityData('identity', 10101)  // Type: Identity
const ego = getEntityData('ego', 20101)  // Type: EGO
```

### Mapped Types

```typescript
// Create a type where all properties are optional and readonly
type PartialReadonly<T> = {
  readonly [P in keyof T]?: T[P]
}

// Create a type that transforms all properties to getters
type Getters<T> = {
  [K in keyof T as `get${Capitalize<string & K>}`]: () => T[K]
}

interface User {
  name: string
  age: number
}

type UserGetters = Getters<User>
// Result: { getName: () => string, getAge: () => number }
```

### Template Literal Types

```typescript
// Type-safe string patterns
type EntityType = 'identity' | 'ego' | 'egoGift'
type Locale = 'EN' | 'KR' | 'JP' | 'CN'

// Type-safe path patterns
type DataPath = `/data/${EntityType}/${number}.json`
type I18nPath = `/i18n/${Locale}/${EntityType}/${number}.json`

function fetchData(path: DataPath): Promise<unknown> {
  return fetch(path).then(r => r.json())
}

// ✅ Valid
fetchData('/data/identity/10101.json')

// ❌ TypeScript error - invalid path
fetchData('/data/invalid/10101.json')
```

### Indexed Access Types

```typescript
// Extract nested types
interface EGOGift {
  id: number
  tier: 1 | 2 | 3
  effects: {
    passive: PassiveEffect[]
    active: ActiveEffect | null
  }
}

// Extract the type of a specific property
type EGOGiftTier = EGOGift['tier']  // 1 | 2 | 3
type EffectsType = EGOGift['effects']  // { passive: ..., active: ... }
type PassiveEffects = EGOGift['effects']['passive']  // PassiveEffect[]
```

### Branded/Nominal Types

```typescript
// Create distinct types that are structurally identical but semantically different
type IdentityId = number & { readonly __brand: 'IdentityId' }
type EGOId = number & { readonly __brand: 'EGOId' }

function createIdentityId(id: number): IdentityId {
  return id as IdentityId
}

function createEGOId(id: number): EGOId {
  return id as EGOId
}

function fetchIdentity(id: IdentityId): Promise<Identity> {
  // ...
}

const identityId = createIdentityId(10101)
const egoId = createEGOId(20101)

fetchIdentity(identityId)  // ✅ Valid
fetchIdentity(egoId)  // ❌ TypeScript error - EGOId not assignable to IdentityId
```

### Complex Union Types

```typescript
// Discriminated union for different effect types
type SkillEffect =
  | { type: 'damage'; element: SinType; basePower: number; coinPower: number }
  | { type: 'heal'; amount: number; target: 'self' | 'ally' | 'all' }
  | { type: 'buff'; stat: StatType; value: number; duration: number }
  | { type: 'debuff'; statusEffect: StatusEffect; chance: number }

function applyEffect(effect: SkillEffect): void {
  switch (effect.type) {
    case 'damage':
      // TypeScript knows: element, basePower, coinPower available
      console.log(`Dealing ${effect.basePower} ${effect.element} damage`)
      break
    case 'heal':
      // TypeScript knows: amount, target available
      console.log(`Healing ${effect.amount} to ${effect.target}`)
      break
    case 'buff':
      // TypeScript knows: stat, value, duration available
      break
    case 'debuff':
      // TypeScript knows: statusEffect, chance available
      break
  }
}
```

### Recursive Types

```typescript
// Tree structure for nested data
interface FilterNode {
  type: 'and' | 'or'
  children: FilterExpression[]
}

interface FilterLeaf {
  type: 'condition'
  field: string
  operator: 'eq' | 'neq' | 'gt' | 'lt' | 'contains'
  value: string | number | boolean
}

type FilterExpression = FilterNode | FilterLeaf

// Example usage
const complexFilter: FilterExpression = {
  type: 'and',
  children: [
    { type: 'condition', field: 'rarity', operator: 'eq', value: 3 },
    {
      type: 'or',
      children: [
        { type: 'condition', field: 'sin', operator: 'eq', value: 'wrath' },
        { type: 'condition', field: 'sin', operator: 'eq', value: 'pride' },
      ],
    },
  ],
}
```

---

## Component Prop Interfaces

### Interface Pattern

```typescript
/**
 * Props for IdentityCard component
 */
interface IdentityCardProps {
  /** The identity to display */
  identity: Identity

  /** Whether the card is selected */
  isSelected?: boolean

  /** Callback when card is clicked */
  onSelect?: (identity: Identity) => void

  /** Additional CSS classes */
  className?: string
}

export function IdentityCard({
  identity,
  isSelected = false,
  onSelect,
  className,
}: IdentityCardProps) {
  return (
    <Card className={cn('cursor-pointer', isSelected && 'ring-2', className)}>
      {/* content */}
    </Card>
  )
}
```

**Key Points:**
- Separate interface for props
- JSDoc comments for each prop
- Optional props use `?`
- Provide defaults in destructuring

### Props with Children

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
type UserUpdate = Partial<User>

function updateUser(id: string, updates: Partial<User>) {
  // updates can have any subset of User properties
}
```

### Pick<T, K>

```typescript
// Select specific properties
type UserPreview = Pick<User, 'id' | 'name' | 'email'>

const preview: UserPreview = {
  id: '1',
  name: 'John',
  email: 'john@example.com',
  // Other User properties not allowed
}
```

### Omit<T, K>

```typescript
// Exclude specific properties
type UserWithoutPassword = Omit<User, 'password' | 'passwordHash'>

const publicUser: UserWithoutPassword = {
  id: '1',
  name: 'John',
  email: 'john@example.com',
  // password and passwordHash not allowed
}
```

### Required<T>

```typescript
// Make all properties required
type RequiredConfig = Required<Config>  // All optional props become required
```

### Record<K, V>

```typescript
// Type-safe object/map
const userMap: Record<string, User> = {
  'user1': { id: '1', name: 'John' },
  'user2': { id: '2', name: 'Jane' },
}

// For query keys
const queryKeys: Record<string, readonly string[]> = {
  users: ['users'],
  posts: ['posts'],
}
```

### Extract and Exclude

```typescript
// Extract union members that match a condition
type SinType = 'wrath' | 'lust' | 'sloth' | 'gluttony' | 'gloom' | 'pride' | 'envy'
type PositiveSins = Extract<SinType, 'pride' | 'wrath'>  // 'pride' | 'wrath'

// Exclude union members that match a condition
type NegativeSins = Exclude<SinType, 'pride'>  // Everything except 'pride'
```

### NonNullable

```typescript
// Remove null and undefined from a type
type MaybeUser = User | null | undefined
type DefiniteUser = NonNullable<MaybeUser>  // User
```

---

## Type Guards

### Basic Type Guards

```typescript
function isUser(data: unknown): data is User {
  return (
    typeof data === 'object' &&
    data !== null &&
    'id' in data &&
    'name' in data
  )
}

// Usage
if (isUser(response)) {
  console.log(response.name)  // TypeScript knows it's User
}
```

### Discriminated Unions

```typescript
type LoadingState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: Data }
  | { status: 'error'; error: Error }

function Component({ state }: { state: LoadingState }) {
  // TypeScript narrows type based on status
  if (state.status === 'success') {
    return <Display data={state.data} />  // data available here
  }

  if (state.status === 'error') {
    return <Error error={state.error} />  // error available here
  }

  return <Loading />
}
```

### Assertion Functions

```typescript
function assertIsUser(data: unknown): asserts data is User {
  if (typeof data !== 'object' || data === null) {
    throw new Error('Expected object')
  }
  if (!('id' in data) || !('name' in data)) {
    throw new Error('Expected User shape')
  }
}

// After this call, TypeScript knows data is User
assertIsUser(response)
console.log(response.name)  // No type error
```

---

## Generic Types

### Generic Functions

```typescript
function getById<T extends { id: string }>(items: T[], id: string): T | undefined {
  return items.find((item) => item.id === id)
}

// Usage with type inference
const users: User[] = [...]
const user = getById(users, '123')  // Type: User | undefined
```

### Generic Components

```typescript
interface ListProps<T> {
  items: T[]
  renderItem: (item: T) => React.ReactNode
  keyExtractor: (item: T) => string
}

export function List<T>({
  items,
  renderItem,
  keyExtractor,
}: ListProps<T>) {
  return (
    <div>
      {items.map((item) => (
        <div key={keyExtractor(item)}>{renderItem(item)}</div>
      ))}
    </div>
  )
}

// Usage
<List<User>
  items={users}
  renderItem={(user) => <UserCard user={user} />}
  keyExtractor={(user) => user.id}
/>
```

### Generic Constraints

```typescript
// Ensure T has specific properties
interface HasId {
  id: string | number
}

function sortById<T extends HasId>(items: T[]): T[] {
  return [...items].sort((a, b) => {
    if (typeof a.id === 'number' && typeof b.id === 'number') {
      return a.id - b.id
    }
    return String(a.id).localeCompare(String(b.id))
  })
}

// Works with any type that has an id property
sortById(identities)  // ✅
sortById(egos)  // ✅
sortById([{ name: 'test' }])  // ❌ Error: missing id
```

---

## Type Assertions (Use Sparingly)

### When to Use

```typescript
// ✅ OK - When you know more than TypeScript
const element = document.getElementById('my-input') as HTMLInputElement
const value = element.value

// ✅ OK - With Zod validated data
const validatedData = schema.parse(rawData)
// validatedData is now typed according to schema
```

### When NOT to Use

```typescript
// ❌ AVOID - Circumventing type safety
const data = getData() as any  // WRONG - defeats TypeScript

// ❌ AVOID - Unsafe assertion
const value = unknownValue as string  // Might not actually be string
```

---

## Null/Undefined Handling

### Optional Chaining

```typescript
// ✅ CORRECT
const name = user?.profile?.name

// Equivalent to:
const name = user && user.profile && user.profile.name
```

### Nullish Coalescing

```typescript
// ✅ CORRECT
const displayName = user?.name ?? 'Anonymous'

// Only uses default if null or undefined
// (Different from || which triggers on '', 0, false)
```

### Non-Null Assertion (Use Carefully)

```typescript
// ⚠️ CAREFUL - Only use when you KNOW it's not null
const data = queryClient.getQueryData<Data>(['data'])!

// Better to check explicitly:
const data = queryClient.getQueryData<Data>(['data'])
if (data) {
  // Use data
}
```

---

## Zod Integration

### Schema-Driven Types

```typescript
import { z } from 'zod'

// Define schema
const userSchema = z.object({
  id: z.string(),
  name: z.string().min(1),
  email: z.string().email(),
  role: z.enum(['admin', 'user', 'guest']),
})

// Infer type from schema
type User = z.infer<typeof userSchema>

// Use for runtime validation
function parseUser(data: unknown): User {
  return userSchema.parse(data)
}
```

### Form Types with Zod

```typescript
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'

const formSchema = z.object({
  username: z.string().min(3),
  email: z.string().email(),
})

type FormData = z.infer<typeof formSchema>

function MyForm() {
  const form = useForm<FormData>({
    resolver: zodResolver(formSchema),
  })

  // form is fully typed
}
```

---

## Summary

**TypeScript Checklist:**
- Strict mode enabled
- No `any` type (use `unknown` if needed)
- Explicit props interface (not React.FC)
- Explicit return types on functions
- Use `import type` for type imports
- Prefer complex, precise types over loose types
- Use conditional types for type-safe variations
- Use discriminated unions for state management
- JSDoc comments on prop interfaces
- Utility types (Partial, Pick, Omit, Required, Record)
- Type guards for narrowing
- Optional chaining and nullish coalescing
- Zod for runtime validation

**See Also:**
- [component-patterns.md](component-patterns.md) - Component typing
- [data-fetching.md](data-fetching.md) - API typing
- [schemas-and-validation.md](schemas-and-validation.md) - Zod patterns
