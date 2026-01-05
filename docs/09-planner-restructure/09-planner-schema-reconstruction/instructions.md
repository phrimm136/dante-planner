# Task: Planner Schema Reconstruction for Multi-Type Support

## Description

Reconstruct the planner schema to support multiple planner types (Mirror Dungeon, Refracted Railway, future types) using a discriminated union pattern. This task focuses on **extensibility only** - no RR features will be implemented yet.

### Current Problem

- `category: MDCategory` is hardcoded throughout the schema
- `PlannerSummary` lacks `plannerType` field - cannot filter/display mixed-type lists
- `PlannerSummaryResponse.java` (backend) is missing `plannerType` - critical gap
- `PlannerContent` assumes MD structure - no separation for different planner types
- Backend `Planner.category` is `@Enumerated(MDCategory)` - won't accept RR categories

### Target Architecture

```
SaveablePlanner {
  metadata: PlannerMetadata        // Universal: schemaVersion, contentVersion, title, timestamps
  config: MDConfig | RRConfig      // Type-specific: plannerType + category
  content: MDPlannerContent | RRPlannerContent  // Type-specific game state
}
```

### Layer Responsibilities

| Layer | Purpose | Fields |
|-------|---------|--------|
| **metadata** | Universal planner info | schemaVersion, contentVersion, title, timestamps |
| **config** | Type-specific user settings | type (discriminator), category |
| **content** | Type-specific game state | equipment, floors, gifts (MD) / TBD (RR) |

### Decided Structure

#### Config (Discriminated Union)

Both planner types use `category` field with type-specific enums:

```typescript
interface MDConfig {
  type: 'MIRROR_DUNGEON'
  category: MDCategory  // '5F' | '10F' | '15F'
}

interface RRConfig {
  type: 'REFRACTED_RAILWAY'
  category: RRCategory  // Placeholder for station lines
}

type PlannerConfig = MDConfig | RRConfig
```

**Rationale**: Same field name (`category`) enables polymorphic code. Both represent "user-selectable variant".

#### Content (Type-Specific Schemas)

```typescript
interface MDPlannerContent {
  equipment: Record<string, SinnerEquipment>
  floorSelections: SerializableFloorSelection[]
  selectedBuffIds: number[]
  // ... all current MD-specific fields
}

interface RRPlannerContent {
  // Placeholder - define when implementing RR
}

type PlannerContent = MDPlannerContent | RRPlannerContent
```

**Note**: Content does NOT have its own `type` field. Use `config.type` as single source of truth.

#### Metadata (Universal)

```typescript
interface PlannerMetadata {
  schemaVersion: number      // Data format version (for migration)
  contentVersion: number     // Game version - interpret by config.type (MD6, RR5)
  title: string              // User-editable, universal across types
  status: PlannerStatus
  lastModifiedAt: string
  // ...
}
```

**contentVersion interpretation**:
- `config.type === 'MIRROR_DUNGEON'` → `MD${contentVersion}` (e.g., MD6)
- `config.type === 'REFRACTED_RAILWAY'` → `RR${contentVersion}` (e.g., RR5)

### Validation Strategy (Two-Step)

Zod's `z.discriminatedUnion()` cannot validate `content` based on `config.type` directly. Use two-step validation:

```typescript
// Step 1: Validate base structure
const BaseSchema = z.object({
  metadata: PlannerMetadataSchema,
  config: PlannerConfigSchema,  // Discriminated union here
  content: z.record(z.unknown()),
})

// Step 2: Validate content based on config.type
function validatePlanner(data: unknown): SaveablePlanner {
  const base = BaseSchema.parse(data)

  const contentSchema = base.config.type === 'MIRROR_DUNGEON'
    ? MDPlannerContentSchema
    : RRPlannerContentSchema

  const content = contentSchema.parse(base.content)

  return { ...base, content }
}
```

### Clarified Decisions

| Question | Decision | Rationale |
|----------|----------|-----------|
| RR category field name | Use `category` (same as MD) | Polymorphic code, same purpose |
| contentVersion location | Keep in metadata | Universal concept, interpret by config.type |
| title location | Keep in metadata | Universal field, not type-specific |
| Content discriminator | None - use config.type | Single source of truth, no redundancy |
| RRCategory placeholder | `'RR_PLACEHOLDER'` literal | Compiles but obviously incomplete |
| Backward compatibility | Not needed | DB is empty - clean slate |
| API routes | Not in scope | Leave `/api/planner/md` as-is |

