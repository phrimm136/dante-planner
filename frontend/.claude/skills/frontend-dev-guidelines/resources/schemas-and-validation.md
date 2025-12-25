# Schemas and Validation

Zod runtime validation for LimbusPlanner. All external JSON data must be validated before use.

---

## Core Principles

### Aggressive Literal Typing

**Use literal types instead of generic `string`, `array`:**

```typescript
// ❌ Generic - loses type information
export const SkillDataSchema = z.object({
  sin: z.string(),
  atkType: z.string(),
})

// ✅ Aggressive - preserves type information
export const SkillDataSchema = z.object({
  sin: SinSchema,  // z.enum(['Wrath', 'Lust', 'Sloth', ...])
  atkType: AtkTypeSchema,  // z.enum(['SLASH', 'PENETRATE', 'HIT'])
})
```

### Use Shared Schemas

Reuse schemas for domain constants that appear across multiple entities:

```typescript
// schemas/SharedSchemas.ts
export const SinSchema = z.enum([
  'Wrath', 'Lust', 'Sloth', 'Gluttony', 'Gloom', 'Pride', 'Envy',
])

export const AffinitySchema = z.enum([
  'CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK', 'AZURE', 'INDIGO', 'VIOLET',
])

// Use in other schemas
import { SinSchema, AffinitySchema } from './SharedSchemas'

export const SkillDataSchema = z.object({
  sin: SinSchema,
  // ...
})
```

---

## Schema Patterns

### Enum Schemas (Literal Types)

For any field with known possible values, use `z.enum()`:

```typescript
// Attack types
export const AtkTypeSchema = z.enum(['SLASH', 'PENETRATE', 'HIT'])

// Uptie levels
export const UptieSchema = z.enum(['3', '4'])

// EGO ranks
export const EGORankSchema = z.enum(['ZAYIN', 'TETH', 'HE', 'WAW', 'ALEPH'])

// Image variants
export const ImageVariantSchema = z.enum(['gacksung', 'normal'])
```

### Synchronize with TypeScript Constants

Keep schemas in sync with `constants.ts`:

```typescript
// lib/constants.ts
export const AFFINITIES = ['CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK', 'AZURE', 'INDIGO', 'VIOLET'] as const
export type Affinity = typeof AFFINITIES[number]

// schemas/SharedSchemas.ts - mirrors constants
export const AffinitySchema = z.enum([
  'CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK', 'AZURE', 'INDIGO', 'VIOLET',
])
```

### Object Schemas with .strict()

Always use `.strict()` to reject unexpected fields:

```typescript
export const IdentityDataSchema = z.object({
  sinner: z.string(),
  grade: z.number(),
  HP: z.number(),
  resist: z.tuple([z.number(), z.number(), z.number()]),
  skills: SkillsDataSchema,
}).strict()  // Rejects extra fields
```

### Nullable vs Optional

```typescript
// Field can be null (present but null)
keyword: z.string().nullable()  // "keyword": null

// Field can be missing (omitted)
keyword: z.string().optional()  // field not present

// Field can be null OR missing
keyword: z.string().nullable().optional()
```

### Tuple for Fixed-Length Arrays

When array length is known:

```typescript
// ❌ Generic array
resist: z.array(z.number())

// ✅ Tuple with exact length
resist: z.tuple([z.number(), z.number(), z.number()])  // [slash, pierce, blunt]
```

### Literal Keys in Objects

For objects with literal string keys:

```typescript
export const SkillDataSchema = z.object({
  upties: z.object({
    '3': UptieDataSchema,
    '4': UptieDataSchema,
  }).strict(),
}).strict()
```

### Array of Enums

When array contains known values:

```typescript
// ❌ Generic
attributeType: z.array(z.string())

// ✅ Array of enum
attributeType: z.array(AffinitySchema)
```

---

## File Organization

```
schemas/
├── SharedSchemas.ts    # Reusable enums (Sin, Affinity, PassiveI18n)
├── IdentitySchemas.ts  # Identity data & i18n schemas
├── EGOSchemas.ts       # EGO data & i18n schemas
├── EGOGiftSchemas.ts   # EGO Gift schemas
├── ColorCodeSchemas.ts
├── StartGiftSchemas.ts
└── index.ts            # Re-exports all schemas
```

### index.ts Export Pattern

```typescript
// Shared schemas
export { SinSchema, AffinitySchema, PassiveI18nSchema } from './SharedSchemas'

// Entity-specific schemas
export {
  IdentityDataSchema,
  IdentityI18nSchema,
  IdentitySpecListSchema,
} from './IdentitySchemas'
```

---

## Runtime Validation

### validateData Utility

All JSON data must be validated using the `validateData` utility:

```typescript
import { validateData } from '@/lib/validation'
import { IdentityDataSchema } from '@/schemas'

const response = await fetch(`/data/identity/${id}.json`)
const rawData = await response.json()

// Validates and returns typed data, throws on failure
const data = validateData<IdentityData>(
  rawData,
  IdentityDataSchema,
  { entityType: 'identity', dataKind: 'detail', id }
)
```

### Error Context

Always provide context for debugging:

```typescript
validateData(data, schema, {
  entityType: 'identity',  // Which entity type
  dataKind: 'detail',      // 'detail' | 'i18n' | 'list'
  id: '10101',             // Entity ID
})
```

---

## Schema Creation Checklist

When creating a new schema:

- [ ] Use `z.enum()` for all fields with known values
- [ ] Check `constants.ts` for existing literal types
- [ ] Create shared schema if enum is used in multiple places
- [ ] Use `.strict()` on all object schemas
- [ ] Use `z.tuple()` for fixed-length arrays
- [ ] Add export to `schemas/index.ts`
- [ ] Ensure TypeScript interface matches schema structure

---

## Common Schemas Reference

| Schema | Values | Used For |
|--------|--------|----------|
| `SinSchema` | Wrath, Lust, Sloth, Gluttony, Gloom, Pride, Envy | Skill sin type |
| `AffinitySchema` | CRIMSON, SCARLET, AMBER, SHAMROCK, AZURE, INDIGO, VIOLET | Attribute types |
| `AtkTypeSchema` | SLASH, PENETRATE, HIT | Attack types |
| `UptieSchema` | '3', '4' | Uptie levels |
| `EGORankSchema` | ZAYIN, TETH, HE, WAW, ALEPH | EGO rarity |

---

## Type Inference from Schemas

Derive TypeScript types from Zod schemas when appropriate:

```typescript
import { z } from 'zod'

export const AffinitySchema = z.enum([
  'CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK', 'AZURE', 'INDIGO', 'VIOLET',
])

// Infer type from schema
export type Affinity = z.infer<typeof AffinitySchema>
// Result: 'CRIMSON' | 'SCARLET' | 'AMBER' | 'SHAMROCK' | 'AZURE' | 'INDIGO' | 'VIOLET'
```

---

## Summary

| Pattern | Example |
|---------|---------|
| Literal enum | `z.enum(['Wrath', 'Lust', ...])` |
| Shared schema | `SinSchema`, `AffinitySchema` |
| Strict objects | `.strict()` |
| Fixed arrays | `z.tuple([z.number(), z.number(), z.number()])` |
| Array of enums | `z.array(AffinitySchema)` |
| Nullable field | `z.string().nullable()` |
| Literal keys | `{ '3': Schema, '4': Schema }` |

**See Also:**
- [data-fetching.md](data-fetching.md) - Using schemas in queries
- [file-organization.md](file-organization.md) - Schema file locations
