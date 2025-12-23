# Common Patterns

Quick reference for TypeScript to Zod mappings.

## Table of Contents

- [Type Mappings](#type-mappings)
- [Basic Examples](#basic-examples)
- [Optional Fields](#optional-fields)
- [Arrays and Tuples](#arrays-and-tuples)
- [Enums and Unions](#enums-and-unions)
- [Records](#records)
- [Best Practices](#best-practices)

---

## Type Mappings

| TypeScript | Zod |
|------------|-----|
| `string` | `z.string()` |
| `number` | `z.number()` |
| `boolean` | `z.boolean()` |
| `field?: T` | `field: schema.optional()` |
| `string[]` | `z.array(z.string())` |
| `[A, B, C]` | `z.tuple([schemaA, schemaB, schemaC])` |
| `'A' \| 'B'` | `z.enum(['A', 'B'])` |
| `1 \| 2 \| 3` | `z.union([z.literal(1), z.literal(2), z.literal(3)])` |
| `Record<K, V>` | `z.record(keySchema, valueSchema)` |

---

## Basic Examples

```typescript
// TypeScript
interface BasicData {
  name: string
  count: number
  active: boolean
}

// Zod
const BasicDataSchema = z.object({
  name: z.string(),
  count: z.number(),
  active: z.boolean(),
}).strict()
```

---

## Optional Fields

```typescript
// TypeScript
interface UserData {
  name: string
  nickname?: string
}

// Zod
const UserDataSchema = z.object({
  name: z.string(),
  nickname: z.string().optional(),
}).strict()
```

---

## Arrays and Tuples

### Simple Arrays

```typescript
// TypeScript
interface ListData {
  tags: string[]
}

// Zod
const ListDataSchema = z.object({
  tags: z.array(z.string()),
}).strict()
```

### Tuples (Fixed Length)

```typescript
// TypeScript - [slash, pierce, blunt]
interface ResistData {
  resist: [number, number, number]
}

// Zod
const ResistDataSchema = z.object({
  resist: z.tuple([z.number(), z.number(), z.number()]),
}).strict()
```

---

## Enums and Unions

### String Enums

```typescript
// TypeScript
type AtkType = 'SLASH' | 'PENETRATE' | 'HIT'

// Zod
const AtkTypeSchema = z.enum(['SLASH', 'PENETRATE', 'HIT'])
```

### Numeric Unions

```typescript
// TypeScript
type Grade = 1 | 2 | 3

// Zod
const GradeSchema = z.union([z.literal(1), z.literal(2), z.literal(3)])
```

### String Literal Keys

```typescript
// TypeScript
interface UptieData {
  upties: { '3': UptieLevel; '4': UptieLevel }
}

// Zod
const UptieDataSchema = z.object({
  upties: z.object({
    '3': UptieLevelSchema,
    '4': UptieLevelSchema,
  }).strict(),
}).strict()
```

---

## Records

```typescript
// TypeScript
type NameList = Record<string, string>
type DataList = Record<string, EntityData>

// Zod
const NameListSchema = z.record(z.string(), z.string())
const DataListSchema = z.record(z.string(), EntityDataSchema)
```

---

## Shared Schemas

```typescript
// SharedSchemas.ts - Reusable across entities
export const AffinitySchema = z.enum([
  'CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK',
  'AZURE', 'INDIGO', 'VIOLET', 'NEUTRAL'
])

export const SinSchema = AffinitySchema  // Alias

// Usage in other schemas
import { SinSchema } from './SharedSchemas'
const SkillDataSchema = z.object({
  sin: SinSchema,
}).strict()
```

---

## Best Practices

1. **Always use `.strict()`** on objects - catches unexpected fields
2. **Define nested schemas first** - better organization
3. **Reuse shared schemas** from `SharedSchemas.ts`
4. **Keep TypeScript types and Zod schemas in sync**
5. **Use meaningful names** - `SinSchema` not `StringEnumSchema`