## Research

### Resolved

- [x] RR uses station line as category equivalent → both use `category` field
- [x] No `z.discriminatedUnion()` in codebase → this is first usage
- [x] `PlannerSummaryResponse.java` missing `plannerType` → critical fix needed
- [x] Content validation strategy → two-step validation
- [x] DB `category` column is `@Enumerated(MDCategory)` → change to String

### Patterns to Reference

- Zod discriminated union: `z.discriminatedUnion('type', [...])`
- TypeScript narrowing: switch on `config.type`
- Jackson polymorphism: `@JsonTypeInfo` + `@JsonSubTypes` (for later)

## Scope

### Files to READ for Context

```
# Frontend Types & Schemas
frontend/src/types/PlannerTypes.ts
frontend/src/types/PlannerListTypes.ts
frontend/src/schemas/PlannerSchemas.ts

# Frontend Hooks
frontend/src/hooks/usePlannerStorage.ts
frontend/src/hooks/usePlannerStorageAdapter.ts
frontend/src/hooks/usePlannerSave.ts

# Backend DTOs
backend/src/main/java/org/danteplanner/backend/dto/planner/PlannerSummaryResponse.java
backend/src/main/java/org/danteplanner/backend/dto/planner/CreatePlannerRequest.java
backend/src/main/java/org/danteplanner/backend/entity/Planner.java

# Constants
frontend/src/lib/constants.ts
```

## Target Code Area

### Phase 1: Frontend Types & Schemas (Foundation)

1. **`frontend/src/lib/constants.ts`**
   - Add `RR_CATEGORIES` placeholder array
   - Verify `PLANNER_TYPES` includes both types

2. **`frontend/src/types/PlannerTypes.ts`**
   - Add `RRCategory` type (placeholder: `'RR_PLACEHOLDER'`)
   - Add `MDConfig`, `RRConfig`, `PlannerConfig` discriminated union
   - Rename `PlannerContent` → `MDPlannerContent`
   - Add `RRPlannerContent` placeholder interface
   - Add `PlannerContent` union type
   - Update `SaveablePlanner` to use config + typed content
   - Move `category` from content to config

3. **`frontend/src/types/PlannerListTypes.ts`**
   - Add `plannerType: PlannerType` to `PlannerSummary`
   - Add `category: MDCategory | RRCategory` to `PlannerSummary`

4. **`frontend/src/schemas/PlannerSchemas.ts`**
   - Add `RRCategorySchema` (placeholder)
   - Add `MDConfigSchema`, `RRConfigSchema`
   - Add `PlannerConfigSchema` using `z.discriminatedUnion('type', [...])`
   - Rename content schema → `MDPlannerContentSchema`
   - Add `RRPlannerContentSchema` placeholder
   - Update `SaveablePlannerSchema` with two-step validation
   - Update `PlannerSummarySchema` to include plannerType + category

### Phase 2: Database Schema

1. **`backend/src/main/resources/db/migration/V{next}__planner_category_to_string.sql`** (NEW)
   - Change `category` column from ENUM to VARCHAR
   - Rationale: Different planner types have different category enums

   ```sql
   -- Change category from ENUM to VARCHAR to support multiple planner types
   ALTER TABLE planners MODIFY COLUMN category VARCHAR(50) NOT NULL;
   ```

2. **`backend/src/main/java/org/danteplanner/backend/entity/Planner.java`**
   - Change `category` from `@Enumerated(MDCategory)` to `String`
   - Validation moves to service layer (based on plannerType)

   ```java
   // Before
   @Enumerated(EnumType.STRING)
   private MDCategory category;

   // After
   @Column(nullable = false, length = 50)
   private String category;
   ```

### Phase 3: Backend DTOs

1. **`backend/.../dto/planner/PlannerSummaryResponse.java`**
   - Add `plannerType: PlannerType` field
   - Change `category` to `String` (generic to support both types)

