# JSON to Hook Pipeline

Complete pipeline for creating type-safe data structures from JSON files: **JSON → Types → Schemas → Hook with Validation**.

---

## Pipeline Overview

```
JSON File
    ↓ (1) Analyze structure, find patterns
TypeScript Types + Constants
    ↓ (2) Mirror with Zod schemas
Zod Schemas
    ↓ (3) Create data hook with validation
React Query Hook + Validation
```

**CRITICAL: When loading new JSON data, you MUST complete ALL steps in order.**

---

## File Locations

| Step | Location | Naming |
|------|----------|--------|
| JSON Data | `static/data/{entity}/` | `{id}.json` |
| JSON i18n | `static/i18n/{lang}/{entity}/` | `{id}.json` |
| **Constants** | `frontend/src/lib/constants.ts` | Central file |
| Types | `frontend/src/types/` | `{Entity}Types.ts` |
| **Shared Schemas** | `frontend/src/schemas/SharedSchemas.ts` | Central file |
| Schemas | `frontend/src/schemas/` | `{Entity}Schemas.ts` |
| Hook | `frontend/src/hooks/` | `use{Entity}Data.ts` |
| Validation | `frontend/src/lib/validation.ts` | Central file |

---

## Step 1: Analyze JSON and Find Patterns

Before writing any code, thoroughly analyze the JSON:

1. **Read the JSON file** to understand the actual structure
2. **Identify field types** - strings, numbers, arrays, nested objects
3. **Note optional vs required** - which fields are always present
4. **CRITICAL: Find repeated value patterns** - fields with limited valid values

### Finding Value Patterns

**Look for fields with consistent value sets:**

```json
{
  "skills": {
    "skill1": {
      "sin": "Wrath",        // ← Always one of 7 sins
      "atkType": "SLASH"     // ← Always SLASH, PENETRATE, or HIT
    }
  },
  "affinity": "CRIMSON"      // ← Always one of 7 affinities
}
```

**If a field has limited valid values → Create a constant + literal type!**

### Document Your Findings

```markdown
## JSON Analysis: identity/{id}.json

**Fields with patterns (NEED CONSTANTS):**
- sin: "Wrath" | "Lust" | "Sloth" | ... (7 values) → SinSchema
- atkType: "SLASH" | "PENETRATE" | "HIT" → AtkTypeSchema
- affinity: "CRIMSON" | "SCARLET" | ... → AffinitySchema

**Regular fields:**
- HP: number (required)
- name: string (required)
- effects: BuffEffect[] (required)
```

---

## Step 2A: Create Constants for Value Patterns

Location: `frontend/src/lib/constants.ts`

**If you found value patterns, add them to constants FIRST:**

```typescript
// frontend/src/lib/constants.ts

/**
 * Affinity types for internal computation (data format names)
 */
export const AFFINITIES = ['CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK', 'AZURE', 'INDIGO', 'VIOLET'] as const

/**
 * Affinity type derived from AFFINITIES array
 */
export type Affinity = typeof AFFINITIES[number]

/**
 * Attack types
 */
export const ATK_TYPES = ['SLASH', 'PENETRATE', 'HIT'] as const

/**
 * Attack type derived from ATK_TYPES array
 */
export type AtkType = typeof ATK_TYPES[number]

/**
 * Status effects (keywords)
 */
export const STATUS_EFFECTS = [
  'Combustion', 'Laceration', 'Vibration', 'Burst',
  'Sinking', 'Breath', 'Charge',
] as const

export type StatusEffect = typeof STATUS_EFFECTS[number]
```

**Pattern:**
1. Array with `as const`
2. Type derived using `typeof ARRAY[number]`

---

## Step 2B: Add Shared Zod Schemas

Location: `frontend/src/schemas/SharedSchemas.ts`

**For each constant, create a matching Zod schema:**

```typescript
// frontend/src/schemas/SharedSchemas.ts
import { z } from 'zod'

/**
 * Sin type enum validation (display names)
 */
export const SinSchema = z.enum([
  'Wrath', 'Lust', 'Sloth', 'Gluttony',
  'Gloom', 'Pride', 'Envy',
])

/**
 * Affinity type enum validation (data format names)
 */
export const AffinitySchema = z.enum([
  'CRIMSON', 'SCARLET', 'AMBER', 'SHAMROCK',
  'AZURE', 'INDIGO', 'VIOLET',
])

/**
 * Attack type enum validation
 */
export const AtkTypeSchema = z.enum(['SLASH', 'PENETRATE', 'HIT'])

/**
 * Status effect enum validation
 */
export const StatusEffectSchema = z.enum([
  'Combustion', 'Laceration', 'Vibration', 'Burst',
  'Sinking', 'Breath', 'Charge',
])
```

