# Runtime Schema Validation

This directory contains Zod schemas for runtime validation of entity data structures (Identity, EGO, EGOGift).

## Overview

These schemas provide:
- **Strict validation**: Reject additional fields not defined in TypeScript interfaces
- **Comprehensive error collection**: Use `safeParse()` to collect all validation errors, not just first failure
- **Type safety**: Mirror TypeScript interfaces exactly with array length constraints and enum validation
- **Runtime guarantees**: Validate JSON data at load time before type casting

## Usage

### Basic Validation

```typescript
import { IdentityDataSchema } from '@/schemas'

const result = IdentityDataSchema.safeParse(jsonData)

if (result.success) {
  const validData = result.data // Type-safe, validated data
} else {
  console.error('Validation errors:', result.error.errors)
}
```

### Integration with Hooks

```typescript
import { useQuery } from '@tanstack/react-query'
import { IdentityDataSchema } from '@/schemas'

const query = useQuery({
  queryKey: ['identity', id],
  queryFn: async () => {
    const module = await import(`@static/data/identity/${id}.json`)
    const result = IdentityDataSchema.safeParse(module.default)

    if (!result.success) {
      throw new Error(`Invalid identity data: ${result.error.message}`)
    }

    return result.data
  },
})
```

## Schema Specifications

### Array Length Constraints

- **resistances**: Exactly 7 elements (one per sin type)
- **costs**: Exactly 7 elements (one per sin type)
- **stagger**: Variable length (no constraint)
- **resist**: Exactly 3 elements as tuple `[slash, pierce, blunt]`

### Strict Mode

All schemas use `.strict()` to reject additional fields, ensuring JSON data precisely matches TypeScript interface definitions.

### Optional Fields

- `atkType` in SkillData
- `passiveSin`, `passiveEA`, `passiveType` in PassiveData
- `corrosion` in EGO skills

### Enum Validation

- **Sin**: `'Wrath' | 'Lust' | 'Sloth' | 'Gluttony' | 'Gloom' | 'Pride' | 'Envy'`
- **EGORank**: `'Zayin' | 'Teth' | 'He' | 'Waw' | 'Aleph'`
- **Uptie**: `'3' | '4'`
- **ImageVariant**: `'gacksung' | 'normal'`

## Maintenance

### Synchronization with TypeScript Interfaces

TypeScript interfaces in `/frontend/src/types/` are the **source of truth**. When interfaces change:

1. **Manual Update** (Current): Edit corresponding schema file to match interface changes
2. **Automated Generation** (Recommended for Future): Use ts-to-zod tool

### Using ts-to-zod for Automated Generation

For future automation of schema generation:

```bash
# Install ts-to-zod as dev dependency
yarn add -D ts-to-zod

# Add JSDoc comments to TypeScript interfaces
/** @zod */
export interface IdentityData {
  // ... interface definition
}

# Generate schemas
npx ts-to-zod src/types/IdentityTypes.ts src/schemas/IdentitySchemas.generated.ts
```

Then review and apply necessary customizations (array length constraints, strict mode).

### Validation Checklist

When adding or modifying schemas:

- [ ] All required fields defined with correct types
- [ ] Optional fields use `.optional()`
- [ ] Nested objects use `.strict()`
- [ ] Array length constraints applied where specified (resistances, costs)
- [ ] Enum types use `z.enum()` with all literal values
- [ ] Tuple types use `z.tuple()` with exact element types
- [ ] Record types use literal keys for upties/threadspins (`'3'`, `'4'`)
- [ ] Export schema from index.ts
- [ ] Test validation with sample data

## Schema Files

- **SharedSchemas.ts**: Common schemas shared across entity types (sin types, passive i18n)
- **IdentitySchemas.ts**: Identity entity data and i18n validation
- **EGOSchemas.ts**: EGO entity data and i18n validation with rank enum
- **EGOGiftSchemas.ts**: EGO Gift entity data and i18n validation
- **index.ts**: Central export point for all schemas

## Error Handling

Zod validation errors provide detailed information:

```typescript
const result = schema.safeParse(data)
if (!result.success) {
  result.error.errors.forEach(err => {
    console.error(`Path: ${err.path.join('.')}`)
    console.error(`Error: ${err.message}`)
  })
}
```

## Future Integration

These schemas are defined but not yet integrated into data loading hooks. Future work:

1. Add schema validation to `useEntityDetailData` hook
2. Add schema validation to `useEntityListData` hook
3. Implement error handling and user feedback for validation failures
4. Consider performance optimization for large datasets
