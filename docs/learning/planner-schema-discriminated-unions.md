# Learning: Discriminated Unions for Multi-Type Schema Support

**Date:** 2026-01-05
**Task:** Planner Schema Reconstruction
**Scope:** Frontend types, Zod schemas, Backend DTOs, Database schema

---

## Problem Statement

The planner system was designed for a single planner type (Mirror Dungeon). Adding support for Refracted Railway (and future types) revealed these structural issues:

| Issue | Location | Problem |
|-------|----------|---------|
| Hardcoded category | `PlannerContent.category: MDCategory` | RR has different category concept (station lines) |
| Missing discriminator | `PlannerSummary` | No `plannerType` field for list filtering |
| Enum-typed column | `Planner.category` (backend) | `@Enumerated(MDCategory)` rejects RR values |
| No content separation | `PlannerContent` | All fields assume MD structure |

---

## Solution: Discriminated Union Pattern

### What is a Discriminated Union?

A discriminated union uses a shared literal field (the "discriminator") to distinguish between variant types at runtime.

```typescript
// The discriminator field 'type' has literal values
interface MDConfig {
  type: 'MIRROR_DUNGEON'  // literal, not string
  category: MDCategory
}

interface RRConfig {
  type: 'REFRACTED_RAILWAY'  // literal, not string
  category: RRCategory
}

type PlannerConfig = MDConfig | RRConfig
```

**Key benefit:** TypeScript narrows the type automatically:

```typescript
function render(config: PlannerConfig) {
  if (config.type === 'MIRROR_DUNGEON') {
    // TypeScript knows: config is MDConfig
    // config.category is MDCategory (not RRCategory)
  }
}
```

---

## Architectural Decisions

### Decision 1: Three-Layer Structure

We chose to separate concerns into three layers:

| Layer | Purpose | Example Fields |
|-------|---------|----------------|
| **metadata** | Universal planner info | schemaVersion, contentVersion, title, timestamps |
| **config** | Type-specific user settings | type (discriminator), category |
| **content** | Type-specific game state | equipment, floors, gifts |

**Rationale:**
- `metadata` = same structure for all planner types
- `config` = lightweight, needed for summaries/filtering
- `content` = heavy, loaded only on detail view

### Decision 2: Same Field Name for Category

Both MD and RR use `category` instead of `mdCategory`/`rrCategory`:

```typescript
// CHOSEN: Same name, different types
interface MDConfig { type: 'MD', category: MDCategory }
interface RRConfig { type: 'RR', category: RRCategory }

// REJECTED: Different names
interface MDConfig { type: 'MD', mdCategory: MDCategory }
interface RRConfig { type: 'RR', stationLine: RRStationLine }
```

**Rationale:** Same field name enables polymorphic code:
```typescript
// Works for both types
<CategoryBadge category={config.category} />
```

### Decision 3: contentVersion in Metadata (Not Config)

Version number stays in metadata, interpreted by type:

```typescript
// contentVersion = 6
// config.type === 'MIRROR_DUNGEON' → "MD6"
// config.type === 'REFRACTED_RAILWAY' → "RR6"
```

**Rationale:** Version is a universal concept (all planners have one). Only the interpretation differs by type.

### Decision 4: No Discriminator in Content

Content does NOT have its own `type` field:

```typescript
// CHOSEN: Single source of truth
interface SaveablePlanner {
  config: PlannerConfig  // Has type field
  content: PlannerContent  // NO type field
}

// REJECTED: Redundant discriminators
interface MDPlannerContent {
  type: 'MIRROR_DUNGEON'  // Redundant with config.type
  equipment: ...
}
```

**Rationale:** Avoid redundancy and potential mismatch. Use `config.type` as single source of truth.

---

## Zod Validation: Two-Step Pattern

### The Problem

Zod's `z.discriminatedUnion()` validates a single object based on a discriminator field. But our structure has the discriminator in `config.type` while we want to validate `content` based on it.

```typescript
{
  config: { type: 'MIRROR_DUNGEON', ... },  // Discriminator here
  content: { ... }  // Validate THIS based on config.type
}
```

Zod cannot natively express "validate field B based on field A's value."

### The Solution: Two-Step Validation

