# Research: Planner Schema Reconstruction

## Clarifications Resolved

| Question | Decision | Impact |
|----------|----------|--------|
| Category location | Move from `PlannerContent` to `PlannerConfig` | Lightweight for summaries, heavy content stays separate |
| RR category field name | Use `category` (same as MD) | Polymorphic code enabled |
| Content validation | Two-step: validate config first, then content by type | Zod limitation workaround |
| DB category column | Change ENUM → VARCHAR(50) | Support multiple category types |

---

## Spec-to-Code Mapping

| Requirement | Current State | Action |
|-------------|---------------|--------|
| Discriminated union types | Not implemented | NEW: `MDConfig`, `RRConfig`, `PlannerConfig` in PlannerTypes.ts |
| Config structure | Missing | NEW: Add config layer to SaveablePlanner |
| PlannerSummary.plannerType | **MISSING** in PlannerListTypes.ts | ADD field |
| PlannerSummaryResponse.plannerType | **MISSING** in DTO | ADD field (CRITICAL) |
| Planner.category type | `@Enumerated(MDCategory)` | CHANGE to `String` |
| RRCategory placeholder | Not defined | NEW: constants + enum |
| Two-step validation | Not implemented | NEW: `validateSaveablePlanner()` function |

---

## Pattern Enforcement

| New/Modified File | MUST Read First | Pattern to Copy |
|-------------------|-----------------|-----------------|
| `types/PlannerTypes.ts` | Current file structure | Discriminated union from spec |
| `schemas/PlannerSchemas.ts` | `types/PlannerTypes.ts` | Two-step validation pattern |
| `types/PlannerListTypes.ts` | `types/PlannerTypes.ts` | Add plannerType matching metadata |
| `entity/Planner.java` | None | Change `@Enumerated` to `@Column` |
| `dto/PlannerSummaryResponse.java` | `entity/Planner.java` | Add plannerType with builder |
| `service/PlannerService.java` | `MDCategory.java` | Category validation by type |

---

## Existing Utilities (Reuse)

| Category | Location | Existing | Status |
|----------|----------|----------|--------|
| Constants | `lib/constants.ts` | `PLANNER_TYPES`, `MD_CATEGORIES` | ✓ Exists |
| Backend Enums | `entity/` | `PlannerType.java`, `MDCategory.java` | ✓ Exists |
| Schemas | `schemas/PlannerSchemas.ts` | `MDCategorySchema`, `PlannerStatusSchema` | ✓ Exists |
| Types | `types/PlannerTypes.ts` | `PlannerMetadata` (has plannerType) | ✓ Exists |

**Need to Add:**
- `RR_CATEGORIES` constant
- `RRCategorySchema` (Zod)
- `RRCategory.java` enum (backend)

---

## Gap Analysis

**Missing (Create New):**
- Frontend: `RRCategory` type, `MDConfig`, `RRConfig`, `PlannerConfig`, `validateSaveablePlanner()`
- Backend: `RRCategory` enum, `PlannerSummaryResponse.plannerType`, category validation
- Database: Migration ENUM → VARCHAR

**Modify:**
- `PlannerTypes.ts`: Restructure SaveablePlanner with config
- `PlannerListTypes.ts`: Add plannerType to PlannerSummary
- `PlannerSchemas.ts`: Add config schemas + two-step validation
- `constants.ts`: Add RR_CATEGORIES
- `Planner.java`: Change category column type
- `PlannerSummaryResponse.java`: Add plannerType field
- `PlannerService.java`: Add category validation

**Reuse:**
- `PLANNER_TYPES` constant
- `PlannerType` enum (backend)
- `MDCategory` enum (backend)
- Builder pattern from DTOs

---

## Testing Requirements

### Manual UI Tests
- IndexedDB: Verify `config: { type, category }` structure after save
- Planner list: Verify plannerType in React DevTools
- API: Verify summary response includes plannerType + category

### Automated Tests

**Frontend (NEW: PlannerSchemas.test.ts):**
- MDConfig validates with valid category
- RRConfig validates with placeholder
- Unknown type rejected
- Cross-type category rejected (MDCategory on RRConfig)
- Two-step validation: config+content match

**Backend (UPDATE existing tests):**
- PlannerServiceTest: Category validation by plannerType
- PlannerControllerTest: Response includes plannerType + category

---

## Technical Constraints

- **DB Migration:** category ENUM → VARCHAR(50), existing MD values preserved
- **Zod limitation:** Cannot validate content based on config.type in single schema
- **TypeScript:** Discriminated unions require literal type on discriminator field
- **IndexedDB:** Sets serialize to arrays (already handled)

---

## Critical Gaps (Priority Order)

| Issue | Severity | Phase |
|-------|----------|-------|
| PlannerSummaryResponse missing plannerType | CRITICAL | Phase 3 |
| Planner.category typed as MDCategory enum | HIGH | Phase 2 |
| No RRCategory enum | HIGH | Phase 2 |
| Config structure undefined | MEDIUM | Phase 1 |
| No RR_CATEGORIES constant | MEDIUM | Phase 1 |