2. **`backend/.../dto/planner/CreatePlannerRequest.java`**
   - Add `plannerType: PlannerType` field (required)
   - Change `category` to `String`

3. **`backend/.../dto/planner/UpdatePlannerRequest.java`**
   - Add `plannerType: PlannerType` field (optional)
   - Change `category` to `String`

4. **`backend/.../service/PlannerService.java`**
   - Add category validation based on plannerType
   - Populate `plannerType` and `category` in summary responses

### Phase 4: Frontend Hooks (Storage Layer)

1. **`frontend/src/hooks/usePlannerStorage.ts`**
   - Update to handle config structure
   - Use two-step validation for content

2. **`frontend/src/hooks/usePlannerSave.ts`**
   - Update serialization for config pattern

3. **`frontend/src/hooks/usePlannerStorageAdapter.ts`**
   - Update type handling

### Phase 5: Tests

#### Frontend Tests

1. **`frontend/src/schemas/PlannerSchemas.test.ts`** (NEW)

   ```typescript
   describe('PlannerConfigSchema', () => {
     it('validates MDConfig with valid category', () => {
       const config = { type: 'MIRROR_DUNGEON', category: '5F' }
       expect(PlannerConfigSchema.parse(config)).toEqual(config)
     })

     it('validates RRConfig with placeholder category', () => {
       const config = { type: 'REFRACTED_RAILWAY', category: 'RR_PLACEHOLDER' }
       expect(PlannerConfigSchema.parse(config)).toEqual(config)
     })

     it('rejects unknown type', () => {
       const config = { type: 'UNKNOWN', category: '5F' }
       expect(() => PlannerConfigSchema.parse(config)).toThrow()
     })

     it('rejects MDConfig with RR category', () => {
       const config = { type: 'MIRROR_DUNGEON', category: 'RR_PLACEHOLDER' }
       expect(() => PlannerConfigSchema.parse(config)).toThrow()
     })

     it('rejects RRConfig with MD category', () => {
       const config = { type: 'REFRACTED_RAILWAY', category: '5F' }
       expect(() => PlannerConfigSchema.parse(config)).toThrow()
     })
   })

   describe('validateSaveablePlanner (two-step)', () => {
     it('validates complete MD planner', () => {
       const planner = createMockMDPlanner()
       expect(() => validateSaveablePlanner(planner)).not.toThrow()
     })

     it('rejects MD config with RR content structure', () => {
       const planner = {
         ...createMockMDPlanner(),
         config: { type: 'MIRROR_DUNGEON', category: '5F' },
         content: { /* RR content fields */ }
       }
       expect(() => validateSaveablePlanner(planner)).toThrow()
     })
   })
   ```

2. **`frontend/src/hooks/usePlannerStorage.test.ts`** (UPDATE)
   - Update fixtures to use new config structure
   - Test IndexedDB stores config correctly
   - Test load validates with two-step

3. **`frontend/src/types/PlannerTypes.test.ts`** (NEW - type tests)

   ```typescript
   describe('Type narrowing', () => {
     it('narrows MDConfig on type check', () => {
       const config: PlannerConfig = { type: 'MIRROR_DUNGEON', category: '5F' }
       if (config.type === 'MIRROR_DUNGEON') {
         // TypeScript should allow this
         const category: MDCategory = config.category
         expect(category).toBe('5F')
       }
     })
   })
   ```

#### Backend Tests

1. **`backend/.../controller/PlannerControllerTest.java`** (UPDATE)

   ```java
   @Test
   void createPlanner_withMDType_shouldSucceed() {
       CreatePlannerRequest request = CreatePlannerRequest.builder()
           .title("Test MD Planner")
           .plannerType(PlannerType.MIRROR_DUNGEON)
           .category("5F")
           .content("{...}")
           .build();

       mockMvc.perform(post("/api/planner/md")
           .contentType(MediaType.APPLICATION_JSON)
           .content(objectMapper.writeValueAsString(request)))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.plannerType").value("MIRROR_DUNGEON"))
           .andExpect(jsonPath("$.category").value("5F"));
   }

   @Test
   void createPlanner_withInvalidCategoryForType_shouldFail() {
       CreatePlannerRequest request = CreatePlannerRequest.builder()
           .plannerType(PlannerType.MIRROR_DUNGEON)
           .category("RR_PLACEHOLDER")  // Invalid for MD
           .build();

       mockMvc.perform(post("/api/planner/md")
           .contentType(MediaType.APPLICATION_JSON)
           .content(objectMapper.writeValueAsString(request)))
           .andExpect(status().isBadRequest());
   }

   @Test
   void getPlannerSummaries_shouldIncludePlannerType() {
       // Create test planner
       mockMvc.perform(get("/api/planner/md"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.content[0].plannerType").exists())
           .andExpect(jsonPath("$.content[0].category").exists());
   }
   ```

