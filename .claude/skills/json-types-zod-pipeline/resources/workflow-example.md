# Complete Workflow Example

End-to-end example of creating TypeScript types and Zod schemas from JSON data.

## Table of Contents

- [Step 1: Analyze JSON Structure](#step-1-analyze-json-structure)
- [Step 2: Create TypeScript Types](#step-2-create-typescript-types)
- [Step 3: Create Zod Schemas](#step-3-create-zod-schemas)
- [Step 4: Register in Validation Utils](#step-4-register-in-validation-utils)
- [Step 5: Export from Index](#step-5-export-from-index)

---

## Step 1: Analyze JSON Structure

### Read the JSON File

```json
// static/data/startBuffsMD6.json
{
  "100": {
    "level": 1,
    "baseId": 100,
    "cost": 5,
    "localizeId": "START_BUFF_100",
    "effects": [
      {
        "type": "ATK_POWER_UP",
        "value": 2,
        "isTypoExist": false,
        "referenceData": {
          "buffKeyword": "AttackPowerUp"
        }
      }
    ],
    "uiConfig": {
      "iconSpriteId": "buff_atk_up"
    }
  }
}
```

### Document Findings

```markdown
## JSON Analysis: startBuffsMD6.json

**Root Structure:** Record<string, StartBuffData>

**StartBuffData fields:**
- level: number (required)
- baseId: number (required)
- cost: number (required)
- localizeId: string (required)
- effects: BuffEffect[] (required)
- uiConfig: BuffUIConfig (required)

**BuffEffect fields:**
- type: string (required)
- value: number (optional)
- value2: number (optional)
- isTypoExist: boolean (required)
- customLocalizeTextId: string (optional)
- referenceData: BuffReferenceData (optional)

**BuffReferenceData fields (all optional):**
- activeRound: number
- buffKeyword: string
- stack: number
- turn: number
- limit: number

**BuffUIConfig fields:**
- iconSpriteId: string (required)
```

---

## Step 2: Create TypeScript Types

Create `frontend/src/types/StartBuffTypes.ts`:

```typescript
/**
 * Start Buff types for Mirror Dungeon 6
 */

/**
 * Enhancement level type (0 = base, 1 = +, 2 = ++)
 */
export type EnhancementLevel = 0 | 1 | 2

/**
 * Reference data for buff effects with buff keywords
 */
export interface BuffReferenceData {
  activeRound?: number
  buffKeyword?: string
  stack?: number
  turn?: number
  limit?: number
}

/**
 * Individual buff effect
 */
export interface BuffEffect {
  type: string
  value?: number
  value2?: number
  isTypoExist: boolean
  customLocalizeTextId?: string
  referenceData?: BuffReferenceData
}

/**
 * UI configuration for buff display
 */
export interface BuffUIConfig {
  iconSpriteId: string
}

/**
 * Start Buff data from startBuffsMD6.json
 */
export interface StartBuffData {
  level: number
  baseId: number
  cost: number
  localizeId: string
  effects: BuffEffect[]
  uiConfig: BuffUIConfig
}

/**
 * Record of Start Buff data by ID
 */
export type StartBuffDataList = Record<string, StartBuffData>
```

### Key Decisions

1. **Optional fields** - Used `?` for fields not always present
2. **Type aliases** - Created `EnhancementLevel` for domain clarity
3. **Record type** - Used for the root JSON structure
4. **JSDoc comments** - Document purpose of each type

---

## Step 3: Create Zod Schemas

Create `frontend/src/schemas/StartBuffSchemas.ts`:

```typescript
import { z } from 'zod'

/**
 * Start Buff Schemas
 *
 * Zod schemas for runtime validation of Start Buff data structures.
 * These schemas mirror the TypeScript interfaces in types/StartBuffTypes.ts.
 *
 * MAINTENANCE: When TypeScript interfaces change, update these schemas
 * to maintain synchronization.
 */

// Enhancement level enum
export const EnhancementLevelSchema = z.union([
  z.literal(0),
  z.literal(1),
  z.literal(2),
])

// BuffReferenceData schema - all fields optional
export const BuffReferenceDataSchema = z.object({
  activeRound: z.number().optional(),
  buffKeyword: z.string().optional(),
  stack: z.number().optional(),
  turn: z.number().optional(),
  limit: z.number().optional(),
}).strict()

// BuffEffect schema
export const BuffEffectSchema = z.object({
  type: z.string(),
  value: z.number().optional(),
  value2: z.number().optional(),
  isTypoExist: z.boolean(),
  customLocalizeTextId: z.string().optional(),
  referenceData: BuffReferenceDataSchema.optional(),
}).strict()

// BuffUIConfig schema
export const BuffUIConfigSchema = z.object({
  iconSpriteId: z.string(),
}).strict()

// StartBuffData schema
export const StartBuffDataSchema = z.object({
  level: z.number(),
  baseId: z.number(),
  cost: z.number(),
  localizeId: z.string(),
  effects: z.array(BuffEffectSchema),
  uiConfig: BuffUIConfigSchema,
}).strict()

// StartBuffDataList schema - Record type
export const StartBuffDataListSchema = z.record(
  z.string(),
  StartBuffDataSchema
)
```

### Key Patterns

1. **`.strict()`** - Always use for objects to catch extra fields
2. **`.optional()`** - Matches TypeScript `?` modifier
3. **`z.record()`** - For `Record<K, V>` TypeScript types
4. **`z.union()` with literals** - For union types like `0 | 1 | 2`
5. **Nested schemas** - Define child schemas before parent

---

## Step 4: Register in Validation Utils

Update `frontend/src/lib/validation.ts`:

```typescript
import {
  // ... existing imports
  StartBuffDataSchema,
  StartBuffDataListSchema,
} from '@/schemas'

// Add to EntityType if new entity
export type EntityType = 'identity' | 'ego' | 'egoGift' | 'startBuff'

/**
 * Get schema for start buff data
 */
export function getStartBuffSchema(dataKind: DataKind): ZodSchema {
  const schemaMap = {
    detail: StartBuffDataSchema,
    list: StartBuffDataListSchema,
  } as const

  return schemaMap[dataKind]
}
```

---

## Step 5: Export from Index

Update `frontend/src/schemas/index.ts`:

```typescript
// Existing exports
export * from './SharedSchemas'
export * from './IdentitySchemas'
export * from './EGOSchemas'
export * from './EGOGiftSchemas'

// New export
export * from './StartBuffSchemas'
```

---

## Validation Example Usage

```typescript
import { StartBuffDataListSchema } from '@/schemas'
import { validateData } from '@/lib/validation'

// In a data loading hook
const rawData = await fetchJson('/data/startBuffsMD6.json')

// Validate at runtime
const validated = validateData(rawData, StartBuffDataListSchema, {
  entityType: 'startBuff',
  dataKind: 'list',
})

// validated is now typed and validated
```

---

## Summary

1. **Read JSON** - Understand actual structure
2. **Document fields** - Note required vs optional
3. **Create types** - TypeScript interfaces first
4. **Create schemas** - Zod schemas that mirror types
5. **Register** - Add to validation utilities
6. **Export** - Add to schema index

This pipeline ensures type-safe data handling with runtime validation.