---

## Step 2C: Create Entity Types

Location: `frontend/src/types/{EntityName}Types.ts`

**Import and use the literal types from constants:**

```typescript
// frontend/src/types/IdentityTypes.ts
import type { Affinity, AtkType } from '@/lib/constants'

export interface SkillData {
  sin: string            // Could use Sin type if we had it
  atkType: AtkType       // ← Uses literal type!
  power: number
}

export interface IdentityData {
  HP: number
  affinity: Affinity     // ← Uses literal type!
  skills: {
    skill1: SkillData[]
    skill2: SkillData[]
  }
}

// i18n type - also needs strict typing!
export interface IdentityI18n {
  name: string
  skills: {
    skill1: SkillI18n[]
    skill2: SkillI18n[]
  }
}

// Merged type for UI consumption
export interface Identity {
  id: string
  name: string
  affinity: Affinity
  // ...
}
```

---

## Step 3: Create Entity Schemas

Location: `frontend/src/schemas/{EntityName}Schemas.ts`

**Import and use shared schemas - NO z.string() for known values:**

```typescript
// frontend/src/schemas/IdentitySchemas.ts
import { z } from 'zod'
import { AffinitySchema, AtkTypeSchema, SinSchema } from './SharedSchemas'

// BAD - too permissive, loses type safety
const SkillDataSchema = z.object({
  sin: z.string(),        // ❌ Wrong!
  atkType: z.string(),    // ❌ Wrong!
  power: z.number(),
}).strict()

// GOOD - uses literal type schemas
const SkillDataSchema = z.object({
  sin: SinSchema,         // ✅ z.enum(['Wrath', 'Lust', ...])
  atkType: AtkTypeSchema, // ✅ z.enum(['SLASH', 'PENETRATE', 'HIT'])
  power: z.number(),
}).strict()

export const IdentityDataSchema = z.object({
  HP: z.number(),
  affinity: AffinitySchema,  // ✅ Uses shared schema
  skills: z.object({
    skill1: z.array(SkillDataSchema),
    skill2: z.array(SkillDataSchema),
  }).strict(),
}).strict()

// i18n schema - ALSO needs validation!
export const IdentityI18nSchema = z.object({
  name: z.string(),
  skills: z.object({
    skill1: z.array(SkillI18nSchema),
    skill2: z.array(SkillI18nSchema),
  }).strict(),
}).strict()
```

### TypeScript to Zod Mapping

| TypeScript | Zod Schema |
|------------|------------|
| `string` (known values) | `z.enum([...])` from SharedSchemas |
| `string` (unknown) | `z.string()` |
| `number` | `z.number()` |
| `boolean` | `z.boolean()` |
| `field?: T` | `field: schema.optional()` |
| `string[]` | `z.array(z.string())` |
| `1 \| 2 \| 3` | `z.union([z.literal(1), z.literal(2), z.literal(3)])` |
| `[A, B, C]` tuple | `z.tuple([schemaA, schemaB, schemaC])` |
| `Record<K,V>` | `z.record(keySchema, valueSchema)` |
| objects | **Always use `.strict()`** |

### Export from Index

```typescript
// frontend/src/schemas/index.ts
export * from './SharedSchemas'
export * from './IdentitySchemas'
```

---

## Step 4: Create Hook with Validation

Location: `frontend/src/hooks/use{Entity}Data.ts`

### Complete Hook Pattern (Both Data AND i18n Validated)

```typescript
// frontend/src/hooks/useIdentityData.ts
import { useQuery, queryOptions } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { validateData } from '@/lib/validation'
import { IdentityDataSchema, IdentityI18nSchema } from '@/schemas/IdentitySchemas'
import type { IdentityData, IdentityI18n, Identity } from '@/types/IdentityTypes'

// Query key factory
export const identityQueryKeys = {
  all: () => ['identity'] as const,
  detail: (id: string) => ['identity', id] as const,
  i18n: (id: string, language: string) => ['identity', id, 'i18n', language] as const,
}

// Data query with validation
function createDataQueryOptions(id: string) {
  return queryOptions({
    queryKey: identityQueryKeys.detail(id),
    queryFn: async () => {
      const response = await fetch(`/data/identity/${id}.json`)
      const rawData = await response.json()
      // VALIDATE data
      return validateData<IdentityData>(
        rawData,
        IdentityDataSchema,
        { entityType: 'identity', dataKind: 'detail', id }
      )
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
  })
}

// i18n query with validation
function createI18nQueryOptions(id: string, language: string) {
  return queryOptions({
    queryKey: identityQueryKeys.i18n(id, language),
    queryFn: async () => {
      const response = await fetch(`/i18n/${language}/identity/${id}.json`)
      const rawData = await response.json()
      // VALIDATE i18n data too!
      return validateData<IdentityI18n>(
        rawData,
        IdentityI18nSchema,
        { entityType: 'identity', dataKind: 'i18n', id }
      )
    },
    staleTime: 7 * 24 * 60 * 60 * 1000, // 7 days
  })
}

/**
 * Hook that loads identity data with i18n translations
 */
export function useIdentityData(id: string) {
  const { i18n } = useTranslation()

  const dataQuery = useQuery(createDataQueryOptions(id))
  const i18nQuery = useQuery(createI18nQueryOptions(id, i18n.language))

  return {
    data: dataQuery.data,
    i18n: i18nQuery.data,
    isPending: dataQuery.isPending || i18nQuery.isPending,
    isError: dataQuery.isError || i18nQuery.isError,
    error: dataQuery.error || i18nQuery.error,
  }
}
```