2. **`backend/.../service/PlannerServiceTest.java`** (UPDATE)

   ```java
   @Test
   void validateCategory_MDPlanner_shouldAcceptMDCategories() {
       assertTrue(plannerService.isValidCategory(PlannerType.MIRROR_DUNGEON, "5F"));
       assertTrue(plannerService.isValidCategory(PlannerType.MIRROR_DUNGEON, "10F"));
       assertTrue(plannerService.isValidCategory(PlannerType.MIRROR_DUNGEON, "15F"));
   }

   @Test
   void validateCategory_MDPlanner_shouldRejectRRCategories() {
       assertFalse(plannerService.isValidCategory(PlannerType.MIRROR_DUNGEON, "RR_PLACEHOLDER"));
   }

   @Test
   void validateCategory_RRPlanner_shouldAcceptRRCategories() {
       assertTrue(plannerService.isValidCategory(PlannerType.REFRACTED_RAILWAY, "RR_PLACEHOLDER"));
   }

   @Test
   void toSummaryResponse_shouldIncludePlannerTypeAndCategory() {
       Planner planner = createTestPlanner();
       PlannerSummaryResponse response = plannerService.toSummaryResponse(planner);

       assertEquals(PlannerType.MIRROR_DUNGEON, response.getPlannerType());
       assertEquals("5F", response.getCategory());
   }
   ```

3. **`backend/.../validation/CategoryValidatorTest.java`** (NEW - if using custom validator)

   ```java
   @Test
   void validate_MDTypeWithMDCategory_shouldPass() {
       // Test category validation logic
   }
   ```

### Phase 6: Cleanup & Documentation

1. Update `docs/architecture-map.md` with:
   - New discriminated union pattern
   - Two-step validation approach
   - Config vs metadata vs content responsibilities

2. Remove deprecated patterns

## Testing Guidelines

### Manual UI Testing

#### Schema Validation (Primary)
1. Open DevTools Console
2. Navigate to `/planner/md/new`
3. Create a new MD planner (select 5F category)
4. Make changes and wait for auto-save
5. Open IndexedDB (Application > IndexedDB > danteplanner)
6. Verify stored data has structure:
   ```json
   {
     "metadata": { "schemaVersion": 1, "contentVersion": 6, "title": "..." },
     "config": { "type": "MIRROR_DUNGEON", "category": "5F" },
     "content": { "equipment": {...}, "floorSelections": [...] }
   }
   ```
7. Verify no Zod validation errors in console

#### Planner List View
1. Navigate to `/planner`
2. Verify "My Plans" tab shows planners
3. Verify each planner card displays category correctly
4. Verify plannerType is available in data (check React DevTools)

#### Backend API
1. Use curl or Postman to create planner:
   ```bash
   curl -X POST http://localhost:8080/api/planner/md \
     -H "Content-Type: application/json" \
     -d '{"title":"Test","plannerType":"MIRROR_DUNGEON","category":"5F","content":"{}"}'
   ```
2. Verify response includes `plannerType` and `category`
3. GET planner summaries, verify both fields present

### Automated Functional Verification

#### Config Discrimination
- [ ] `config.type === 'MIRROR_DUNGEON'` correctly narrows to MDConfig
- [ ] `config.type === 'REFRACTED_RAILWAY'` correctly narrows to RRConfig
- [ ] Unknown type values are rejected by Zod validation

#### Two-Step Content Validation
- [ ] MDConfig + MDPlannerContent validates successfully
- [ ] MDConfig + invalid content fails validation
- [ ] RRConfig + RRPlannerContent validates (placeholder)

