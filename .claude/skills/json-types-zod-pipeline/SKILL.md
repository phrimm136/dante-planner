---
name: json-types-zod-pipeline
description: Pipeline for creating TypeScript types and Zod schemas from JSON data files. Use when creating types from JSON, writing Zod schemas, validating JSON data structures, generating type definitions, or syncing types with schemas. Covers JSON analysis, TypeScript interface generation, Zod schema creation, and validation utilities.
---

# JSON to TypeScript Types to Zod Schema Pipeline

## Purpose

Streamlined workflow for creating type-safe data structures from JSON files. Ensures TypeScript interfaces and Zod schemas stay synchronized and accurately reflect the source JSON data structure.

## When to Use This Skill

- Creating TypeScript types from JSON data files
- Writing Zod schemas for runtime validation
- Adding new entity types (identity, EGO, ego gift, etc.)
- Updating existing types when JSON structure changes
- Setting up validation for new data sources
- Ensuring types and schemas match JSON structure

---

## Pipeline Overview

```
JSON File → Analyze Structure → TypeScript Types → Zod Schema → Validation Utils
```

### Step 1: Analyze JSON Structure

Before writing any code, thoroughly analyze the JSON:

1. **Read the JSON file** to understand the actual structure
2. **Identify field types** - strings, numbers, arrays, nested objects
3. **Note optional vs required** - which fields are always present
4. **Find enums/unions** - fields with limited valid values
5. **Identify tuples** - fixed-length arrays with specific types

### Step 2: Create TypeScript Types

Location: `frontend/src/types/{EntityName}Types.ts`

### Step 3: Create Zod Schemas

Location: `frontend/src/schemas/{EntityName}Schemas.ts`

### Step 4: Register in Validation Utils

Location: `frontend/src/lib/validation.ts`

---

## Quick Reference

### File Locations

| File Type | Location | Naming |
|-----------|----------|--------|
| JSON Data | `static/data/{entity}/` | `{id}.json` |
| JSON i18n | `static/i18n/{lang}/{entity}/` | `{id}.json` |
| Types | `frontend/src/types/` | `{Entity}Types.ts` |
| Schemas | `frontend/src/schemas/` | `{Entity}Schemas.ts` |
| Validation | `frontend/src/lib/validation.ts` | Central file |

### TypeScript to Zod Mapping

| TypeScript | Zod Schema |
|------------|------------|
| `string` | `z.string()` |
| `number` | `z.number()` |
| `boolean` | `z.boolean()` |
| `string[]` | `z.array(z.string())` |
| `optional` | `.optional()` |
| `union` | `z.union([...])` |
| `enum` | `z.enum([...])` |
| `tuple` | `z.tuple([...])` |
| `Record<K,V>` | `z.record(keySchema, valueSchema)` |
| `strict` | `.strict()` |

---

## Detailed Patterns

See reference files for complete examples:

- [Complete Workflow Example](resources/workflow-example.md) - End-to-end pipeline with code
- [Common Patterns](resources/common-patterns.md) - Frequently used type/schema patterns
- [Validation Integration](resources/validation-integration.md) - Hook integration patterns

---

## Checklist: New Entity Type

When adding a new entity type:

- [ ] Read sample JSON files to understand structure
- [ ] Create `frontend/src/types/{Entity}Types.ts`
- [ ] Create `frontend/src/schemas/{Entity}Schemas.ts`
- [ ] Import shared schemas from `SharedSchemas.ts`
- [ ] Export schemas from `frontend/src/schemas/index.ts`
- [ ] Add schema mappings in `validation.ts`
- [ ] Add entity type to `EntityType` union if needed

## Checklist: Updating Existing Types

When JSON structure changes:

- [ ] Read updated JSON to identify changes
- [ ] Update TypeScript interface in `*Types.ts`
- [ ] Update Zod schema in `*Schemas.ts`
- [ ] Verify schema still uses `.strict()` for objects
- [ ] Test validation with sample data

---

## Best Practices

### Always Read JSON First

```typescript
// BAD: Guessing structure
interface MyData {
  name: string;
  value: number;
}

// GOOD: After reading actual JSON
interface MyData {
  name: string;
  value: number;
  optionalField?: string;  // Discovered in JSON analysis
}
```

### Use Strict Mode

```typescript
// Always use .strict() for object schemas
export const MyDataSchema = z.object({
  name: z.string(),
  value: z.number(),
}).strict()  // Catches extra fields
```

### Keep Types and Schemas in Sync

```typescript
// Types file
export interface BuffEffect {
  type: string;
  value?: number;
  isTypoExist: boolean;
}

// Schema file - must match exactly
export const BuffEffectSchema = z.object({
  type: z.string(),
  value: z.number().optional(),
  isTypoExist: z.boolean(),
}).strict()
```

### Reuse Shared Schemas

```typescript
// SharedSchemas.ts has common schemas
import { SinSchema, AffinitySchema } from './SharedSchemas'

// Reuse in entity schemas
export const SkillDataSchema = z.object({
  sin: SinSchema,  // Reuse shared enum
  // ...
}).strict()
```

---

## Common Mistakes to Avoid

1. **Not reading JSON first** - Always analyze actual data
2. **Missing optional markers** - Check if fields can be undefined
3. **Wrong array types** - Distinguish arrays vs tuples
4. **Forgetting .strict()** - Use strict mode for all objects
5. **Not exporting from index** - Add to `schemas/index.ts`
6. **Type/Schema mismatch** - Keep them synchronized

---

## Related Files

**Existing Examples:**
- `frontend/src/types/IdentityTypes.ts`
- `frontend/src/types/StartBuffTypes.ts`
- `frontend/src/schemas/IdentitySchemas.ts`
- `frontend/src/schemas/SharedSchemas.ts`
- `frontend/src/lib/validation.ts`

**Data Sources:**
- `static/data/` - JSON data files
- `static/i18n/EN/` - i18n JSON files

---

**Skill Status**: ACTIVE
**Line Count**: < 200 (following 500-line rule)