---

## Validation Utilities

```typescript
// frontend/src/lib/validation.ts
import { type ZodSchema, type ZodError } from 'zod'

export type EntityType = 'identity' | 'ego' | 'egoGift' | 'startBuff'
export type DataKind = 'detail' | 'list' | 'i18n'

export function validateData<T>(
  data: unknown,
  schema: ZodSchema,
  context: { entityType: EntityType; dataKind: DataKind; id?: string }
): T {
  const result = schema.safeParse(data)

  if (!result.success) {
    const prefix = `[${context.entityType}/${context.dataKind}]`
    const details = result.error.issues
      .map(err => `  - ${err.path.join('.')}: ${err.message}`)
      .join('\n')
    throw new Error(`${prefix} Validation failed:\n${details}`)
  }

  return result.data as T
}
```

---

## Complete Pipeline Checklist

When adding a new JSON data source, complete ALL steps:

### 1. JSON Analysis
- [ ] Read sample JSON files (data AND i18n) to understand structure
- [ ] **Find fields with limited valid values (patterns)**
- [ ] Document field types, optionals, and patterns

### 2. Constants & Shared Types (if patterns found)
- [ ] Add constant array to `lib/constants.ts` with `as const`
- [ ] Add derived type using `typeof ARRAY[number]`
- [ ] Add matching `z.enum()` to `schemas/SharedSchemas.ts`

### 3. Entity Types
- [ ] Create `frontend/src/types/{Entity}Types.ts`
- [ ] **Import and use literal types from constants**
- [ ] Define data type (from JSON data)
- [ ] Define i18n type (from i18n JSON)
- [ ] Define merged type for UI consumption

### 4. Entity Schemas
- [ ] Create `frontend/src/schemas/{Entity}Schemas.ts`
- [ ] **Import shared schemas from SharedSchemas.ts**
- [ ] **Use shared schemas, NOT z.string() for known values**
- [ ] Create data schema
- [ ] **Create i18n schema**
- [ ] Use `.strict()` on all object schemas
- [ ] Export from `frontend/src/schemas/index.ts`

### 5. React Query Hook
- [ ] Create `frontend/src/hooks/use{Entity}Data.ts`
- [ ] Create query key factory with `as const`
- [ ] Use `queryOptions()` pattern
- [ ] **Add `validateData()` for data in queryFn**
- [ ] **Add `validateData()` for i18n in queryFn**
- [ ] Set appropriate `staleTime`
- [ ] Return consistent `{ data, i18n, isPending, isError, error }`

---

## Common Mistakes to Avoid

1. **Using `z.string()` for known values** - Use `z.enum()` from SharedSchemas!
2. **Not validating i18n data** - Validate BOTH data and i18n JSON!
3. **Not creating constants for patterns** - Any limited value set needs a constant
4. **Skipping validation** - Always call `validateData()` in queryFn
5. **Forgetting `.strict()`** - Use strict mode for all objects
6. **Not exporting from index** - Add to `schemas/index.ts`
7. **Type/Schema mismatch** - Keep them synchronized

---

## Related Files

**Constants & Types:**
- `frontend/src/lib/constants.ts` - AFFINITIES, ATK_TYPES, etc.
- `frontend/src/types/IdentityTypes.ts`

**Schemas:**
- `frontend/src/schemas/SharedSchemas.ts` - SinSchema, AffinitySchema, etc.
- `frontend/src/schemas/IdentitySchemas.ts`

**Hooks:**
- `frontend/src/hooks/useEntityDetailData.ts`
- `frontend/src/lib/validation.ts`

**See Also:**
- [data-fetching.md](data-fetching.md) - TanStack Query patterns
- [typescript-standards.md](typescript-standards.md) - TypeScript patterns