#### Summary Structure
- [ ] PlannerSummary includes plannerType field
- [ ] PlannerSummary includes category field
- [ ] Backend PlannerSummaryResponse includes both fields

#### Category Validation (Backend)
- [ ] MD planner accepts only MD categories (5F, 10F, 15F)
- [ ] MD planner rejects RR categories
- [ ] RR planner accepts only RR categories
- [ ] RR planner rejects MD categories

#### Data Flow
- [ ] Guest save: IndexedDB stores config + content structure
- [ ] Guest load: Two-step validation works from IndexedDB
- [ ] Auth save: API request includes plannerType
- [ ] Auth load: Server response includes plannerType in summary

### Edge Cases

- [ ] Empty config: Rejects planner without config field
- [ ] Invalid config.type: Rejects unknown type values
- [ ] Missing category: Rejects MDConfig without category
- [ ] Wrong category enum: MDCategory value on RRConfig rejected
- [ ] Content mismatch: MDPlannerContent fields on RR planner rejected
- [ ] Null plannerType in request: Validation fails with clear error

### Integration Points

- [ ] SSE sync: Real-time updates preserve config + content structure
- [ ] Conflict resolution: Overwrite/discard handles new structure
- [ ] Planner list: Cards render correctly with plannerType + category

## Implementation Notes

### Type Definitions

```typescript
// constants.ts
export const RR_CATEGORIES = ['RR_PLACEHOLDER'] as const
export type RRCategory = (typeof RR_CATEGORIES)[number]

// PlannerTypes.ts
interface MDConfig {
  type: 'MIRROR_DUNGEON'
  category: MDCategory
}

interface RRConfig {
  type: 'REFRACTED_RAILWAY'
  category: RRCategory
}

type PlannerConfig = MDConfig | RRConfig

interface MDPlannerContent {
  equipment: Record<string, SinnerEquipment>
  floorSelections: SerializableFloorSelection[]
  // ... current fields
}

interface RRPlannerContent {
  // Placeholder
}

type PlannerContent = MDPlannerContent | RRPlannerContent

interface SaveablePlanner {
  metadata: PlannerMetadata
  config: PlannerConfig
  content: PlannerContent
}
```

### Zod Schemas

```typescript
// Config schemas
const MDConfigSchema = z.object({
  type: z.literal('MIRROR_DUNGEON'),
  category: MDCategorySchema,
})

const RRConfigSchema = z.object({
  type: z.literal('REFRACTED_RAILWAY'),
  category: RRCategorySchema,
})

const PlannerConfigSchema = z.discriminatedUnion('type', [
  MDConfigSchema,
  RRConfigSchema,
])

// Two-step validation
function validateSaveablePlanner(data: unknown): SaveablePlanner {
  const base = z.object({
    metadata: PlannerMetadataSchema,
    config: PlannerConfigSchema,
    content: z.record(z.unknown()),
  }).parse(data)

  const contentSchema = base.config.type === 'MIRROR_DUNGEON'
    ? MDPlannerContentSchema
    : RRPlannerContentSchema

  const content = contentSchema.parse(base.content)

  return { metadata: base.metadata, config: base.config, content }
}
```

### Backend Category Validation

```java
// PlannerService.java
public boolean isValidCategory(PlannerType type, String category) {
    return switch (type) {
        case MIRROR_DUNGEON -> MDCategory.isValid(category);
        case REFRACTED_RAILWAY -> RRCategory.isValid(category);
    };
}

// MDCategory.java (add helper)
public static boolean isValid(String value) {
    return Arrays.stream(values())
        .anyMatch(c -> c.name().equals(value));
}
```

### Type Narrowing Usage

```typescript
function renderCategoryBadge(config: PlannerConfig) {
  switch (config.type) {
    case 'MIRROR_DUNGEON':
      return <MDBadge category={config.category} />  // TS knows: MDCategory
    case 'REFRACTED_RAILWAY':
      return <RRBadge category={config.category} />  // TS knows: RRCategory
  }
}

function getVersionLabel(metadata: PlannerMetadata, config: PlannerConfig): string {
  return config.type === 'MIRROR_DUNGEON'
    ? `MD${metadata.contentVersion}`
    : `RR${metadata.contentVersion}`
}
```