```typescript
function validateSaveablePlanner(data: unknown): SaveablePlanner {
  // Step 1: Validate base structure (config uses discriminatedUnion)
  const base = z.object({
    metadata: PlannerMetadataSchema,
    config: PlannerConfigSchema,  // z.discriminatedUnion here
    content: z.record(z.unknown()),  // Loose validation
  }).parse(data)

  // Step 2: Validate content based on config.type
  const contentSchema = base.config.type === 'MIRROR_DUNGEON'
    ? MDPlannerContentSchema
    : RRPlannerContentSchema

  const content = contentSchema.parse(base.content)

  return { metadata: base.metadata, config: base.config, content }
}
```

**Trade-off:** More code than a single schema, but necessary for cross-field validation.

---

## Database Schema Change

### Before: ENUM Column

```java
@Enumerated(EnumType.STRING)
private MDCategory category;  // Only accepts MD values
```

### After: VARCHAR Column

```java
@Column(nullable = false, length = 50)
private String category;  // Accepts any string
```

**Migration:**
```sql
ALTER TABLE planners MODIFY COLUMN category VARCHAR(50) NOT NULL;
```

**Trade-off:**
- Lost: Database-level enum constraint
- Gained: Flexibility for multiple category types
- Mitigation: Service-layer validation by plannerType

---

## Key Patterns Introduced

### 1. Discriminated Union (First Usage in Codebase)

```typescript
const PlannerConfigSchema = z.discriminatedUnion('type', [
  MDConfigSchema,
  RRConfigSchema,
])
```

### 2. Two-Step Validation

Validate base structure first, then type-specific content.

### 3. Service-Layer Type Validation

```java
public boolean isValidCategory(PlannerType type, String category) {
    return switch (type) {
        case MIRROR_DUNGEON -> MDCategory.isValid(category);
        case REFRACTED_RAILWAY -> RRCategory.isValid(category);
    };
}
```

### 4. Placeholder Types for Future Extensions

```typescript
type RRCategory = 'RR_PLACEHOLDER'  // Replace when implementing RR
```

---

## Principles Applied

| Principle | Application |
|-----------|-------------|
| **Open/Closed** | Adding new planner types = add new config variant, no existing code changes |
| **Single Source of Truth** | `config.type` is the only discriminator |
| **Separation of Concerns** | metadata (universal) / config (type settings) / content (game state) |
| **YAGNI** | RR content is placeholder until actually implemented |
| **Polymorphism** | Same field names enable shared component code |

---

## Testing Strategy

### What to Test

| Test | Purpose |
|------|---------|
| MDConfig validates | Happy path |
| RRConfig validates | Placeholder works |
| Unknown type rejected | Discriminator validation |
| Cross-type category rejected | MDCategory on RRConfig fails |
| Two-step validation | Config+content must match |

### Type-Level Tests

TypeScript's type narrowing is compile-time only. Test it explicitly:

```typescript
it('narrows MDConfig on type check', () => {
  const config: PlannerConfig = { type: 'MIRROR_DUNGEON', category: '5F' }
  if (config.type === 'MIRROR_DUNGEON') {
    const category: MDCategory = config.category  // Should compile
    expect(category).toBe('5F')
  }
})
```

---

## Common Pitfalls

### 1. Forgetting Exhaustive Checks

```typescript
// BAD: Missing case
function getLabel(config: PlannerConfig) {
  if (config.type === 'MIRROR_DUNGEON') return 'MD'
  // RR case missing - no compile error!
}

// GOOD: Exhaustive switch
function getLabel(config: PlannerConfig) {
  switch (config.type) {
    case 'MIRROR_DUNGEON': return 'MD'
    case 'REFRACTED_RAILWAY': return 'RR'
    // TypeScript errors if case missing
  }
}
```

### 2. Loose Discriminator Type

```typescript
// BAD: String type doesn't narrow
interface Config { type: string }

// GOOD: Literal types enable narrowing
interface Config { type: 'MIRROR_DUNGEON' | 'REFRACTED_RAILWAY' }
```

### 3. Redundant Type Fields

Don't add `type` to multiple layers. One discriminator is enough.

---

## References

- TypeScript Handbook: [Discriminated Unions](https://www.typescriptlang.org/docs/handbook/2/narrowing.html#discriminated-unions)
- Zod Documentation: [Discriminated Unions](https://zod.dev/?id=discriminated-unions)
- Task Documents: `docs/09-planner-restructure/09-planner-schema-reconstruction/`
